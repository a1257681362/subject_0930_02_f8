package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户通知：临时换车（VEHICLE_CHANGE）/短装超装（VARIANCE）等事件通知客户。
 * SENT 已发送 / ACKED 客户已签收；重复签收幂等，不覆盖首次回执。
 */
@TableName("t_harv_notification")
public class HarvNotification extends BaseEntity {

    public static final String EVENT_VEHICLE_CHANGE = "VEHICLE_CHANGE";
    public static final String EVENT_VARIANCE = "VARIANCE";

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACKED = "ACKED";

    private String noticeNo;
    private Long customerId;
    private Long orderId;
    private Long reservationId;
    private String eventType;
    private String message;
    private String status;
    private String ackBy;
    private LocalDateTime ackTime;
    private String ackRemark;

    public String getNoticeNo() { return noticeNo; }
    public void setNoticeNo(String noticeNo) { this.noticeNo = noticeNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
}
