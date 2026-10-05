package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evops.aqua.harv.dto.AllocationRecordRequest;
import com.evops.aqua.harv.dto.InspectionPassRequest;
import com.evops.aqua.harv.dto.ReservationActionRequest;
import com.evops.aqua.harv.dto.ReservationLockRequest;
import com.evops.aqua.harv.dto.VehicleChangeRequest;
import com.evops.aqua.harv.entity.HarvAllocation;
import com.evops.aqua.harv.entity.HarvAllocationLine;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvCustomerOrder;
import com.evops.aqua.harv.entity.HarvGradeRule;
import com.evops.aqua.harv.entity.HarvLocation;
import com.evops.aqua.harv.entity.HarvNotification;
import com.evops.aqua.harv.entity.HarvReservation;
import com.evops.aqua.harv.entity.HarvVariance;
import com.evops.aqua.harv.entity.HarvVehicleChange;
import com.evops.aqua.harv.mapper.HarvAllocationLineMapper;
import com.evops.aqua.harv.mapper.HarvAllocationMapper;
import com.evops.aqua.harv.mapper.HarvReservationMapper;
import com.evops.aqua.harv.mapper.HarvVehicleChangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 捕捞预约主流程：锁定库位车辆（互斥）→ 现场按实际规格分仓（同步库位状态与待检标识）
 * → 短装/超装差异单与客户通知 → 检验通过同步解除待检 → 临时换车留因并通知客户。
 * 分仓回传区分 NORMAL/RETRANSMIT/BACKFILL/RETRY：同一预约永远只有一条 USABLE 可用结果。
 */
@Service
public class HarvReservationService {

    private final HarvReservationMapper reservationMapper;
    private final HarvAllocationMapper allocationMapper;
    private final HarvAllocationLineMapper lineMapper;
    private final HarvVehicleChangeMapper vehicleChangeMapper;
    private final HarvOrderService orderService;
    private final HarvLocationService locationService;
    private final HarvGradeService gradeService;
    private final HarvVarianceService varianceService;
    private final HarvNotificationService notificationService;
    private final HarvEvidenceService evidenceService;

    public HarvReservationService(HarvReservationMapper reservationMapper,
                                  HarvAllocationMapper allocationMapper,
                                  HarvAllocationLineMapper lineMapper,
                                  HarvVehicleChangeMapper vehicleChangeMapper,
                                  HarvOrderService orderService,
                                  HarvLocationService locationService,
                                  HarvGradeService gradeService,
                                  HarvVarianceService varianceService,
                                  HarvNotificationService notificationService,
                                  HarvEvidenceService evidenceService) {
        this.reservationMapper = reservationMapper;
        this.allocationMapper = allocationMapper;
        this.lineMapper = lineMapper;
        this.vehicleChangeMapper = vehicleChangeMapper;
        this.orderService = orderService;
        this.locationService = locationService;
        this.gradeService = gradeService;
        this.varianceService = varianceService;
        this.notificationService = notificationService;
        this.evidenceService = evidenceService;
    }

