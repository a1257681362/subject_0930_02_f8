package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.LocationCreateRequest;
import com.evops.aqua.harv.entity.HarvLocation;
import com.evops.aqua.harv.service.HarvLocationService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 捕捞库位入口：登记、按海区/状态查询（库位状态与待检标识随锁定/分仓/检验同步）。
 */
@RestController
@RequestMapping("/api/aqua/harv/locations")
public class HarvLocationController {

    private final HarvLocationService locationService;

    public HarvLocationController(HarvLocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ApiResponse<HarvLocation> register(@Validated @RequestBody LocationCreateRequest request) {
        return ApiResponse.ok(locationService.register(request));
    }

    @GetMapping
    public ApiResponse<List<HarvLocation>> list(@RequestParam(required = false) String seaArea,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(locationService.list(seaArea, status));
    }
}
