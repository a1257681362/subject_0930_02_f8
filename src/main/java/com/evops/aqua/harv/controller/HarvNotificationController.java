package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.NoticeAckRequest;
import com.evops.aqua.harv.entity.HarvNotification;
import com.evops.aqua.harv.service.HarvNotificationService;
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
 * 客户通知入口：按客户查询，客户签收留回执（重复签收不覆盖首次）。
 */
@RestController
@RequestMapping("/api/aqua/harv/notifications")
public class HarvNotificationController {

    private final HarvNotificationService notificationService;

    public HarvNotificationController(HarvNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<HarvNotification>> list(@RequestParam(required = false) Long customerId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(notificationService.list(customerId, status));
    }

    @PostMapping("/{id}/ack")
    public ApiResponse<HarvNotification> ack(@PathVariable Long id,
                                             @Validated @RequestBody NoticeAckRequest request) {
        return ApiResponse.ok(notificationService.ack(id, request));
    }
}
