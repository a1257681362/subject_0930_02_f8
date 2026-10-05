package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 捕捞库位（仓/箱）：按海区登记，容量为可承载重量上限（kg）。
 * 状态 AVAILABLE 可用 / LOCKED 已被预约锁定 / OCCUPIED 现场已分仓占用 / RELEASED 已释放清空。
 * pendingInspect 待检标识：分仓落位置 TRUE，库位内全部分仓行检验通过后同步解除。
 * 预约锁定后 reservationId 指向占用预约，其他订单不得再占用。
 */
@TableName("t_harv_location")
public class HarvLocation extends BaseEntity {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_OCCUPIED = "OCCUPIED";
    public static final String STATUS_RELEASED = "RELEASED";

    private String locationCode;
    private String seaArea;
    private BigDecimal capacityKg;
    private String status;
    private Boolean pendingInspect;
    private Long reservationId;
    private String requestId;

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getPendingInspect() { return pendingInspect; }
    public void setPendingInspect(Boolean pendingInspect) { this.pendingInspect = pendingInspect; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
