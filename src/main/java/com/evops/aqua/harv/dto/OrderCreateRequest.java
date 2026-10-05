package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 客户订单创建请求：建单即冻结该苗种当前生效的分级规则版本。
 */
public class OrderCreateRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    @NotBlank(message = "苗种不能为空")
    private String species;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    private String remark;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
