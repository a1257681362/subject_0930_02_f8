package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.harv.dto.VarianceHandleRequest;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvVariance;
import com.evops.aqua.harv.mapper.HarvVarianceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 差异单：现场实际总量落在预约计划重量区间之外即生成 SHORT 短装 / OVER 超装。
 * 一张可用分仓结果至多一张差异单；差异单只追加，处置登记不改变短/超事实。
 */
@Service
public class HarvVarianceService {

    private final HarvVarianceMapper varianceMapper;
    private final HarvEvidenceService evidenceService;

    public HarvVarianceService(HarvVarianceMapper varianceMapper,
                               HarvEvidenceService evidenceService) {
        this.varianceMapper = varianceMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 按实际总量与计划闭区间判定并生成差异单；在区间内不生成，返回 null。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvVariance generate(Long reservationId, Long orderId, Long allocationId,
                                 BigDecimal plannedMin, BigDecimal plannedMax, BigDecimal actual,
                                 String operator, String requestId) {
        String type = null;
        BigDecimal diff = null;
        if (actual.compareTo(plannedMin) < 0) {
            type = HarvVariance.TYPE_SHORT;
            diff = plannedMin.subtract(actual);
        } else if (actual.compareTo(plannedMax) > 0) {
            type = HarvVariance.TYPE_OVER;
            diff = actual.subtract(plannedMax);
        }
        if (type == null) {
            return null;
        }
        HarvVariance variance = new HarvVariance();
        variance.setVarianceNo("VR-" + allocationId);
        variance.setReservationId(reservationId);
        variance.setAllocationId(allocationId);
        variance.setOrderId(orderId);
        variance.setVarianceType(type);
        variance.setPlannedMinKg(plannedMin);
        variance.setPlannedMaxKg(plannedMax);
        variance.setActualKg(actual);
        variance.setDiffKg(diff);
        variance.setHandled(false);
        varianceMapper.insert(variance);
        evidenceService.record(HarvChangeEvidence.ENTITY_VARIANCE, variance.getId(),
                HarvChangeEvidence.ACTION_VARIANCE_CREATE, requestId, operator,
                null, variance,
                (HarvVariance.TYPE_SHORT.equals(type) ? "短装" : "超装")
                        + "差异单：计划区间" + plannedMin + "~" + plannedMax + "kg，实际"
                        + actual + "kg，差异" + diff + "kg");
        return variance;
    }

    /** 作废结果下的历史差异单随结果一并作废（删除行并留痕到预约实体），保证新结果可重新对账。 */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByAllocation(Long allocationId, String operator, String requestId) {
        List<HarvVariance> variances = varianceMapper.selectList(new QueryWrapper<HarvVariance>()
                .eq("allocation_id", allocationId));
        for (HarvVariance variance : variances) {
            evidenceService.record(HarvChangeEvidence.ENTITY_VARIANCE, variance.getId(),
                    HarvChangeEvidence.ACTION_ALLOCATION_VOID, requestId, operator,
                    variance, null, "旧分仓结果作废，其差异单" + variance.getVarianceNo()
                            + "（" + variance.getVarianceType() + "）随之作废，等待重新对账");
            varianceMapper.deleteById(variance.getId());
        }
        return variances.size();
    }

    /** 差异处置：只登记处理意见，重复处置幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public HarvVariance handle(Long id, VarianceHandleRequest request) {
        HarvVariance variance = getOrThrow(id);
        if (Boolean.TRUE.equals(variance.getHandled())) {
            return variance;
        }
        HarvVariance before = copyOf(variance);
        variance.setHandled(true);
        variance.setHandleBy(request.getHandleBy());
        variance.setHandleRemark(request.getHandleRemark());
        variance.setHandleTime(LocalDateTime.now());
        varianceMapper.updateById(variance);
        evidenceService.record(HarvChangeEvidence.ENTITY_VARIANCE, variance.getId(),
                HarvChangeEvidence.ACTION_VARIANCE_HANDLE, request.getRequestId(), request.getHandleBy(),
                before, variance, "差异单处置：" + request.getHandleRemark());
        return variance;
    }

    public HarvVariance getOrThrow(Long id) {
        HarvVariance variance = varianceMapper.selectById(id);
        if (variance == null) {
            throw new IllegalArgumentException("差异单不存在，id=" + id);
        }
        return variance;
    }

    public List<HarvVariance> list(Long reservationId, Long orderId) {
        QueryWrapper<HarvVariance> wrapper = new QueryWrapper<>();
        wrapper.eq(reservationId != null, "reservation_id", reservationId);
        wrapper.eq(orderId != null, "order_id", orderId);
        wrapper.orderByAsc("id");
        return varianceMapper.selectList(wrapper);
    }

    private HarvVariance copyOf(HarvVariance variance) {
        HarvVariance copy = new HarvVariance();
        org.springframework.beans.BeanUtils.copyProperties(variance, copy);
        return copy;
    }
}
