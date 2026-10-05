package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.CustomerCreateRequest;
import com.evops.aqua.harv.entity.HarvCustomer;
import com.evops.aqua.harv.service.HarvCustomerService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户档案入口。
 */
@RestController
@RequestMapping("/api/aqua/harv/customers")
public class HarvCustomerController {

    private final HarvCustomerService customerService;

    public HarvCustomerController(HarvCustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ApiResponse<HarvCustomer> create(@Validated @RequestBody CustomerCreateRequest request) {
        return ApiResponse.ok(customerService.create(request));
    }

    @GetMapping
    public ApiResponse<List<HarvCustomer>> list() {
        return ApiResponse.ok(customerService.list());
    }
}
