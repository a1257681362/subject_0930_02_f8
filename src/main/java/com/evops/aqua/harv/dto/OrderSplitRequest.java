package com.evops.aqua.harv.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单拆分预约请求：按「规格 × 计划重量区间 × 交付时间」逐行拆分，一行一预约。
 * 规格代码必须命中订单冻结的分级规则版本；重量下限不得大于上限。
 * 同一次拆分共享一个 requestId（整体幂等：重复提交不产生第二批预约）。
 */
public class OrderSplitRequest {

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotEmpty(message = "预约拆分行不能为空")
    @Valid
    private List<SplitLineRequest> lines;

    public static class SplitLineRequest {

        @NotBlank(message = "规格代码不能为空")
        private String gradeCode;

        @NotNull(message = "计划重量下限不能为空")
        @DecimalMin(value = "0.01", message = "计划重量下限必须大于0")
        private BigDecimal plannedMinKg;

        @NotNull(message = "计划重量上限不能为空")
        @DecimalMin(value = "0.01", message = "计划重量上限必须大于0")
        private BigDecimal plannedMaxKg;

        @NotNull(message = "交付时间不能为空")
        private LocalDateTime deliveryTime;

        public String getGradeCode() { return gradeCode; }
        public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
        public BigDecimal getPlannedMinKg() { return plannedMinKg; }
        public void setPlannedMinKg(BigDecimal plannedMinKg) { this.plannedMinKg = plannedMinKg; }
        public BigDecimal getPlannedMaxKg() { return plannedMaxKg; }
        public void setPlannedMaxKg(BigDecimal plannedMaxKg) { this.plannedMaxKg = plannedMaxKg; }
        public LocalDateTime getDeliveryTime() { return deliveryTime; }
        public void setDeliveryTime(LocalDateTime deliveryTime) { this.deliveryTime = deliveryTime; }
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public List<SplitLineRequest> getLines() { return lines; }
    public void setLines(List<SplitLineRequest> lines) { this.lines = lines; }
}
