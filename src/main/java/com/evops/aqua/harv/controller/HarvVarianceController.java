package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.VarianceHandleRequest;
import com.evops.aqua.harv.entity.HarvVariance;
import com.evops.aqua.harv.service.HarvVarianceService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 差异单入口：短装/超装查询与处置登记。
 */
@RestController
@RequestMapping("/api/aqua/harv/variances")
public class HarvVarianceController {

    private final HarvVarianceService varianceService;

    public HarvVarianceController(HarvVarianceService varianceService) {
        this.varianceService = varianceService;
    }

    @GetMapping
    public ApiResponse<List<HarvVariance>> list(@RequestParam(required = false) Long reservationId,
                                                @RequestParam(required = false) Long orderId) {
        return ApiResponse.ok(varianceService.list(reservationId, orderId));
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<HarvVariance> handle(@PathVariable Long id,
                                            @Validated @RequestBody VarianceHandleRequest request) {
        return ApiResponse.ok(varianceService.handle(id, request));
    }
}