    /**
     * 锁定预约：独占绑定库位与车辆。库位必须 AVAILABLE 且容量足够；
     * 锁定后其他订单预约再占用一律拒绝。requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvReservation lock(Long reservationId, ReservationLockRequest request) {
        HarvReservation reservation = getOrThrow(reservationId);
        if (alreadyDone(HarvChangeEvidence.ACTION_RESERVATION_LOCK, reservationId, request.getRequestId())) {
            return reservation;
        }
        if (HarvReservation.STATUS_LOCKED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("预约" + reservation.getReservationNo()
                    + "已锁定库位id=" + reservation.getLocationId() + "，不能重复锁定");
        }
        if (!HarvReservation.STATUS_PLANNED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("仅待锁定（PLANNED）预约可锁定，当前状态："
                    + reservation.getStatus());
        }
        HarvLocation location = locationService.getOrThrow(request.getLocationId());
        locationService.lockTo(reservation, location, request.getOperator(), request.getRequestId());

        HarvReservation before = copyOf(reservation);
        reservation.setStatus(HarvReservation.STATUS_LOCKED);
        reservation.setLocationId(location.getId());
        reservation.setVehicleNo(request.getVehicleNo());
        reservationMapper.updateById(reservation);
        evidenceService.record(HarvChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                HarvChangeEvidence.ACTION_RESERVATION_LOCK, request.getRequestId(), request.getOperator(),
                before, reservation, "预约锁定库位" + location.getLocationCode()
                        + "、车辆" + request.getVehicleNo() + "，锁定后不被其他订单占用");
        return reservation;
    }

    /**
     * 释放锁定：仅 LOCKED 可释放，库位恢复 AVAILABLE，预约回到 PLANNED。requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvReservation unlock(Long reservationId, ReservationActionRequest request) {
        HarvReservation reservation = getOrThrow(reservationId);
        if (HarvReservation.STATUS_PLANNED.equals(reservation.getStatus())
                || alreadyDone(HarvChangeEvidence.ACTION_RESERVATION_UNLOCK, reservationId, request.getRequestId())) {
            return reservation;
        }
        if (!HarvReservation.STATUS_LOCKED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("仅已锁定（LOCKED）预约可释放，当前状态：" + reservation.getStatus());
        }
        HarvReservation before = copyOf(reservation);
        locationService.releaseFrom(reservation, request.getOperator(), request.getRequestId());
        reservationMapper.update(null, new LambdaUpdateWrapper<HarvReservation>()
                .eq(HarvReservation::getId, reservation.getId())
                .set(HarvReservation::getStatus, HarvReservation.STATUS_PLANNED)
                .set(HarvReservation::getLocationId, null)
                .set(HarvReservation::getVehicleNo, null));
        reservation.setStatus(HarvReservation.STATUS_PLANNED);
        reservation.setLocationId(null);
        reservation.setVehicleNo(null);
        evidenceService.record(HarvChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                HarvChangeEvidence.ACTION_RESERVATION_UNLOCK, request.getRequestId(), request.getOperator(),
                before, reservation, "释放预约锁定，库位恢复可用");
        return reservation;
    }

    /**
     * 现场分仓回传：按实际规格逐行落位。
     * 同一 requestId 重试（含补传/补录请求重试）原样返回首次结果；
     * 新 requestId 的补传 RETRANSMIT/补录 BACKFILL/重试 RETRY 会把旧 USABLE 结果置 VOIDED 后重建——
     * 任何时刻该预约只有一条 USABLE 可用结果，作废结果只追加留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordAllocation(Long reservationId, AllocationRecordRequest request) {
        HarvReservation reservation = getOrThrow(reservationId);

        HarvAllocation sameRequest = allocationMapper.selectOne(new QueryWrapper<HarvAllocation>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (sameRequest != null) {
            return allocationDetail(sameRequest.getId());
        }

        String source = normalizeSource(request.getSource());
        if (!HarvReservation.STATUS_LOCKED.equals(reservation.getStatus())
                && !HarvReservation.STATUS_ALLOCATED.equals(reservation.getStatus())) {
            String reason = "仅锁定后预约可现场分仓，当前状态：" + reservation.getStatus();
            evidenceService.recordStandalone(HarvChangeEvidence.ENTITY_RESERVATION, reservationId,
                    HarvChangeEvidence.ACTION_ALLOCATION_RECORD, request.getRequestId(),
                    request.getOperator(), reservation, null, "分仓回传被拒绝：" + reason);
            throw new IllegalArgumentException(reason);
        }

        // 先校验整次回传：实际规格必须命中订单冻结版本的分级规则、落位库位必须存在。
        // 校验全部通过后才允许作废旧结果，避免非法回传破坏既有唯一可用结果。
        List<HarvAllocationLine> lines = new ArrayList<>();
        Map<Long, BigDecimal> weightByLocation = new LinkedHashMap<>();
        for (AllocationRecordRequest.AllocationLineRequest lineRequest : request.getLines()) {
            HarvGradeRule rule = gradeService.ruleOf(reservation.getGradeVersionId(),
                    lineRequest.getActualGradeCode());
            if (rule == null) {
                throw new IllegalArgumentException("实际规格代码" + lineRequest.getActualGradeCode()
                        + "不在预约保留的分级版本" + reservation.getGradeVersionNo() + "中");
            }
            HarvLocation location = locationService.getOrThrow(lineRequest.getLocationId());
            HarvAllocationLine line = new HarvAllocationLine();
            line.setReservationId(reservationId);
            line.setActualGradeCode(rule.getGradeCode());
            line.setActualGradeName(rule.getGradeName());
            line.setActualWeightKg(lineRequest.getActualWeightKg());
            line.setLocationId(location.getId());
            line.setPendingInspect(true);
            line.setInspectPassed(false);
            lines.add(line);
            weightByLocation.merge(location.getId(), lineRequest.getActualWeightKg(), BigDecimal::add);
        }

        // 新 requestId 且已有可用结果：作废旧结果（含其分仓行、库位占用、差异单），保证只留一条 USABLE。
        HarvAllocation existing = findUsable(reservationId);
        if (existing != null) {
            voidAllocation(existing, reservation, request.getOperator(), request.getRequestId(), source);
            reservation = getOrThrow(reservationId);
        }

        // 作废旧结果后再占用落位（在仓重量按仍生效结果计），校验互斥与容量。
        for (Map.Entry<Long, BigDecimal> entry : weightByLocation.entrySet()) {
            HarvLocation location = locationService.getOrThrow(entry.getKey());
            locationService.occupyFor(reservation, location, entry.getValue(),
                    request.getOperator(), request.getRequestId());
        }

        HarvAllocation allocation = new HarvAllocation();
        allocation.setAllocationNo(nextAllocationNo(reservationId));
        allocation.setReservationId(reservationId);
        allocation.setOrderId(reservation.getOrderId());
        allocation.setSource(source);
        allocation.setResultStatus(HarvAllocation.RESULT_USABLE);
        allocation.setUsableKey(reservationId);
        allocation.setOperator(request.getOperator());
        allocation.setRemark(request.getRemark());
        allocation.setRequestId(request.getRequestId());
        allocationMapper.insert(allocation);
        BigDecimal total = BigDecimal.ZERO;
        for (HarvAllocationLine line : lines) {
            line.setAllocationId(allocation.getId());
            lineMapper.insert(line);
            total = total.add(line.getActualWeightKg());
        }

        HarvReservation before = copyOf(reservation);
        reservation.setStatus(HarvReservation.STATUS_ALLOCATED);
        reservation.setActualTotalKg(total);
        reservation.setActualTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);

        String sourceText = sourceText(source);
        evidenceService.record(HarvChangeEvidence.ENTITY_ALLOCATION, allocation.getId(),
                HarvChangeEvidence.ACTION_ALLOCATION_RECORD, request.getRequestId(), request.getOperator(),
                before, allocationDetail(allocation.getId()),
                sourceText + "现场分仓" + lines.size() + "行，实际合计" + total + "kg，库位置占用并打待检标识");

        // 短装/超装对账：实际总量落在计划闭区间之外即生成差异单并通知客户；在区间内不生成。
        HarvVariance variance = varianceService.generate(reservationId, reservation.getOrderId(),
                allocation.getId(), reservation.getPlannedMinKg(), reservation.getPlannedMaxKg(),
                total, request.getOperator(), request.getRequestId());
        if (variance != null) {
            HarvCustomerOrder order = orderService.getOrThrow(reservation.getOrderId());
            String word = HarvVariance.TYPE_SHORT.equals(variance.getVarianceType()) ? "短装" : "超装";
            notificationService.notify(order.getCustomerId(), order.getId(), reservationId,
                    HarvNotification.EVENT_VARIANCE, "V" + variance.getId(),
                    "您的订单" + order.getOrderNo() + "预约" + reservation.getReservationNo()
                            + "出现" + word + "：计划" + variance.getPlannedMinKg() + "~"
                            + variance.getPlannedMaxKg() + "kg，实际" + variance.getActualKg()
                            + "kg，" + word + differenceText(variance) + "kg，请知悉并确认处置。",
                    request.getOperator(), request.getRequestId());
        }
        return allocationDetail(allocation.getId());
    }

    /**
     * 分仓行检验通过：解除该行待检；库位内全部行通过后库位待检标识同步解除。
     * 已通过行重复登记幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvAllocationLine passInspection(InspectionPassRequest request) {
        HarvAllocationLine line = lineMapper.selectById(request.getLineId());
        if (line == null) {
            throw new IllegalArgumentException("分仓明细行不存在，id=" + request.getLineId());
        }
        HarvAllocation allocation = allocationMapper.selectById(line.getAllocationId());
        if (allocation == null || !HarvAllocation.RESULT_USABLE.equals(allocation.getResultStatus())) {
            throw new IllegalArgumentException("分仓结果已作废，不能登记检验");
        }
        if (Boolean.TRUE.equals(line.getInspectPassed())) {
            return line;
        }
        HarvAllocationLine before = copyOf(line);
        line.setInspectPassed(true);
        line.setPendingInspect(false);
        line.setInspectBy(request.getInspectBy());
        line.setInspectTime(LocalDateTime.now());
        lineMapper.updateById(line);
        evidenceService.record(HarvChangeEvidence.ENTITY_ALLOCATION, allocation.getId(),
                HarvChangeEvidence.ACTION_ALLOCATION_INSPECT, request.getRequestId(), request.getInspectBy(),
                before, line, "分仓行id=" + line.getId() + "检验通过，解除该行待检");
        locationService.syncPendingFlag(line.getLocationId(), request.getInspectBy(),
                request.getRequestId(), "分仓行id=" + line.getId() + "检验通过");
        return line;
    }

    /**
     * 临时换车：仅 LOCKED/ALLOCATED 预约可换，新车牌不得与现车相同，原因必填；
     * 逐次只追加并触发客户通知。requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvVehicleChange changeVehicle(Long reservationId, VehicleChangeRequest request) {
        HarvReservation reservation = getOrThrow(reservationId);
        HarvVehicleChange duplicated = vehicleChangeMapper.selectOne(new QueryWrapper<HarvVehicleChange>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        if (!HarvReservation.STATUS_LOCKED.equals(reservation.getStatus())
                && !HarvReservation.STATUS_ALLOCATED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("仅锁定后（LOCKED/ALLOCATED）预约可临时换车，当前状态："
                    + reservation.getStatus());
        }
        if (reservation.getVehicleNo() == null) {
            throw new IllegalArgumentException("预约尚未绑定车辆，不能换车");
        }
        if (request.getToVehicleNo().equals(reservation.getVehicleNo())) {
            throw new IllegalArgumentException("新车牌与现车牌相同，无需换车：" + reservation.getVehicleNo());
        }
        HarvVehicleChange change = new HarvVehicleChange();
        change.setReservationId(reservationId);
        change.setOrderId(reservation.getOrderId());
        change.setFromVehicleNo(reservation.getVehicleNo());
        change.setToVehicleNo(request.getToVehicleNo());
        change.setReason(request.getReason());
        change.setOperator(request.getOperator());
        change.setChangeTime(LocalDateTime.now());
        change.setRequestId(request.getRequestId());
        vehicleChangeMapper.insert(change);

        HarvReservation before = copyOf(reservation);
        reservation.setVehicleNo(request.getToVehicleNo());
        reservationMapper.updateById(reservation);
        evidenceService.record(HarvChangeEvidence.ENTITY_VEHICLE_CHANGE, change.getId(),
                HarvChangeEvidence.ACTION_VEHICLE_CHANGE, request.getRequestId(), request.getOperator(),
                before, change, "临时换车：" + change.getFromVehicleNo() + "→" + change.getToVehicleNo()
                        + "，原因：" + request.getReason());

        HarvCustomerOrder order = orderService.getOrThrow(reservation.getOrderId());
        notificationService.notify(order.getCustomerId(), order.getId(), reservationId,
                HarvNotification.EVENT_VEHICLE_CHANGE, "C" + change.getId(),
                "您的订单" + order.getOrderNo() + "预约" + reservation.getReservationNo()
                        + "运输车辆临时变更：" + change.getFromVehicleNo() + "→" + change.getToVehicleNo()
                        + "。原因：" + request.getReason() + "。给您带来不便敬请谅解。",
                request.getOperator(), request.getRequestId());
        return change;
    }

    public HarvReservation getOrThrow(Long id) {
        HarvReservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new IllegalArgumentException("捕捞预约不存在，id=" + id);
        }
        return reservation;
    }

    public List<HarvReservation> list(Long orderId, String status, Long locationId) {
        QueryWrapper<HarvReservation> wrapper = new QueryWrapper<>();
        wrapper.eq(orderId != null, "order_id", orderId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(locationId != null, "location_id", locationId);
        wrapper.orderByAsc("id");
        return reservationMapper.selectList(wrapper);
    }

    /** 预约当前可用分仓结果（含明细与差异单）；无结果返回 null。 */
    public Map<String, Object> usableAllocation(Long reservationId) {
        HarvAllocation usable = findUsable(reservationId);
        return usable == null ? null : allocationDetail(usable.getId());
    }

