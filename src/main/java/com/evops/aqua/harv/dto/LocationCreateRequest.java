package com.evops.aqua.harv.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 捕捞库位登记请求：按海区登记仓/箱与重量容量，requestId 幂等。
 */
public class LocationCreateRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "库位编码不能为空")
    private String locationCode;

    @NotBlank(message = "所属海区不能为空")
    private String seaArea;

    @NotNull(message = "库位容量不能为空")
    @DecimalMin(value = "0.01", message = "库位容量必须大于0")
    private BigDecimal capacityKg;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
}
