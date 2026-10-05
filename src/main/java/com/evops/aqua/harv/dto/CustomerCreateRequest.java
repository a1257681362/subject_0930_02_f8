package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;

/**
 * 客户档案登记请求，requestId 幂等。
 */
public class CustomerCreateRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "客户编码不能为空")
    private String customerCode;

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    private String contactPhone;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
