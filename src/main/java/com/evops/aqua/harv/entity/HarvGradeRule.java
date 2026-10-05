package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 分级规则行：规格代码 + 单尾重量闭区间 [minWeightKg, maxWeightKg]（kg）。
 * 规则行随版本整体复制冻结到预约行；同一版本内规格代码唯一、重量区间不得重叠。
 */
@TableName("t_harv_grade_rule")
public class HarvGradeRule extends BaseEntity {

    private Long versionId;
    private String gradeCode;
    private String gradeName;
    private BigDecimal minWeightKg;
    private BigDecimal maxWeightKg;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public BigDecimal getMinWeightKg() { return minWeightKg; }
    public void setMinWeightKg(BigDecimal minWeightKg) { this.minWeightKg = minWeightKg; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(BigDecimal maxWeightKg) { this.maxWeightKg = maxWeightKg; }
}
