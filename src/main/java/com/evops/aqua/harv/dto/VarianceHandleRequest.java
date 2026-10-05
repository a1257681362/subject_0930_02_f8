package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;

/**
 * 差异单处置登记：记录处置意见与处置人，不改变短装/超装事实；requestId 幂等。
 */
public class VarianceHandleRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "处置人不能为空")
    private String handleBy;

    @NotBlank(message = "处置意见不能为空")
    private String handleRemark;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getHandleBy() { return handleBy; }
    public void setHandleBy(String handleBy) { this.handleBy = handleBy; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
}