    public Map<String, Object> allocationDetail(Long allocationId) {
        HarvAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new IllegalArgumentException("分仓登记不存在，id=" + allocationId);
        }
        List<HarvAllocationLine> lines = lineMapper.selectList(new QueryWrapper<HarvAllocationLine>()
                .eq("allocation_id", allocationId).orderByAsc("id"));
        List<HarvVariance> variances = varianceService.list(allocation.getReservationId(), null).stream()
                .filter(v -> allocationId.equals(v.getAllocationId()))
                .collect(java.util.stream.Collectors.toList());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("allocation", allocation);
        detail.put("lines", lines);
        detail.put("variances", variances);
        return detail;
    }

    private HarvAllocation findUsable(Long reservationId) {
        return allocationMapper.selectOne(new QueryWrapper<HarvAllocation>()
                .eq("reservation_id", reservationId)
                .eq("result_status", HarvAllocation.RESULT_USABLE)
                .last("limit 1"));
    }

    /**
     * 作废旧可用结果：删旧分仓行、清空旧库位占用并恢复预约锁定库位、作废其差异单，
     * 预约回 LOCKED 等待重新分仓；旧结果头置 VOIDED（usableKey 置空）只追加留痕。
     */
    private void voidAllocation(HarvAllocation existing, HarvReservation reservation,
                                String operator, String requestId, String newSource) {
        List<HarvAllocationLine> oldLines = lineMapper.selectList(new QueryWrapper<HarvAllocationLine>()
                .eq("allocation_id", existing.getId()));
        Set<Long> oldLocationIds = new LinkedHashSet<>();
        for (HarvAllocationLine oldLine : oldLines) {
            oldLocationIds.add(oldLine.getLocationId());
            lineMapper.deleteById(oldLine.getId());
        }
        locationService.clearAfterVoid(new ArrayList<>(oldLocationIds), operator, requestId);
        // 预约锁定库位无论是否在旧落位中，清空后都恢复为对本预约的互斥锁定，新结果可再次占用。
        if (reservation.getLocationId() != null) {
            locationService.restoreLocked(reservation, operator, requestId);
        }
        varianceService.deleteByAllocation(existing.getId(), operator, requestId);

        existing.setResultStatus(HarvAllocation.RESULT_VOIDED);
        existing.setUsableKey(null);
        allocationMapper.update(null, new LambdaUpdateWrapper<HarvAllocation>()
                .eq(HarvAllocation::getId, existing.getId())
                .set(HarvAllocation::getResultStatus, HarvAllocation.RESULT_VOIDED)
                .set(HarvAllocation::getUsableKey, null));
        evidenceService.record(HarvChangeEvidence.ENTITY_ALLOCATION, existing.getId(),
                HarvChangeEvidence.ACTION_ALLOCATION_VOID, requestId, operator,
                existing, null, "收到" + sourceText(newSource) + "，旧分仓结果"
                        + existing.getAllocationNo() + "作废，仅保留一条可用结果");

        reservation.setStatus(HarvReservation.STATUS_LOCKED);
        reservation.setActualTotalKg(null);
        reservation.setActualTime(null);
        reservationMapper.update(null, new LambdaUpdateWrapper<HarvReservation>()
                .eq(HarvReservation::getId, reservation.getId())
                .set(HarvReservation::getStatus, HarvReservation.STATUS_LOCKED)
                .set(HarvReservation::getActualTotalKg, null)
                .set(HarvReservation::getActualTime, null));
    }

    private String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return HarvAllocation.SOURCE_NORMAL;
        }
        String normalized = source.trim().toUpperCase();
        if (!Arrays.asList(HarvAllocation.SOURCE_NORMAL, HarvAllocation.SOURCE_RETRANSMIT,
                HarvAllocation.SOURCE_BACKFILL, HarvAllocation.SOURCE_RETRY).contains(normalized)) {
            throw new IllegalArgumentException("不支持的回传来源：" + source
                    + "，仅支持 NORMAL/RETRANSMIT/BACKFILL/RETRY");
        }
        return normalized;
    }

    private String sourceText(String source) {
        if (HarvAllocation.SOURCE_RETRANSMIT.equals(source)) {
            return "补传";
        }
        if (HarvAllocation.SOURCE_BACKFILL.equals(source)) {
            return "补录";
        }
        if (HarvAllocation.SOURCE_RETRY.equals(source)) {
            return "重试";
        }
        return "正常回传";
    }

    private String differenceText(HarvVariance variance) {
        return HarvVariance.TYPE_SHORT.equals(variance.getVarianceType())
                ? "缺口" + variance.getDiffKg()
                : "超出" + variance.getDiffKg();
    }

    private String nextAllocationNo(Long reservationId) {
        Long count = allocationMapper.selectCount(new QueryWrapper<HarvAllocation>()
                .eq("reservation_id", reservationId));
        return "AL-" + reservationId + "-" + (count + 1);
    }

    private boolean alreadyDone(String action, Long reservationId, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        return evidenceService.list(HarvChangeEvidence.ENTITY_RESERVATION, reservationId, requestId)
                .stream().anyMatch(e -> action.equals(e.getAction()));
    }

    private HarvReservation copyOf(HarvReservation reservation) {
        HarvReservation copy = new HarvReservation();
        org.springframework.beans.BeanUtils.copyProperties(reservation, copy);
        return copy;
    }

    private HarvAllocationLine copyOf(HarvAllocationLine line) {
        HarvAllocationLine copy = new HarvAllocationLine();
        org.springframework.beans.BeanUtils.copyProperties(line, copy);
        return copy;
    }
}
