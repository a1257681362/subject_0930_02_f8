package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 差异单：预约实际总重量落在计划重量闭区间之外即生成，一次可用分仓结果至多一张差异单。
 * SHORT 短装（实际 &lt; 计划下限，diffKg 为缺口正数）/ OVER 超装（实际 &gt; 计划上限，diffKg 为超出正数）。
 * 差异单只追加；处置登记不改变短/超事实。
 */
@TableName("t_harv_variance")
public class HarvVariance extends BaseEntity {

    public static final String TYPE_SHORT = "SHORT";
    public static final String TYPE_OVER = "OVER";

    private String varianceNo;
    private Long reservationId;
    private Long allocationId;
    private Long orderId;
    private String varianceType;
    private BigDecimal plannedMinKg;
    private BigDecimal plannedMaxKg;
    private BigDecimal actualKg;
    private BigDecimal diffKg;
    private Boolean handled;
    private String handleRemark;
    private String handleBy;
    private LocalDateTime handleTime;

    public String getVarianceNo() { return varianceNo; }
    public void setVarianceNo(String varianceNo) { this.varianceNo = varianceNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getVarianceType() { return varianceType; }
    public void setVarianceType(String varianceType) { this.varianceType = varianceType; }
    public BigDecimal getPlannedMinKg() { return plannedMinKg; }
    public void setPlannedMinKg(BigDecimal plannedMinKg) { this.plannedMinKg = plannedMinKg; }
    public BigDecimal getPlannedMaxKg() { return plannedMaxKg; }
    public void setPlannedMaxKg(BigDecimal plannedMaxKg) { this.plannedMaxKg = plannedMaxKg; }
    public BigDecimal getActualKg() { return actualKg; }
    public void setActualKg(BigDecimal actualKg) { this.actualKg = actualKg; }
    public BigDecimal getDiffKg() { return diffKg; }
    public void setDiffKg(BigDecimal diffKg) { this.diffKg = diffKg; }
    public Boolean getHandled() { return handled; }
    public void setHandled(Boolean handled) { this.handled = handled; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public String getHandleBy() { return handleBy; }
    public void setHandleBy(String handleBy) { this.handleBy = handleBy; }
    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }
}
