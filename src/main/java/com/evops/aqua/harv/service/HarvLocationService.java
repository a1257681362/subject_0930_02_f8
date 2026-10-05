package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evops.aqua.harv.dto.LocationCreateRequest;
import com.evops.aqua.harv.entity.HarvAllocation;
import com.evops.aqua.harv.entity.HarvAllocationLine;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvLocation;
import com.evops.aqua.harv.entity.HarvReservation;
import com.evops.aqua.harv.mapper.HarvAllocationLineMapper;
import com.evops.aqua.harv.mapper.HarvAllocationMapper;
import com.evops.aqua.harv.mapper.HarvLocationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 捕捞库位：登记 → 预约锁定（LOCKED 互斥）→ 分仓占用（OCCUPIED+待检）→ 检验/释放。
 * 库位状态与待检标识的每次同步都写 t_harv_change_evidence（ENTITY_LOCATION）。
 */
@Service
public class HarvLocationService {

    private final HarvLocationMapper locationMapper;
    private final HarvAllocationLineMapper lineMapper;
    private final HarvAllocationMapper allocationMapper;
    private final HarvEvidenceService evidenceService;

    public HarvLocationService(HarvLocationMapper locationMapper,
                               HarvAllocationLineMapper lineMapper,
                               HarvAllocationMapper allocationMapper,
                               HarvEvidenceService evidenceService) {
        this.locationMapper = locationMapper;
        this.lineMapper = lineMapper;
        this.allocationMapper = allocationMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public HarvLocation register(LocationCreateRequest request) {
        HarvLocation duplicated = locationMapper.selectOne(new QueryWrapper<HarvLocation>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        HarvLocation location = new HarvLocation();
        location.setLocationCode(request.getLocationCode());
        location.setSeaArea(request.getSeaArea());
        location.setCapacityKg(request.getCapacityKg());
        location.setStatus(HarvLocation.STATUS_AVAILABLE);
        location.setPendingInspect(false);
        location.setRequestId(request.getRequestId());
        locationMapper.insert(location);
        return location;
    }

    public HarvLocation getOrThrow(Long locationId) {
        HarvLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new IllegalArgumentException("库位不存在，id=" + locationId);
        }
        return location;
    }

    public List<HarvLocation> list(String seaArea, String status) {
        QueryWrapper<HarvLocation> wrapper = new QueryWrapper<>();
        wrapper.eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return locationMapper.selectList(wrapper);
    }

    /**
     * 锁定给预约：库位必须 AVAILABLE，且计划上限重量不超过库位容量。
     * 锁定后其他订单预约不得占用（状态 LOCKED + reservationId 互斥）。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvLocation lockTo(HarvReservation reservation, HarvLocation location,
                               String operator, String requestId) {
        if (!HarvLocation.STATUS_AVAILABLE.equals(location.getStatus())) {
            throw new IllegalArgumentException("库位" + location.getLocationCode() + "当前状态为"
                    + location.getStatus() + "，不可锁定；预约锁定后库位不能被其他订单占用");
        }
        if (reservation.getPlannedMaxKg().compareTo(location.getCapacityKg()) > 0) {
            throw new IllegalArgumentException("库位" + location.getLocationCode() + "容量"
                    + location.getCapacityKg() + "kg 不足，预约计划上限" + reservation.getPlannedMaxKg() + "kg");
        }
        HarvLocation before = copyOf(location);
        location.setStatus(HarvLocation.STATUS_LOCKED);
        location.setReservationId(reservation.getId());
        location.setPendingInspect(false);
        locationMapper.updateById(location);
        evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                HarvChangeEvidence.ACTION_RESERVATION_LOCK, requestId, operator, before, location,
                "库位锁定给预约" + reservation.getReservationNo());
        return location;
    }

    /** 释放预约锁定：库位恢复 AVAILABLE，清空互斥占用与待检标识。 */
    @Transactional(rollbackFor = Exception.class)
    public HarvLocation releaseFrom(HarvReservation reservation, String operator, String requestId) {
        if (reservation.getLocationId() == null) {
            return null;
        }
        HarvLocation location = getOrThrow(reservation.getLocationId());
        HarvLocation before = copyOf(location);
        location.setStatus(HarvLocation.STATUS_AVAILABLE);
        location.setReservationId(null);
        location.setPendingInspect(false);
        locationMapper.update(null, new LambdaUpdateWrapper<HarvLocation>()
                .eq(HarvLocation::getId, location.getId())
                .set(HarvLocation::getStatus, HarvLocation.STATUS_AVAILABLE)
                .set(HarvLocation::getReservationId, null)
                .set(HarvLocation::getPendingInspect, false));
        evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                HarvChangeEvidence.ACTION_RESERVATION_UNLOCK, requestId, operator, before, location,
                "释放预约" + reservation.getReservationNo() + "对库位的锁定");
        return location;
    }

