package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.GradeVersionPublishRequest;
import com.evops.aqua.harv.entity.HarvGradeRule;
import com.evops.aqua.harv.entity.HarvGradeVersion;
import com.evops.aqua.harv.service.HarvGradeService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分级规则版本入口：发布（同苗种旧版本自动停用）、查询版本与规则行。
 */
@RestController
@RequestMapping("/api/aqua/harv/grade-versions")
public class HarvGradeVersionController {

    private final HarvGradeService gradeService;

    public HarvGradeVersionController(HarvGradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PostMapping
    public ApiResponse<HarvGradeVersion> publish(@Validated @RequestBody GradeVersionPublishRequest request) {
        return ApiResponse.ok(gradeService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<HarvGradeVersion>> list(@RequestParam(required = false) String species) {
        return ApiResponse.ok(gradeService.list(species));
    }

    /** 版本详情含规则行（冻结口径可查）。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id) {
        HarvGradeVersion version = gradeService.getOrThrow(id);
        List<HarvGradeRule> rules = gradeService.rulesOf(id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("version", version);
        view.put("rules", rules);
        return ApiResponse.ok(view);
    }
}
