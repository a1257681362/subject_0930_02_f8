package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分仓明细行：按实际规格（actualGradeCode，可能与预约规格不一致）记录实际重量与落位库位。
 * 每行落位即把库位置 OCCUPIED 并打待检标识（pendingInspect=TRUE）；
 * 检验通过 inspectPassed=TRUE 后解除该行待检；库位内全部行通过后库位待检标识同步解除。
 */
@TableName("t_harv_allocation_line")
public class HarvAllocationLine extends BaseEntity {

    private Long allocationId;
    private Long reservationId;
    private String actualGradeCode;
    private String actualGradeName;
    private BigDecimal actualWeightKg;
    private Long locationId;
    private Boolean pendingInspect;
    private Boolean inspectPassed;
    private String inspectBy;
    private LocalDateTime inspectTime;

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getActualGradeCode() { return actualGradeCode; }
    public void setActualGradeCode(String actualGradeCode) { this.actualGradeCode = actualGradeCode; }
    public String getActualGradeName() { return actualGradeName; }
    public void setActualGradeName(String actualGradeName) { this.actualGradeName = actualGradeName; }
    public BigDecimal getActualWeightKg() { return actualWeightKg; }
    public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Boolean getPendingInspect() { return pendingInspect; }
    public void setPendingInspect(Boolean pendingInspect) { this.pendingInspect = pendingInspect; }
    public Boolean getInspectPassed() { return inspectPassed; }
    public void setInspectPassed(Boolean inspectPassed) { this.inspectPassed = inspectPassed; }
    public String getInspectBy() { return inspectBy; }
    public void setInspectBy(String inspectBy) { this.inspectBy = inspectBy; }
    public LocalDateTime getInspectTime() { return inspectTime; }
    public void setInspectTime(LocalDateTime inspectTime) { this.inspectTime = inspectTime; }
}
