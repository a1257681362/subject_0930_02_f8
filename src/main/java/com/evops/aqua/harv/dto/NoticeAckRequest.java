package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;

/**
 * 客户通知签收：登记签收人/回执备注；重复签收幂等，不覆盖首次回执。
 */
public class NoticeAckRequest {

    @NotBlank(message = "签收人不能为空")
    private String ackBy;

    @NotBlank(message = "回执备注不能为空")
    private String ackRemark;

    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
}
