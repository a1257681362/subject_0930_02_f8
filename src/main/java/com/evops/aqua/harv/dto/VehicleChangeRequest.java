package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;

/**
 * 临时换车请求：仅锁定后预约可换车，原因必填，登记后触发客户通知；requestId 幂等。
 */
public class VehicleChangeRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "新车牌号不能为空")
    private String toVehicleNo;

    @NotBlank(message = "换车原因不能为空")
    private String reason;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getToVehicleNo() { return toVehicleNo; }
    public void setToVehicleNo(String toVehicleNo) { this.toVehicleNo = toVehicleNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
