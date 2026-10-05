package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户订单：建单即冻结当时生效的分级规则版本指针（gradeVersionId/No）。
 * 状态 DRAFT 草稿（可拆分预约）/ CONFIRMED 已确认（预约已拆分）/ COMPLETED 已完成 / CANCELLED 已取消。
 * COMPLETED 后永久保留建单时版本——后续发布新分级版本不影响该订单及其预约的分级口径。
 */
@TableName("t_harv_customer_order")
public class HarvCustomerOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String orderNo;
    private Long customerId;
    private Long pondId;
    private String species;
    private Long gradeVersionId;
    private String gradeVersionNo;
    private String status;
    private String operator;
    private LocalDateTime completedTime;
    private String requestId;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public Long getGradeVersionId() { return gradeVersionId; }
    public void setGradeVersionId(Long gradeVersionId) { this.gradeVersionId = gradeVersionId; }
    public String getGradeVersionNo() { return gradeVersionNo; }
    public void setGradeVersionNo(String gradeVersionNo) { this.gradeVersionNo = gradeVersionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
