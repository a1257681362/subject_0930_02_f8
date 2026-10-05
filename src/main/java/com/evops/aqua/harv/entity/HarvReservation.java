package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捕捞预约：客户订单按「规格 × 计划重量区间 × 交付时间」拆分，一行一预约。
 * plannedMinKg/plannedMaxKg 为该规格预约重量闭区间；建单时冻结分级规则行快照
 * （gradeCode/Name/VersionNo + gradeMin/MaxWeightKg），历史预约永远引用当时规则。
 * 状态 PLANNED 已拆分待锁定 / LOCKED 已锁定库位车辆 / ALLOCATED 现场已分仓 /
 * COMPLETED 已完成 / RELEASED 已释放 / CANCELLED 已取消。
 * 锁定后 locationId 指向被独占库位，其他订单预约不得再占用。
 */
@TableName("t_harv_reservation")
public class HarvReservation extends BaseEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_ALLOCATED = "ALLOCATED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String reservationNo;
    private Long orderId;
    private Long pondId;
    private String gradeCode;
    private String gradeName;
    private Long gradeVersionId;
    private String gradeVersionNo;
    private BigDecimal gradeMinWeightKg;
    private BigDecimal gradeMaxWeightKg;
    private BigDecimal plannedMinKg;
    private BigDecimal plannedMaxKg;
    private LocalDateTime deliveryTime;
    private String status;
    private Long locationId;
    private String vehicleNo;
    private BigDecimal actualTotalKg;
    private LocalDateTime actualTime;
    private String requestId;

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public Long getGradeVersionId() { return gradeVersionId; }
    public void setGradeVersionId(Long gradeVersionId) { this.gradeVersionId = gradeVersionId; }
    public String getGradeVersionNo() { return gradeVersionNo; }
    public void setGradeVersionNo(String gradeVersionNo) { this.gradeVersionNo = gradeVersionNo; }
    public BigDecimal getGradeMinWeightKg() { return gradeMinWeightKg; }
    public void setGradeMinWeightKg(BigDecimal gradeMinWeightKg) { this.gradeMinWeightKg = gradeMinWeightKg; }
    public BigDecimal getGradeMaxWeightKg() { return gradeMaxWeightKg; }
    public void setGradeMaxWeightKg(BigDecimal gradeMaxWeightKg) { this.gradeMaxWeightKg = gradeMaxWeightKg; }
    public BigDecimal getPlannedMinKg() { return plannedMinKg; }
    public void setPlannedMinKg(BigDecimal plannedMinKg) { this.plannedMinKg = plannedMinKg; }
    public BigDecimal getPlannedMaxKg() { return plannedMaxKg; }
    public void setPlannedMaxKg(BigDecimal plannedMaxKg) { this.plannedMaxKg = plannedMaxKg; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(LocalDateTime deliveryTime) { this.deliveryTime = deliveryTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public BigDecimal getActualTotalKg() { return actualTotalKg; }
    public void setActualTotalKg(BigDecimal actualTotalKg) { this.actualTotalKg = actualTotalKg; }
    public LocalDateTime getActualTime() { return actualTime; }
    public void setActualTime(LocalDateTime actualTime) { this.actualTime = actualTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
