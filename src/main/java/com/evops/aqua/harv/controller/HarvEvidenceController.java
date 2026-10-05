package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.service.HarvEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 捕捞预约模块留证追溯：按对象（entityType+entityId）或幂等请求号 requestId 查询。
 */
@RestController
@RequestMapping("/api/aqua/harv")
public class HarvEvidenceController {

    private final HarvEvidenceService evidenceService;

    public HarvEvidenceController(HarvEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/evidence")
    public ApiResponse<List<HarvChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                          @RequestParam(required = false) Long entityId,
                                                          @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
