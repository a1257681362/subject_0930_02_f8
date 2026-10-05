package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.harv.dto.OrderCreateRequest;
import com.evops.aqua.harv.dto.OrderSplitRequest;
import com.evops.aqua.harv.dto.ReservationActionRequest;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvCustomerOrder;
import com.evops.aqua.harv.entity.HarvGradeRule;
import com.evops.aqua.harv.entity.HarvGradeVersion;
import com.evops.aqua.harv.entity.HarvReservation;
import com.evops.aqua.harv.mapper.HarvCustomerOrderMapper;
import com.evops.aqua.harv.mapper.HarvReservationMapper;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户订单与预约拆分：建单冻结当时生效的分级规则版本；
 * 按「规格 × 计划重量区间 × 交付时间」逐行拆分为预约；完成后订单永久保留当时分级规则。
 */
@Service
public class HarvOrderService {

    private final HarvCustomerOrderMapper orderMapper;
    private final HarvReservationMapper reservationMapper;
    private final HarvCustomerService customerService;
    private final HarvGradeService gradeService;
    private final HarvLocationService locationService;
    private final PondService pondService;
    private final HarvEvidenceService evidenceService;

    public HarvOrderService(HarvCustomerOrderMapper orderMapper,
                            HarvReservationMapper reservationMapper,
                            HarvCustomerService customerService,
                            HarvGradeService gradeService,
                            HarvLocationService locationService,
                            PondService pondService,
                            HarvEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.reservationMapper = reservationMapper;
        this.customerService = customerService;
        this.gradeService = gradeService;
        this.locationService = locationService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 建客户订单：校验客户与池塘，取苗种当前生效分级版本并冻结版本指针。requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvCustomerOrder create(OrderCreateRequest request) {
        HarvCustomerOrder duplicated = orderMapper.selectOne(new QueryWrapper<HarvCustomerOrder>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        customerService.getOrThrow(request.getCustomerId());
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        HarvGradeVersion version = gradeService.activeVersionOf(request.getSpecies());

        HarvCustomerOrder order = new HarvCustomerOrder();
        order.setOrderNo(nextOrderNo());
        order.setCustomerId(request.getCustomerId());
        order.setPondId(pond.getId());
        order.setSpecies(request.getSpecies());
        order.setGradeVersionId(version.getId());
        order.setGradeVersionNo(version.getVersionNo());
        order.setStatus(HarvCustomerOrder.STATUS_DRAFT);
        order.setOperator(request.getOperator());
        order.setRequestId(request.getRequestId());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);
        evidenceService.record(HarvChangeEvidence.ENTITY_ORDER, order.getId(),
                HarvChangeEvidence.ACTION_ORDER_CREATE, request.getRequestId(), request.getOperator(),
                null, order, "建立客户订单" + order.getOrderNo() + "，冻结分级版本" + version.getVersionNo());
        return order;
    }

    /**
     * 按「规格 × 计划重量区间 × 交付时间」拆分预约。
     * 整次拆分共享 requestId：重复提交（补传/重试）返回首次拆出的同一批预约，不产生第二批。
     * 规格必须命中订单冻结版本中的规则行，重量区间合法；拆分后订单转 CONFIRMED。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> split(Long orderId, OrderSplitRequest request) {
        HarvCustomerOrder order = getOrThrow(orderId);
        List<HarvReservation> existed = reservationsByRequestId(request.getRequestId());
        if (!existed.isEmpty()) {
            return splitResult(order, existed);
        }
        if (!HarvCustomerOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅草稿订单可拆分预约，当前状态：" + order.getStatus());
        }
        List<HarvGradeRule> frozenRules = gradeService.rulesOf(order.getGradeVersionId());

        HarvCustomerOrder before = copyOf(order);
        List<HarvReservation> reservations = new ArrayList<>();
        int seq = 1;
        for (OrderSplitRequest.SplitLineRequest line : request.getLines()) {
            HarvGradeRule rule = frozenRules.stream()
                    .filter(r -> r.getGradeCode().equals(line.getGradeCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("规格代码" + line.getGradeCode()
                            + "不在订单冻结版本" + order.getGradeVersionNo() + "的分级规则中"));
            if (line.getPlannedMinKg().compareTo(line.getPlannedMaxKg()) > 0) {
                throw new IllegalArgumentException("规格" + rule.getGradeCode()
                        + "计划重量下限不能大于上限：" + line.getPlannedMinKg() + "~" + line.getPlannedMaxKg());
            }
            HarvReservation reservation = new HarvReservation();
            reservation.setReservationNo("RS-" + order.getId() + "-" + seq);
            reservation.setOrderId(order.getId());
            reservation.setPondId(order.getPondId());
            reservation.setGradeCode(rule.getGradeCode());
            reservation.setGradeName(rule.getGradeName());
            reservation.setGradeVersionId(order.getGradeVersionId());
            reservation.setGradeVersionNo(order.getGradeVersionNo());
            reservation.setGradeMinWeightKg(rule.getMinWeightKg());
            reservation.setGradeMaxWeightKg(rule.getMaxWeightKg());
            reservation.setPlannedMinKg(line.getPlannedMinKg());
            reservation.setPlannedMaxKg(line.getPlannedMaxKg());
            reservation.setDeliveryTime(line.getDeliveryTime());
            reservation.setStatus(HarvReservation.STATUS_PLANNED);
            reservation.setRequestId(request.getRequestId());
            reservationMapper.insert(reservation);
            reservations.add(reservation);
            seq++;
        }
        order.setStatus(HarvCustomerOrder.STATUS_CONFIRMED);
        orderMapper.updateById(order);
        evidenceService.record(HarvChangeEvidence.ENTITY_ORDER, order.getId(),
                HarvChangeEvidence.ACTION_ORDER_SPLIT, request.getRequestId(), request.getOperator(),
                before, splitResult(order, reservations),
                "按规格/重量区间/交付时间拆分预约" + reservations.size() + "条");
        return splitResult(order, reservations);
    }

    /**
     * 完成订单：所有预约必须已现场分仓（ALLOCATED）。完成后订单冻结为 COMPLETED，
     * 永久保留建单时分级版本——后续发布新版本不改变其预约上的规则快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvCustomerOrder complete(Long orderId, ReservationActionRequest request) {
        HarvCustomerOrder order = getOrThrow(orderId);
        if (HarvCustomerOrder.STATUS_COMPLETED.equals(order.getStatus())
                || alreadyDone(HarvChangeEvidence.ACTION_ORDER_COMPLETE, orderId, request.getRequestId())) {
            return order;
        }
        if (!HarvCustomerOrder.STATUS_CONFIRMED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已确认（已拆分）订单可完成，当前状态：" + order.getStatus());
        }
        List<HarvReservation> reservations = reservationsOf(orderId);
        List<String> unfinished = new ArrayList<>();
        for (HarvReservation reservation : reservations) {
            if (!HarvReservation.STATUS_ALLOCATED.equals(reservation.getStatus())
                    && !HarvReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
                unfinished.add(reservation.getReservationNo() + "(" + reservation.getStatus() + ")");
            }
        }
        if (!unfinished.isEmpty()) {
            throw new IllegalArgumentException("存在尚未现场分仓的预约，订单不能完成："
                    + String.join("、", unfinished));
        }
        HarvCustomerOrder before = copyOf(order);
        for (HarvReservation reservation : reservations) {
            if (!HarvReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
                reservation.setStatus(HarvReservation.STATUS_COMPLETED);
                reservationMapper.updateById(reservation);
            }
        }
        order.setStatus(HarvCustomerOrder.STATUS_COMPLETED);
        order.setCompletedTime(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(HarvChangeEvidence.ENTITY_ORDER, order.getId(),
                HarvChangeEvidence.ACTION_ORDER_COMPLETE, request.getRequestId(), request.getOperator(),
                before, order, "订单完成，保留当时分级版本" + order.getGradeVersionNo());
        return order;
    }

    /**
     * 取消订单：仅草稿/已确认且无已分仓预约可取消；已锁定库位同步释放。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvCustomerOrder cancel(Long orderId, ReservationActionRequest request) {
        HarvCustomerOrder order = getOrThrow(orderId);
        if (HarvCustomerOrder.STATUS_CANCELLED.equals(order.getStatus())
                || alreadyDone(HarvChangeEvidence.ACTION_ORDER_CANCEL, orderId, request.getRequestId())) {
            return order;
        }
        boolean doneOrAlloc = HarvCustomerOrder.STATUS_COMPLETED.equals(order.getStatus());
        if (doneOrAlloc) {
            throw new IllegalArgumentException("已完成订单不可取消");
        }
        List<HarvReservation> reservations = reservationsOf(orderId);
        for (HarvReservation reservation : reservations) {
            if (HarvReservation.STATUS_ALLOCATED.equals(reservation.getStatus())
                    || HarvReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
                throw new IllegalArgumentException("预约" + reservation.getReservationNo()
                        + "已现场分仓，订单不可取消");
            }
        }
        HarvCustomerOrder before = copyOf(order);
        for (HarvReservation reservation : reservations) {
            if (reservation.getLocationId() != null
                    && HarvReservation.STATUS_LOCKED.equals(reservation.getStatus())) {
                locationService.releaseFrom(reservation, request.getOperator(), request.getRequestId());
            }
            if (!HarvReservation.STATUS_CANCELLED.equals(reservation.getStatus())) {
                reservation.setStatus(HarvReservation.STATUS_CANCELLED);
                reservationMapper.updateById(reservation);
            }
        }
        order.setStatus(HarvCustomerOrder.STATUS_CANCELLED);
        orderMapper.updateById(order);
        evidenceService.record(HarvChangeEvidence.ENTITY_ORDER, order.getId(),
                HarvChangeEvidence.ACTION_ORDER_CANCEL, request.getRequestId(), request.getOperator(),
                before, order, "取消客户订单" + order.getOrderNo());
        return order;
    }

    public HarvCustomerOrder getOrThrow(Long id) {
        HarvCustomerOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("客户订单不存在，id=" + id);
        }
        return order;
    }

    public List<HarvCustomerOrder> list(Long customerId, String status) {
        QueryWrapper<HarvCustomerOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(customerId != null, "customer_id", customerId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return orderMapper.selectList(wrapper);
    }

    public List<HarvReservation> reservationsOf(Long orderId) {
        return reservationMapper.selectList(new QueryWrapper<HarvReservation>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private List<HarvReservation> reservationsByRequestId(String requestId) {
        return reservationMapper.selectList(new QueryWrapper<HarvReservation>()
                .eq("request_id", requestId).orderByAsc("id"));
    }

    private Map<String, Object> splitResult(HarvCustomerOrder order, List<HarvReservation> reservations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("reservations", reservations);
        return result;
    }

    private boolean alreadyDone(String action, Long orderId, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        return evidenceService.list(HarvChangeEvidence.ENTITY_ORDER, orderId, requestId)
                .stream().anyMatch(e -> action.equals(e.getAction()));
    }

    private String nextOrderNo() {
        Long count = orderMapper.selectCount(new QueryWrapper<HarvCustomerOrder>());
        return "CO-" + (count + 1);
    }

    private HarvCustomerOrder copyOf(HarvCustomerOrder order) {
        HarvCustomerOrder copy = new HarvCustomerOrder();
        org.springframework.beans.BeanUtils.copyProperties(order, copy);
        return copy;
    }
}
