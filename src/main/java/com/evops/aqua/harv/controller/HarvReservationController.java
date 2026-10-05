package com.evops.aqua.harv.controller;

import com.evops.aqua.harv.dto.AllocationRecordRequest;
import com.evops.aqua.harv.dto.InspectionPassRequest;
import com.evops.aqua.harv.dto.ReservationActionRequest;
import com.evops.aqua.harv.dto.ReservationLockRequest;
import com.evops.aqua.harv.dto.VehicleChangeRequest;
import com.evops.aqua.harv.entity.HarvAllocationLine;
import com.evops.aqua.harv.entity.HarvReservation;
import com.evops.aqua.harv.entity.HarvVehicleChange;
import com.evops.aqua.harv.service.HarvReservationService;
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
import java.util.Map;

/**
 * 捕捞预约入口：锁定/释放 → 现场分仓（补传/补录/重试只留一条可用结果）→ 检验 → 临时换车。
 */
@RestController
@RequestMapping("/api/aqua/harv/reservations")
public class HarvReservationController {

    private final HarvReservationService reservationService;

    public HarvReservationController(HarvReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ApiResponse<List<HarvReservation>> list(@RequestParam(required = false) Long orderId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long locationId) {
        return ApiResponse.ok(reservationService.list(orderId, status, locationId));
    }

    @GetMapping("/{id}")
    public ApiResponse<HarvReservation> view(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.getOrThrow(id));
    }

    /** 当前唯一可用分仓结果（含分仓行与差异单）。 */
    @GetMapping("/{id}/usable-allocation")
    public ApiResponse<Map<String, Object>> usableAllocation(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.usableAllocation(id));
    }

    @PostMapping("/{id}/lock")
    public ApiResponse<HarvReservation> lock(@PathVariable Long id,
                                             @Validated @RequestBody ReservationLockRequest request) {
        return ApiResponse.ok(reservationService.lock(id, request));
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<HarvReservation> unlock(@PathVariable Long id,
                                               @Validated @RequestBody ReservationActionRequest request) {
        return ApiResponse.ok(reservationService.unlock(id, request));
    }

    /**
     * 现场分仓回传：source=NORMAL/RETRANSMIT/BACKFILL/RETRY。
     * 补传、补录与重试只能形成一条可用结果。
     */
    @PostMapping("/{id}/allocations")
    public ApiResponse<Map<String, Object>> recordAllocation(
            @PathVariable Long id, @Validated @RequestBody AllocationRecordRequest request) {
        return ApiResponse.ok(reservationService.recordAllocation(id, request));
    }

    @PostMapping("/inspections/pass")
    public ApiResponse<HarvAllocationLine> passInspection(@Validated @RequestBody InspectionPassRequest request) {
        return ApiResponse.ok(reservationService.passInspection(request));
    }

    @PostMapping("/{id}/vehicle-change")
    public ApiResponse<HarvVehicleChange> changeVehicle(@PathVariable Long id,
                                                        @Validated @RequestBody VehicleChangeRequest request) {
        return ApiResponse.ok(reservationService.changeVehicle(id, request));
    }
}