    /**
     * 分仓落位占用：库位须未被其他预约锁定/占用（本预约锁定的 LOCKED 库位可直接用）。
     * 置 OCCUPIED 并打待检标识；本行重量与库位既有在仓重量之和不得超容量。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvLocation occupyFor(HarvReservation reservation, HarvLocation location,
                                  BigDecimal lineWeight, String operator, String requestId) {
        boolean ownLocked = HarvLocation.STATUS_LOCKED.equals(location.getStatus())
                && reservation.getId().equals(location.getReservationId());
        boolean ownOccupied = HarvLocation.STATUS_OCCUPIED.equals(location.getStatus())
                && reservation.getId().equals(location.getReservationId());
        if (!ownLocked && !ownOccupied && !HarvLocation.STATUS_AVAILABLE.equals(location.getStatus())) {
            throw new IllegalArgumentException("库位" + location.getLocationCode() + "已被预约id="
                    + location.getReservationId() + "占用（状态" + location.getStatus()
                    + "），不能分给预约" + reservation.getReservationNo());
        }
        BigDecimal used = activeWeightAt(location.getId());
        if (used.add(lineWeight).compareTo(location.getCapacityKg()) > 0) {
            throw new IllegalArgumentException("库位" + location.getLocationCode() + "容量"
                    + location.getCapacityKg() + "kg，已在仓" + used + "kg，再装" + lineWeight
                    + "kg 将超容量");
        }
        HarvLocation before = copyOf(location);
        location.setStatus(HarvLocation.STATUS_OCCUPIED);
        location.setReservationId(reservation.getId());
        location.setPendingInspect(true);
        locationMapper.updateById(location);
        evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                HarvChangeEvidence.ACTION_ALLOCATION_RECORD, requestId, operator, before, location,
                "现场分仓落位" + lineWeight + "kg，库位置占用并打待检标识");
        return location;
    }

    /** 作废旧分仓结果后把曾落位的库位清空恢复 AVAILABLE（保留本预约锁定库位由调用方另行复位）。 */
    @Transactional(rollbackFor = Exception.class)
    public void clearAfterVoid(List<Long> locationIds, String operator, String requestId) {
        for (Long locationId : locationIds) {
            HarvLocation location = getOrThrow(locationId);
            HarvLocation before = copyOf(location);
            location.setStatus(HarvLocation.STATUS_AVAILABLE);
            location.setReservationId(null);
            location.setPendingInspect(false);
            locationMapper.update(null, new LambdaUpdateWrapper<HarvLocation>()
                    .eq(HarvLocation::getId, locationId)
                    .set(HarvLocation::getStatus, HarvLocation.STATUS_AVAILABLE)
                    .set(HarvLocation::getReservationId, null)
                    .set(HarvLocation::getPendingInspect, false));
            evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                    HarvChangeEvidence.ACTION_ALLOCATION_VOID, requestId, operator, before, location,
                    "旧分仓结果作废，库位清空待重新分仓");
        }
    }

    /** 补传/补录重建后，把预约锁定库位重新置回 LOCKED 互斥态。 */
    @Transactional(rollbackFor = Exception.class)
    public void restoreLocked(HarvReservation reservation, String operator, String requestId) {
        if (reservation.getLocationId() == null) {
            return;
        }
        HarvLocation location = getOrThrow(reservation.getLocationId());
        HarvLocation before = copyOf(location);
        location.setStatus(HarvLocation.STATUS_LOCKED);
        location.setReservationId(reservation.getId());
        location.setPendingInspect(false);
        locationMapper.updateById(location);
        evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                HarvChangeEvidence.ACTION_ALLOCATION_VOID, requestId, operator, before, location,
                "预约" + reservation.getReservationNo() + "锁定库位在旧结果作废后恢复互斥锁定");
    }

    /**
     * 按库位内仍生效（USABLE 结果下）的分仓行重算待检标识：
     * 尚有未通过检验的行→TRUE；全部通过→FALSE。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingFlag(Long locationId, String operator, String requestId, String detail) {
        HarvLocation location = getOrThrow(locationId);
        List<HarvAllocationLine> active = activeLinesAt(locationId);
        boolean pending = active.stream().anyMatch(line -> !Boolean.TRUE.equals(line.getInspectPassed()));
        if (location.getPendingInspect() == null || location.getPendingInspect() != pending) {
            HarvLocation before = copyOf(location);
            location.setPendingInspect(pending);
            locationMapper.updateById(location);
            evidenceService.record(HarvChangeEvidence.ENTITY_LOCATION, location.getId(),
                    HarvChangeEvidence.ACTION_ALLOCATION_INSPECT, requestId, operator, before, location,
                    detail + "；库位待检标识同步为" + (pending ? "待检" : "检验完成"));
        }
    }

    /** 库位内当前有效（USABLE 分仓结果）在仓重量。 */
    public BigDecimal activeWeightAt(Long locationId) {
        return activeLinesAt(locationId).stream()
                .map(HarvAllocationLine::getActualWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 库位内仍生效（所属分仓登记为 USABLE）的明细行；VOIDED 作废结果的行不计入。 */
    private List<HarvAllocationLine> activeLinesAt(Long locationId) {
        List<HarvAllocationLine> lines = lineMapper.selectList(new QueryWrapper<HarvAllocationLine>()
                .eq("location_id", locationId).orderByAsc("id"));
        Set<Long> usableAllocationIds = new HashSet<>();
        for (HarvAllocationLine line : lines) {
            if (usableAllocationIds.contains(line.getAllocationId())) {
                continue;
            }
            HarvAllocation allocation = allocationMapper.selectById(line.getAllocationId());
            if (allocation != null && HarvAllocation.RESULT_USABLE.equals(allocation.getResultStatus())) {
                usableAllocationIds.add(line.getAllocationId());
            }
        }
        lines.removeIf(line -> !usableAllocationIds.contains(line.getAllocationId()));
        return lines;
    }

    private HarvLocation copyOf(HarvLocation location) {
        HarvLocation copy = new HarvLocation();
        org.springframework.beans.BeanUtils.copyProperties(location, copy);
        return copy;
    }
}
