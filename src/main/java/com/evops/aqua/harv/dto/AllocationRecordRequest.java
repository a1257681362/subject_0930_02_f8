package com.evops.aqua.harv.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 现场分仓回传登记：按实际规格逐行回传实际重量与落位库位。
 * source 标注来源 NORMAL 正常 / RETRANSMIT 补传 / BACKFILL 补录 / RETRY 重试（缺省 NORMAL）；
 * 无论何种来源，同一预约只能形成一条 USABLE 可用结果：
 * 同 requestId 重试原样返回；新 requestId 的补传/补录会作废旧结果并重建，绝不残留第二条可用结果。
 */
public class AllocationRecordRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    /** NORMAL/RETRANSMIT/BACKFILL/RETRY，为空按 NORMAL。 */
    private String source;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    private String remark;

    @NotEmpty(message = "分仓明细行不能为空")
    @Valid
    private List<AllocationLineRequest> lines;

    public static class AllocationLineRequest {

        @NotBlank(message = "实际规格代码不能为空")
        private String actualGradeCode;

        @NotNull(message = "实际重量不能为空")
        @DecimalMin(value = "0.01", message = "实际重量必须大于0")
        private BigDecimal actualWeightKg;

        @NotNull(message = "落位库位不能为空")
        private Long locationId;

        public String getActualGradeCode() { return actualGradeCode; }
        public void setActualGradeCode(String actualGradeCode) { this.actualGradeCode = actualGradeCode; }
        public BigDecimal getActualWeightKg() { return actualWeightKg; }
        public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
        public Long getLocationId() { return locationId; }
        public void setLocationId(Long locationId) { this.locationId = locationId; }
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<AllocationLineRequest> getLines() { return lines; }
    public void setLines(List<AllocationLineRequest> lines) { this.lines = lines; }
}
