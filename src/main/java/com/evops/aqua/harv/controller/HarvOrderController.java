package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.OrderCreateRequest;
import com.evops.aqua.harv.dto.OrderSplitRequest;
import com.evops.aqua.harv.dto.ReservationActionRequest;
import com.evops.aqua.harv.entity.HarvCustomerOrder;
import com.evops.aqua.harv.entity.HarvReservation;
import com.evops.aqua.harv.service.HarvOrderService;
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
 * 客户订单入口：建单（冻结分级版本）→ 按规格/重量区间/交付时间拆分预约 → 完成/取消；
 * 完成订单保留当时分级规则。
 */
@RestController
@RequestMapping("/api/aqua/harv/orders")
public class HarvOrderController {

    private final HarvOrderService orderService;

    public HarvOrderController(HarvOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<HarvCustomerOrder> create(@Validated @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<HarvCustomerOrder>> list(@RequestParam(required = false) Long customerId,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.list(customerId, status));
    }

    /** 订单详情含拆出的预约（预约上带冻结规则快照）。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id) {
        HarvCustomerOrder order = orderService.getOrThrow(id);
        List<HarvReservation> reservations = orderService.reservationsOf(id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("order", order);
        view.put("reservations", reservations);
        return ApiResponse.ok(view);
    }

    @PostMapping("/{id}/split")
    public ApiResponse<Map<String, Object>> split(@PathVariable Long id,
                                                  @Validated @RequestBody OrderSplitRequest request) {
        return ApiResponse.ok(orderService.split(id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<HarvCustomerOrder> complete(@PathVariable Long id,
                                                   @Validated @RequestBody ReservationActionRequest request) {
        return ApiResponse.ok(orderService.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<HarvCustomerOrder> cancel(@PathVariable Long id,
                                                 @Validated @RequestBody ReservationActionRequest request) {
        return ApiResponse.ok(orderService.cancel(id, request));
    }
}
