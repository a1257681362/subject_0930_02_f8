package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 现场分仓登记（头）：一个预约的一次回传登记，按实际规格分多行落位。
 * 来源 source：NORMAL 正常回传 / RETRANSMIT 补传 / BACKFILL 补录 / RETRY 重试。
 * 结果 resultStatus：USABLE 可用结果 / VOIDED 被新版本结果顶替的作废结果（只追加留痕）。
 * 同一预约永远最多一条 USABLE（usableKey=预约id 唯一）——补传/补录/重试要么复用同一 requestId
 * 返回原结果，要么作废旧结果后新建一条，绝不形成第二条可用结果。
 */
@TableName("t_harv_allocation")
public class HarvAllocation extends BaseEntity {

    public static final String SOURCE_NORMAL = "NORMAL";
    public static final String SOURCE_RETRANSMIT = "RETRANSMIT";
    public static final String SOURCE_BACKFILL = "BACKFILL";
    public static final String SOURCE_RETRY = "RETRY";

    public static final String RESULT_USABLE = "USABLE";
    public static final String RESULT_VOIDED = "VOIDED";

    private String allocationNo;
    private Long reservationId;
    private Long orderId;
    private String source;
    private String resultStatus;
    private Long usableKey;
    private String operator;
    private String remark;
    private String requestId;

    public String getAllocationNo() { return allocationNo; }
    public void setAllocationNo(String allocationNo) { this.allocationNo = allocationNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public Long getUsableKey() { return usableKey; }
    public void setUsableKey(Long usableKey) { this.usableKey = usableKey; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
