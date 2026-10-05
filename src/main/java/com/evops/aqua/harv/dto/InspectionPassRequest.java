package com.evops.aqua.harv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 分仓行检验通过登记：解除该行待检；库位内全部行检验通过后库位待检标识同步解除。
 */
public class InspectionPassRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotNull(message = "分仓明细行不能为空")
    private Long lineId;

    @NotBlank(message = "检验人不能为空")
    private String inspectBy;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public String getInspectBy() { return inspectBy; }
    public void setInspectBy(String inspectBy) { this.inspectBy = inspectBy; }
}
