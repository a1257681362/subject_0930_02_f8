package com.evops.aqua.harv;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.harv.entity.HarvAllocation;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvLocation;
import com.evops.aqua.harv.entity.HarvNotification;
import com.evops.aqua.harv.mapper.HarvAllocationLineMapper;
import com.evops.aqua.harv.mapper.HarvAllocationMapper;
import com.evops.aqua.harv.mapper.HarvChangeEvidenceMapper;
import com.evops.aqua.harv.mapper.HarvGradeVersionMapper;
import com.evops.aqua.harv.mapper.HarvLocationMapper;
import com.evops.aqua.harv.mapper.HarvNotificationMapper;
import com.evops.aqua.harv.mapper.HarvReservationMapper;
import com.evops.aqua.harv.mapper.HarvVarianceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 捕捞预约与规格分仓验收（aqua-8）。
 * 主线：分级版本 → 客户订单冻结版本 → 按规格/重量区间/交付时间拆分预约 → 锁定库位车辆互斥
 * → 临时换车留因并通知客户 → 现场按实际规格分仓（库位状态+待检同步）→ 检验同步解除待检
 * → 短装/超装差异单与客户通知 → 补传/补录/重试只留一条可用结果 → 新版本不影响历史订单
 * → 完成订单保留当时规则 → 释放/取消与全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HarvestReservationAcceptanceTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HarvAllocationMapper allocationMapper;
    @Autowired private HarvAllocationLineMapper allocationLineMapper;
    @Autowired private HarvChangeEvidenceMapper evidenceMapper;
    @Autowired private HarvGradeVersionMapper gradeVersionMapper;
    @Autowired private HarvLocationMapper locationMapper;
    @Autowired private HarvNotificationMapper notificationMapper;
    @Autowired private HarvReservationMapper reservationMapper;
    @Autowired private HarvVarianceMapper varianceMapper;

    private String sessionCookie;
    private final List<String> report = new ArrayList<>();

    private long pondId;
    private long customerId;
    private long gv1;
    private long loc1, loc2, loc3, loc4, loc5;
    private long order1, order2, order3;
    private long rsA, rsB, rsC, rsD;
    private long lineA1, lineA2;
    private long varianceB;
    private long allocC1, allocC2;

    @Test
    void acceptanceHarvestReservationAndGrading() {
        checkpoint("CP0", "登录认证：bootstrap 登录成功并下发会话Cookie", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "基础档案：建池、客户、分级规则版本（两档规格）、五个可用库位", () -> {
            JsonNode pond = json(call(HttpMethod.POST, "/api/aqua/ponds", pondBody()));
            assertSuccess(pond, "建养殖池");
            pondId = pond.path("data").path("id").asLong();

            JsonNode customer = json(call(HttpMethod.POST, "/api/aqua/harv/customers",
                    mapOf("requestId", "hv-cust-1", "customerCode", "HV-C01",
                            "customerName", "海丰客户", "contactPhone", "13800000000")));
            assertSuccess(customer, "登记客户");
            customerId = customer.path("data").path("id").asLong();

            JsonNode version = json(call(HttpMethod.POST, "/api/aqua/harv/grade-versions",
                    gradePublishBody("hv-gv-1", rule("G1", "小规格", "0", "0.50"),
                            rule("G2", "大规格", "0.51", "1.00"))));
            assertSuccess(version, "发布分级版本V1");
            gv1 = version.path("data").path("id").asLong();
            check("GV-石斑鱼-1".equals(version.path("data").path("versionNo").asText()),
                    "分级版本号异常：" + version.path("data").path("versionNo").asText());

            loc1 = createLocation("hv-loc-1", "HV-L1", "200");
            loc2 = createLocation("hv-loc-2", "HV-L2", "200");
            loc3 = createLocation("hv-loc-3", "HV-L3", "200");
            loc4 = createLocation("hv-loc-4", "HV-L4", "200");
            loc5 = createLocation("hv-loc-5", "HV-L5", "200");
            check("AVAILABLE".equals(location(loc1).getStatus())
                            && !Boolean.TRUE.equals(location(loc1).getPendingInspect()),
                    "新库位应为可用且非待检");
        });

        checkpoint("CP2", "建客户订单即冻结当前分级版本；苗种无生效版本时拒绝建单", () -> {
            JsonNode noVersion = json(call(HttpMethod.POST, "/api/aqua/harv/orders",
                    orderBody("hv-order-x", "不存在鱼种")));
            check(!noVersion.path("success").asBoolean(true)
                            && noVersion.path("message").asText().contains("分级规则版本"),
                    "无生效分级版本的苗种应拒绝建单：" + noVersion.path("message").asText());

            JsonNode order = json(call(HttpMethod.POST, "/api/aqua/harv/orders",
                    orderBody("hv-order-1", "石斑鱼")));
            assertSuccess(order, "建立客户订单1");
            order1 = order.path("data").path("id").asLong();
            check(order.path("data").path("gradeVersionId").asLong() == gv1,
                    "订单应冻结建单时生效的分级版本id=" + gv1);
            check("DRAFT".equals(order.path("data").path("status").asText()), "新订单应为草稿态");
        });

        checkpoint("CP3", "按规格/重量区间/交付时间拆分预约并冻结规则快照；非法规格拒绝，订单转已确认", () -> {
            JsonNode bad = json(call(HttpMethod.POST, "/api/aqua/harv/orders/" + order1 + "/split",
                    splitBody("hv-split-bad",
                            splitLine("G9", "90", "110"), splitLine("G2", "40", "60"))));
            check(!bad.path("success").asBoolean(true) && bad.path("message").asText().contains("分级规则"),
                    "规格不在冻结版本中应拒绝拆分：" + bad.path("message").asText());

            JsonNode split = json(call(HttpMethod.POST, "/api/aqua/harv/orders/" + order1 + "/split",
                    splitBody("hv-split-1",
                            splitLine("G1", "90", "110"), splitLine("G2", "40", "60"))));
            assertSuccess(split, "拆分预约");
            JsonNode reservations = split.path("data").path("reservations");
            check(reservations.size() == 2, "应拆出2条预约，实际：" + reservations.size());
            rsA = reservations.get(0).path("id").asLong();
            rsB = reservations.get(1).path("id").asLong();
            check(("RS-" + order1 + "-1").equals(reservations.get(0).path("reservationNo").asText()),
                    "预约编号应按订单内序号生成");
            check("PLANNED".equals(reservations.get(0).path("status").asText()), "新预约应为待锁定");
            check(reservations.get(0).path("gradeVersionId").asLong() == gv1,
                    "预约行应冻结分级版本id，期望" + gv1 + "，实际"
                            + reservations.get(0).path("gradeVersionId").asLong());
            check(new BigDecimal(reservations.get(0).path("gradeMaxWeightKg").asText())
                            .compareTo(new BigDecimal("0.50")) == 0,
                    "预约行应冻结单尾重量区间快照，期望0.50，实际"
                            + reservations.get(0).path("gradeMaxWeightKg").asText());
            check("CONFIRMED".equals(split.path("data").path("order").path("status").asText()),
                    "拆分后订单应转已确认");
        });

        checkpoint("CP4", "拆分幂等：同 requestId 补传/重试返回同一批预约，不产生第二批", () -> {
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/harv/orders/" + order1 + "/split",
                    splitBody("hv-split-1",
                            splitLine("G1", "90", "110"), splitLine("G2", "40", "60"))));
            assertSuccess(again, "重复拆分");
            check(again.path("data").path("reservations").size() == 2
                            && again.path("data").path("reservations").get(0).path("id").asLong() == rsA,
                    "同requestId重复拆分必须返回首次的同一批预约");
            long count = reservationMapper.selectCount(new QueryWrapper<com.evops.aqua.harv.entity.HarvReservation>()
                    .eq("order_id", order1));
            check(count == 2, "订单1下只能有2条预约，实际：" + count);
        });

        checkpoint("CP5", "锁定库位车辆互斥：锁定后他单占用拒绝；重复锁定幂等不多占", () -> {
            JsonNode order2Node = json(call(HttpMethod.POST, "/api/aqua/harv/orders",
                    orderBody("hv-order-2", "石斑鱼")));
            assertSuccess(order2Node, "建立客户订单2");
            order2 = order2Node.path("data").path("id").asLong();
            JsonNode split2 = json(call(HttpMethod.POST, "/api/aqua/harv/orders/" + order2 + "/split",
                    splitBody("hv-split-2", splitLine("G1", "50", "60"))));
            rsC = split2.path("data").path("reservations").get(0).path("id").asLong();

            JsonNode lockA = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsA + "/lock",
                    lockBody("hv-lock-a", loc1, "粤B0001")));
            assertSuccess(lockA, "预约A锁定库位L1");
            check("LOCKED".equals(lockA.path("data").path("status").asText())
                            && lockA.path("data").path("locationId").asLong() == loc1,
                    "预约A应为锁定态并绑定L1");
            check("LOCKED".equals(location(loc1).getStatus())
                            && location(loc1).getReservationId() != null
                            && location(loc1).getReservationId().equals(rsA),
                    "L1应锁定且互斥指向预约A");

            JsonNode conflict = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/lock",
                    lockBody("hv-lock-c-conflict", loc1, "粤C0003")));
            check(!conflict.path("success").asBoolean(true)
                            && conflict.path("message").asText().contains("其他订单"),
                    "已被预约A锁定的L1不得被订单2占用：" + conflict.path("message").asText());

            JsonNode lockC = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/lock",
                    lockBody("hv-lock-c", loc2, "粤C0003")));
            assertSuccess(lockC, "预约C改锁库位L2");

            JsonNode lockAAgain = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsA + "/lock",
                    lockBody("hv-lock-a", loc1, "粤B0001")));
            assertSuccess(lockAAgain, "同requestId重复锁定应幂等");
            check(lockAAgain.path("data").path("locationId").asLong() == loc1,
                    "幂等锁定不得改变库位绑定");
            check(evidenceCount(HarvChangeEvidence.ENTITY_RESERVATION, rsA,
                    HarvChangeEvidence.ACTION_RESERVATION_LOCK) == 1,
                    "重复锁定不得多写锁定证据");
        });

        checkpoint("CP6", "临时换车必须登记原因并触发客户通知；客户签收留回执，重复签收不覆盖首次", () -> {
            JsonNode change = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsA + "/vehicle-change",
                    vehicleBody("hv-vc-a", "粤B9999", "原车故障临时调车")));
            assertSuccess(change, "预约A临时换车");
            check("粤B9999".equals(change.path("data").path("toVehicleNo").asText())
                            && "粤B0001".equals(change.path("data").path("fromVehicleNo").asText())
                            && change.path("data").path("reason").asText().contains("故障"),
                    "换车记录应保留原车/新车/原因");
            check("粤B9999".equals(getReservation(rsA).getVehicleNo()), "预约车辆应更新为新车");

            JsonNode noReason = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/vehicle-change",
                    vehicleBody("hv-vc-c-bad", "粤C8888", "  ")));
            check(!noReason.path("success").asBoolean(true), "换车不填原因应被拒绝");

            List<HarvNotification> notices = notificationMapper.selectList(
                    new QueryWrapper<HarvNotification>().eq("customer_id", customerId)
                            .eq("event_type", HarvNotification.EVENT_VEHICLE_CHANGE));
            check(notices.size() == 1 && notices.get(0).getMessage().contains("粤B9999")
                            && notices.get(0).getMessage().contains("原车故障"),
                    "换车应恰好触发一条含原因与新车牌的客户通知");
            long noticeId = notices.get(0).getId();

            JsonNode ack = json(call(HttpMethod.POST,
                    "/api/aqua/harv/notifications/" + noticeId + "/ack",
                    mapOf("ackBy", "客户采购员李", "ackRemark", "已知悉换车")));
            assertSuccess(ack, "客户签收");
            check("ACKED".equals(ack.path("data").path("status").asText())
                            && "客户采购员李".equals(ack.path("data").path("ackBy").asText()),
                    "签收应留回执");
            JsonNode ackAgain = json(call(HttpMethod.POST,
                    "/api/aqua/harv/notifications/" + noticeId + "/ack",
                    mapOf("ackBy", "冒名顶替者", "ackRemark", "第二次签收")));
            assertSuccess(ackAgain, "重复签收应幂等");
            check("客户采购员李".equals(ackAgain.path("data").path("ackBy").asText())
                            && "已知悉换车".equals(ackAgain.path("data").path("ackRemark").asText()),
                    "重复签收不得覆盖首次回执");
        });

        checkpoint("CP7", "未锁定预约现场分仓被拒绝，失败原因独立事务留证不随回滚丢失", () -> {
            JsonNode denied = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsB + "/allocations",
                    allocationBody("hv-alloc-b-fail", "NORMAL",
                            allocLine("G2", "30", loc4))));
            check(!denied.path("success").asBoolean(true)
                            && denied.path("message").asText().contains("锁定"),
                    "待锁定（PLANNED）预约不得分仓：" + denied.path("message").asText());
            check(evidenceCountByRequest(HarvChangeEvidence.ENTITY_RESERVATION, rsB,
                    HarvChangeEvidence.ACTION_ALLOCATION_RECORD, "hv-alloc-b-fail") == 1,
                    "分仓被拒也必须留一条带操作者的失败证据");
            check(allocationMapper.selectCount(new QueryWrapper<HarvAllocation>()
                    .eq("reservation_id", rsB)) == 0, "被拒分仓不得残留登记");
        });

        checkpoint("CP8", "现场按实际规格分仓：含跨规格实际收货，库位置占用并打待检，区间内不出差异单", () -> {
            // 预约A计划G1 90~110kg：实际收到 G1 95kg 落 L1、G2 5kg 落 L3（现场实际分级与预约规格不同）。
            JsonNode alloc = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsA + "/allocations",
                    allocationBody("hv-alloc-a", "NORMAL",
                            allocLine("G1", "95", loc1), allocLine("G2", "5", loc3))));
            assertSuccess(alloc, "预约A现场分仓");
            JsonNode lines = alloc.path("data").path("lines");
            check(lines.size() == 2, "应按实际规格分2行，实际：" + lines.size());
            lineA1 = lines.get(0).path("id").asLong();
            lineA2 = lines.get(1).path("id").asLong();
            check("USABLE".equals(alloc.path("data").path("allocation").path("resultStatus").asText()),
                    "首次分仓应为可用结果USABLE");
            check(bd(getReservation(rsA).getActualTotalKg()).compareTo(new BigDecimal("100")) == 0,
                    "预约A实际总量应为100kg");
            check("OCCUPIED".equals(location(loc1).getStatus())
                            && Boolean.TRUE.equals(location(loc1).getPendingInspect())
                            && "OCCUPIED".equals(location(loc3).getStatus())
                            && Boolean.TRUE.equals(location(loc3).getPendingInspect()),
                    "落位库位应同步为占用且待检");
            check(alloc.path("data").path("variances").size() == 0,
                    "实际100kg落在90~110区间内不应产生差异单");
        });

        checkpoint("CP9", "检验逐行通过：行待检解除，库位待检标识随行状态同步，全部通过才解除", () -> {
            JsonNode pass1 = json(call(HttpMethod.POST, "/api/aqua/harv/reservations/inspections/pass",
                    mapOf("requestId", "hv-insp-a1", "lineId", lineA1, "inspectBy", "检验员周")));
            assertSuccess(pass1, "L1分仓行检验通过");
            check(pass1.path("data").path("inspectPassed").asBoolean()
                            && !pass1.path("data").path("pendingInspect").asBoolean(true),
                    "通过行应解除待检");
            check(!Boolean.TRUE.equals(location(loc1).getPendingInspect()),
                    "库位L1唯一行通过后待检标识应同步解除");
            check(Boolean.TRUE.equals(location(loc3).getPendingInspect()),
                    "库位L3尚有未通过行，应保持待检");

            JsonNode pass1Again = json(call(HttpMethod.POST, "/api/aqua/harv/reservations/inspections/pass",
                    mapOf("requestId", "hv-insp-a1", "lineId", lineA1, "inspectBy", "检验员周")));
            assertSuccess(pass1Again, "重复检验登记幂等");

            JsonNode pass2 = json(call(HttpMethod.POST, "/api/aqua/harv/reservations/inspections/pass",
                    mapOf("requestId", "hv-insp-a2", "lineId", lineA2, "inspectBy", "检验员周")));
            assertSuccess(pass2, "L3分仓行检验通过");
            check(!Boolean.TRUE.equals(location(loc3).getPendingInspect()),
                    "库位L3全部分仓行通过后待检标识应解除");
        });

        checkpoint("CP10", "短装生成SHORT差异单并通知客户，差异数量准确且可处置", () -> {
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsB + "/lock",
                    lockBody("hv-lock-b", loc4, "粤B0002"))), "预约B锁定L4");
            // 预约B计划40~60kg，实际仅30kg → 短装10kg。
            JsonNode alloc = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsB + "/allocations",
                    allocationBody("hv-alloc-b", "NORMAL", allocLine("G2", "30", loc4))));
            assertSuccess(alloc, "预约B现场分仓30kg");
            JsonNode variances = alloc.path("data").path("variances");
            check(variances.size() == 1 && "SHORT".equals(variances.get(0).path("varianceType").asText()),
                    "应恰好生成一张短装差异单");
            check(new BigDecimal(variances.get(0).path("diffKg").asText()).compareTo(new BigDecimal("10")) == 0
                            && new BigDecimal(variances.get(0).path("actualKg").asText())
                            .compareTo(new BigDecimal("30")) == 0,
                    "短装差异应为缺口10kg");
            varianceB = variances.get(0).path("id").asLong();

            List<HarvNotification> varianceNotices = notificationMapper.selectList(
                    new QueryWrapper<HarvNotification>().eq("reservation_id", rsB)
                            .eq("event_type", HarvNotification.EVENT_VARIANCE));
            check(varianceNotices.size() == 1 && varianceNotices.get(0).getMessage().contains("短装")
                            && varianceNotices.get(0).getMessage().contains("10"),
                    "短装应触发客户通知且内容含短装缺口");

            JsonNode handled = json(call(HttpMethod.POST,
                    "/api/aqua/harv/variances/" + varianceB + "/handle",
                    mapOf("requestId", "hv-var-b-handle", "handleBy", "调度员吴",
                            "handleRemark", "下次捕捞补足10kg")));
            assertSuccess(handled, "差异单处置");
            check(handled.path("data").path("handled").asBoolean()
                            && "下次捕捞补足10kg".equals(handled.path("data").path("handleRemark").asText()),
                    "处置应登记意见；短装事实仍保留");
            check("SHORT".equals(variances.get(0).path("varianceType").asText()), "处置不改变短装类型");
        });

        checkpoint("CP11", "超装生成OVER差异单；同requestId重试只返回同一可用结果与同一张差异单", () -> {
            // 预约C计划50~60kg，实际70kg落锁定的L2 → 超装10kg。
            JsonNode alloc = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/allocations",
                    allocationBody("hv-alloc-c", "NORMAL", allocLine("G1", "70", loc2))));
            assertSuccess(alloc, "预约C现场分仓70kg");
            allocC1 = alloc.path("data").path("allocation").path("id").asLong();
            check("OVER".equals(alloc.path("data").path("variances").get(0).path("varianceType").asText())
                            && new BigDecimal(alloc.path("data").path("variances").get(0)
                            .path("diffKg").asText()).compareTo(new BigDecimal("10")) == 0,
                    "应生成超装差异单，超出10kg");

            JsonNode retry = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/allocations",
                    allocationBody("hv-alloc-c", "RETRY", allocLine("G1", "70", loc2))));
            assertSuccess(retry, "同requestId重试（网络抖动重发）");
            check(retry.path("data").path("allocation").path("id").asLong() == allocC1,
                    "同requestId重试必须返回首次结果，不得新建");
            check(countUsable(rsC) == 1, "预约C只能有一条USABLE结果");
            check(varianceMapper.selectCount(new QueryWrapper<com.evops.aqua.harv.entity.HarvVariance>()
                    .eq("reservation_id", rsC)) == 1, "重试不得多生成差异单");
        });

        checkpoint("CP12", "补传与补录：各作废旧结果重建，任意时刻只有一条USABLE，旧结果VOIDED只追加留痕", () -> {
            // 补传：实际55kg（落在50~60区间）→ 旧超装结果作废、旧差异单随之作废。
            JsonNode retransmit = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/allocations",
                    allocationBody("hv-alloc-c-rt", "RETRANSMIT", allocLine("G1", "55", loc2))));
            assertSuccess(retransmit, "补传分仓结果");
            allocC2 = retransmit.path("data").path("allocation").path("id").asLong();
            check(countUsable(rsC) == 1, "补传后仍只能有一条USABLE结果");
            check(countByStatus(rsC, HarvAllocation.RESULT_VOIDED) == 1, "旧结果应置VOIDED保留一条");
            check(retransmit.path("data").path("variances").size() == 0,
                    "补传55kg落在区间内，旧超装差异单应随旧结果作废且不产生新差异单");
            check("OCCUPIED".equals(location(loc2).getStatus())
                            && Boolean.TRUE.equals(location(loc2).getPendingInspect()),
                    "补传重新落位后L2应为占用且重新待检");
            check(bd(getReservation(rsC).getActualTotalKg()).compareTo(new BigDecimal("55")) == 0,
                    "预约C实际总量应更新为补传值55kg");

            // 补录：新requestId再来一次58kg → 上一条补传结果也作废。
            JsonNode backfill = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/allocations",
                    allocationBody("hv-alloc-c-bf", "BACKFILL", allocLine("G1", "58", loc2))));
            assertSuccess(backfill, "补录分仓结果");
            check(countUsable(rsC) == 1, "补录后仍只能有一条USABLE结果");
            check(countByStatus(rsC, HarvAllocation.RESULT_VOIDED) == 2,
                    "两次替换应累计两条VOIDED历史结果");
            long allocC3 = backfill.path("data").path("allocation").path("id").asLong();
            check(allocC3 != allocC2 && backfill.path("data").path("lines").size() == 1
                            && new BigDecimal(backfill.path("data").path("lines").get(0)
                            .path("actualWeightKg").asText()).compareTo(new BigDecimal("58")) == 0,
                    "补录形成新的唯一可用结果，旧明细已随作废清除");
            check(allocationLineMapper.selectCount(new QueryWrapper<com.evops.aqua.harv.entity.HarvAllocationLine>()
                    .eq("allocation_id", allocC1)) == 0
                            && allocationLineMapper.selectCount(new QueryWrapper<com.evops.aqua.harv.entity.HarvAllocationLine>()
                            .eq("allocation_id", allocC2)) == 0,
                    "两次作废结果的明细行都应清除，不参与在仓重量");
            check(evidenceCount(HarvChangeEvidence.ENTITY_ALLOCATION, allocC1,
                    HarvChangeEvidence.ACTION_ALLOCATION_VOID) == 1
                            && evidenceCount(HarvChangeEvidence.ENTITY_ALLOCATION, allocC2,
                            HarvChangeEvidence.ACTION_ALLOCATION_VOID) == 1,
                    "两次作废都应留痕");
        });

        checkpoint("CP13", "发布新分级版本：旧版本停用，在途/已完成订单预约永久保留当时版本与规则快照", () -> {
            JsonNode gv2Node = json(call(HttpMethod.POST, "/api/aqua/harv/grade-versions",
                    gradePublishBody("hv-gv-2", rule("G1", "小规格新", "0", "0.40"),
                            rule("G2", "大规格新", "0.41", "0.80"))));
            assertSuccess(gv2Node, "发布分级版本V2");
            check("INACTIVE".equals(gradeVersionMapper.selectById(gv1).getStatus()),
                    "新版本发布后同苗种旧版本应自动停用");

            com.evops.aqua.harv.entity.HarvReservation snapshotC = getReservation(rsC);
            check(snapshotC.getGradeVersionId() == gv1
                            && "GV-石斑鱼-1".equals(snapshotC.getGradeVersionNo())
                            && bd(snapshotC.getGradeMaxWeightKg()).compareTo(new BigDecimal("0.50")) == 0,
                    "在途预约C必须保留建单时V1版本与重量区间快照，不受V2影响");
            com.evops.aqua.harv.entity.HarvReservation snapshotA = getReservation(rsA);
            check(snapshotA.getGradeVersionId() == gv1, "已分仓预约A同样保留V1快照");

            // 用V2里不存在的口径与V1口径各试一次实际分级：按预约冻结的V1校验。
            JsonNode badGrade = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsC + "/allocations",
                    allocationBody("hv-alloc-c-badgrade", "RETRY", allocLine("G3", "10", loc2))));
            check(!badGrade.path("success").asBoolean(true)
                            && badGrade.path("message").asText().contains("分级版本"),
                    "实际规格不在预约保留版本内应拒绝：" + badGrade.path("message").asText());
            check(countUsable(rsC) == 1, "失败的回传不得改变唯一可用结果");
        });

        checkpoint("CP14", "完成订单保留当时分级规则且预约转完成；已完成订单不可取消", () -> {
            JsonNode complete = json(call(HttpMethod.POST,
                    "/api/aqua/harv/orders/" + order1 + "/complete",
                    actionBody("hv-complete-1")));
            assertSuccess(complete, "完成订单1");
            check("COMPLETED".equals(complete.path("data").path("status").asText())
                            && complete.path("data").path("gradeVersionId").asLong() == gv1
                            && "GV-石斑鱼-1".equals(complete.path("data").path("gradeVersionNo").asText()),
                    "完成订单必须保留当时分级版本V1");
            check("COMPLETED".equals(getReservation(rsA).getStatus())
                            && "COMPLETED".equals(getReservation(rsB).getStatus()),
                    "完成订单下预约应同步完成");

            JsonNode completeAgain = json(call(HttpMethod.POST,
                    "/api/aqua/harv/orders/" + order1 + "/complete",
                    actionBody("hv-complete-1")));
            assertSuccess(completeAgain, "重复完成幂等");
            JsonNode cancelDone = json(call(HttpMethod.POST,
                    "/api/aqua/harv/orders/" + order1 + "/cancel",
                    actionBody("hv-cancel-done")));
            check(!cancelDone.path("success").asBoolean(true)
                            && cancelDone.path("message").asText().contains("已完成"),
                    "已完成订单不可取消：" + cancelDone.path("message").asText());
        });

        checkpoint("CP15", "释放锁定/取消订单同步库位互斥；全程留证可按对象与requestId追溯且操作者不丢", () -> {
            // 新订单在V2生效后建立，应冻结V2；拆一条预约走锁定-释放-取消。
            JsonNode order3Node = json(call(HttpMethod.POST, "/api/aqua/harv/orders",
                    orderBody("hv-order-3", "石斑鱼")));
            assertSuccess(order3Node, "建立客户订单3（应冻结V2）");
            order3 = order3Node.path("data").path("id").asLong();
            check(order3Node.path("data").path("gradeVersionId").asLong() != gv1,
                    "V2生效后新订单应冻结新版本，历史订单才保留V1");
            JsonNode split3 = json(call(HttpMethod.POST, "/api/aqua/harv/orders/" + order3 + "/split",
                    splitBody("hv-split-3", splitLine("G1", "20", "30"))));
            rsD = split3.path("data").path("reservations").get(0).path("id").asLong();

            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsD + "/lock",
                    lockBody("hv-lock-d", loc5, "粤D0005"))), "预约D锁定L5");
            check("LOCKED".equals(location(loc5).getStatus())
                            && location(loc5).getReservationId() == rsD,
                    "L5应锁定互斥给预约D");
            JsonNode unlock = json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsD + "/unlock",
                    actionBody("hv-unlock-d")));
            assertSuccess(unlock, "释放预约D");
            check("PLANNED".equals(unlock.path("data").path("status").asText())
                            && "AVAILABLE".equals(location(loc5).getStatus())
                            && location(loc5).getReservationId() == null
                            && !Boolean.TRUE.equals(location(loc5).getPendingInspect()),
                    "释放后库位应恢复可用、互斥解除、待检清除");

            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/harv/reservations/" + rsD + "/lock",
                    lockBody("hv-lock-d-2", loc5, "粤D0006"))), "预约D重新锁定L5");
            JsonNode cancel = json(call(HttpMethod.POST,
                    "/api/aqua/harv/orders/" + order3 + "/cancel",
                    actionBody("hv-cancel-3")));
            assertSuccess(cancel, "取消订单3");
            check("CANCELLED".equals(cancel.path("data").path("status").asText())
                            && "AVAILABLE".equals(location(loc5).getStatus()),
                    "取消订单后锁定库位应同步释放");

            // 全程留证：关键动作齐全，按 requestId 可追溯，操作者非空。
            check(evidenceCount(HarvChangeEvidence.ENTITY_GRADE_VERSION, gv1,
                    HarvChangeEvidence.ACTION_GRADE_PUBLISH) == 1, "分级发布留证");
            check(evidenceCount(HarvChangeEvidence.ENTITY_ORDER, order1,
                    HarvChangeEvidence.ACTION_ORDER_SPLIT) == 1, "订单拆分留证");
            check(evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                    .eq("action", HarvChangeEvidence.ACTION_VEHICLE_CHANGE)) >= 1, "换车留证");
            check(evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                    .eq("action", HarvChangeEvidence.ACTION_VARIANCE_CREATE)) >= 2, "短装与超装差异均留证");
            check(evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                    .eq("action", HarvChangeEvidence.ACTION_NOTIFY_CUSTOMER)) >= 3,
                    "换车、短装、超装三次客户通知均留证");
            check(evidenceCount(HarvChangeEvidence.ENTITY_ORDER, order1,
                    HarvChangeEvidence.ACTION_ORDER_COMPLETE) == 1, "订单完成留证");

            JsonNode byRequest = json(call(HttpMethod.GET,
                    "/api/aqua/harv/evidence?requestId=hv-alloc-c-bf", null));
            assertSuccess(byRequest, "按requestId追溯证据");
            check(byRequest.path("data").size() >= 1, "补录requestId应能追溯到证据链");
            long blankOperator = evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                    .eq("request_id", "hv-alloc-a").eq("operator", ""));
            check(blankOperator == 0, "证据操作者不得为空");
            long nullOperator = evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                    .isNull("operator").or().eq("operator", ""));
            check(nullOperator == 0, "全部证据操作者不丢（含失败留证与系统通知）");
        });

        report.add("验收结论：全部检查点通过——捕捞预约按规格/重量区间/交付时间拆分、锁定互斥、"
                + "现场实际规格分仓与库位/待检同步、短装超装差异单、换车留因通知客户、"
                + "完成订单保留当时分级规则、补传补录重试只形成一条可用结果，全程留证可追溯。");
        printReport();
    }

    // ------------------------------------------------------------
    // 业务构造
    // ------------------------------------------------------------

    private Map<String, Object> pondBody() {
        return mapOf("pondCode", "HV-P1", "pondName", "捕捞验收池", "seaArea", "东海区",
                "waterDepthM", "2.5", "waterAreaM2", "1000", "salinityPpt", "30");
    }

    private Map<String, Object> orderBody(String requestId, String species) {
        return mapOf("requestId", requestId, "customerId", customerId, "pondId", pondId,
                "species", species, "operator", "捕捞调度员郑", "remark", "验收用订单");
    }

    private Map<String, Object> gradePublishBody(String requestId, Map<String, Object>... rules) {
        return mapOf("requestId", requestId, "species", "石斑鱼", "publishedBy", "分级管理员",
                "remark", "验收分级版本", "rules", Arrays.asList(rules));
    }

    private Map<String, Object> rule(String code, String name, String min, String max) {
        return mapOf("gradeCode", code, "gradeName", name, "minWeightKg", min, "maxWeightKg", max);
    }

    private long createLocation(String requestId, String code, String capacity) throws Exception {
        JsonNode node = json(call(HttpMethod.POST, "/api/aqua/harv/locations",
                mapOf("requestId", requestId, "locationCode", code, "seaArea", "东海区",
                        "capacityKg", capacity)));
        assertSuccess(node, "登记库位" + code);
        return node.path("data").path("id").asLong();
    }

    private Map<String, Object> splitBody(String requestId, Map<String, Object>... lines) {
        return mapOf("requestId", requestId, "operator", "捕捞调度员郑", "lines", Arrays.asList(lines));
    }

    private Map<String, Object> splitLine(String gradeCode, String min, String max) {
        return mapOf("gradeCode", gradeCode, "plannedMinKg", min, "plannedMaxKg", max,
                "deliveryTime", LocalDateTime.of(2026, 10, 20, 8, 0).toString());
    }

    private Map<String, Object> lockBody(String requestId, long locationId, String vehicle) {
        return mapOf("requestId", requestId, "locationId", locationId,
                "vehicleNo", vehicle, "operator", "码头调度员钱");
    }

    private Map<String, Object> allocationBody(String requestId, String source, Map<String, Object>... lines) {
        Map<String, Object> body = mapOf("requestId", requestId, "operator", "现场分仓员孙",
                "lines", Arrays.asList(lines));
        if (source != null) {
            body.put("source", source);
        }
        return body;
    }

    private Map<String, Object> allocLine(String gradeCode, String weight, long locationId) {
        return mapOf("actualGradeCode", gradeCode, "actualWeightKg", weight, "locationId", locationId);
    }

    private Map<String, Object> vehicleBody(String requestId, String toVehicle, String reason) {
        return mapOf("requestId", requestId, "toVehicleNo", toVehicle,
                "reason", reason, "operator", "车队队长冯");
    }

    private Map<String, Object> actionBody(String requestId) {
        return mapOf("requestId", requestId, "operator", "捕捞调度员郑");
    }

    private HarvLocation location(long id) {
        return locationMapper.selectById(id);
    }

    private com.evops.aqua.harv.entity.HarvReservation getReservation(long id) {
        return reservationMapper.selectById(id);
    }

    private long countUsable(long reservationId) {
        return allocationMapper.selectCount(new QueryWrapper<HarvAllocation>()
                .eq("reservation_id", reservationId)
                .eq("result_status", HarvAllocation.RESULT_USABLE));
    }

    private long countByStatus(long reservationId, String status) {
        return allocationMapper.selectCount(new QueryWrapper<HarvAllocation>()
                .eq("reservation_id", reservationId).eq("result_status", status));
    }

    private long evidenceCount(String entityType, long entityId, String action) {
        return evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                .eq("entity_type", entityType).eq("entity_id", entityId).eq("action", action));
    }

    private long evidenceCountByRequest(String entityType, long entityId, String action, String requestId) {
        return evidenceMapper.selectCount(new QueryWrapper<HarvChangeEvidence>()
                .eq("entity_type", entityType).eq("entity_id", entityId).eq("action", action)
                .eq("request_id", requestId));
    }

    private BigDecimal bd(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    // ------------------------------------------------------------
    // 验收框架
    // ------------------------------------------------------------

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 捕捞预约与规格分仓验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("==============================================\n");
        System.out.println(sb);
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
