package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;

/**
 * 预约动作请求：释放锁定/完成/取消等状态动作的操作者与幂等号。
 */
public class ReservationActionRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
