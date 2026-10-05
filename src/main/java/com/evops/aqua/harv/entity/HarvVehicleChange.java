package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 临时换车记录：预约锁定后车辆变更必须登记原因（reason 必填）并触发客户通知。
 * 每次换车一行，只追加，不覆盖历史；同 requestId 幂等。
 */
@TableName("t_harv_vehicle_change")
public class HarvVehicleChange extends BaseEntity {

    private Long reservationId;
    private Long orderId;
    private String fromVehicleNo;
    private String toVehicleNo;
    private String reason;
    private String operator;
    private LocalDateTime changeTime;
    private String requestId;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getFromVehicleNo() { return fromVehicleNo; }
    public void setFromVehicleNo(String fromVehicleNo) { this.fromVehicleNo = fromVehicleNo; }
    public String getToVehicleNo() { return toVehicleNo; }
    public void setToVehicleNo(String toVehicleNo) { this.toVehicleNo = toVehicleNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
