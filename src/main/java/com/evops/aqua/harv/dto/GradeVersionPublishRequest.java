package com.evops.aqua.harv.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 分级规则版本发布请求：按苗种发布整套规格分级（规格代码 + 单尾重量闭区间）。
 * 同苗种发布新版本后旧版本自动停用；已冻结到订单/预约上的快照不受影响。
 */
public class GradeVersionPublishRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "苗种不能为空")
    private String species;

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;

    private String remark;

    @NotEmpty(message = "分级规则行不能为空")
    @Valid
    private List<GradeRuleRequest> rules;

    public static class GradeRuleRequest {

        @NotBlank(message = "规格代码不能为空")
        private String gradeCode;

        @NotBlank(message = "规格名称不能为空")
        private String gradeName;

        @NotNull(message = "规格重量下限不能为空")
        @DecimalMin(value = "0", message = "规格重量下限不能为负")
        private BigDecimal minWeightKg;

        @NotNull(message = "规格重量上限不能为空")
        @DecimalMin(value = "0.01", message = "规格重量上限必须大于0")
        private BigDecimal maxWeightKg;

        public String getGradeCode() { return gradeCode; }
        public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
        public String getGradeName() { return gradeName; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }
        public BigDecimal getMinWeightKg() { return minWeightKg; }
        public void setMinWeightKg(BigDecimal minWeightKg) { this.minWeightKg = minWeightKg; }
        public BigDecimal getMaxWeightKg() { return maxWeightKg; }
        public void setMaxWeightKg(BigDecimal maxWeightKg) { this.maxWeightKg = maxWeightKg; }
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<GradeRuleRequest> getRules() { return rules; }
    public void setRules(List<GradeRuleRequest> rules) { this.rules = rules; }
}
