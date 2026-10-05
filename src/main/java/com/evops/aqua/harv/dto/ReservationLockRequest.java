package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 预约锁定请求：为预约独占绑定库位与运输车辆。
 * 库位必须 AVAILABLE，锁定后其他订单预约不得占用；requestId 幂等。
 */
public class ReservationLockRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotNull(message = "库位不能为空")
    private Long locationId;

    @NotBlank(message = "车牌号不能为空")
    private String vehicleNo;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
