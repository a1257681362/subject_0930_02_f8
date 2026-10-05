package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.harv.dto.NoticeAckRequest;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvNotification;
import com.evops.aqua.harv.mapper.HarvNotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户通知：换车/差异等事件向客户逐条发送；客户签收留回执，重复签收幂等不覆盖首次。
 */
@Service
public class HarvNotificationService {

    private final HarvNotificationMapper notificationMapper;
    private final HarvEvidenceService evidenceService;

    public HarvNotificationService(HarvNotificationMapper notificationMapper,
                                   HarvEvidenceService evidenceService) {
        this.notificationMapper = notificationMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发送一条客户通知。noticeSuffix 绑定业务事实（如换车记录id/差异单id），
     * 同一业务事实只通知一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvNotification notify(Long customerId, Long orderId, Long reservationId,
                                   String eventType, String noticeSuffix, String message,
                                   String operator, String requestId) {
        String noticeNo = "NT-" + eventType + "-" + noticeSuffix;
        HarvNotification existing = notificationMapper.selectOne(new QueryWrapper<HarvNotification>()
                .eq("notice_no", noticeNo).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        HarvNotification notification = new HarvNotification();
        notification.setNoticeNo(noticeNo);
        notification.setCustomerId(customerId);
        notification.setOrderId(orderId);
        notification.setReservationId(reservationId);
        notification.setEventType(eventType);
        notification.setMessage(message);
        notification.setStatus(HarvNotification.STATUS_SENT);
        notificationMapper.insert(notification);
        evidenceService.record(HarvChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                HarvChangeEvidence.ACTION_NOTIFY_CUSTOMER, requestId, operator,
                null, notification, "触发客户通知：" + eventType);
        return notification;
    }

    /** 客户签收：重复签收返回同一回执，不覆盖首次签收人与时间。 */
    @Transactional(rollbackFor = Exception.class)
    public HarvNotification ack(Long id, NoticeAckRequest request) {
        HarvNotification notification = getOrThrow(id);
        if (HarvNotification.STATUS_ACKED.equals(notification.getStatus())) {
            return notification;
        }
        HarvNotification before = copyOf(notification);
        notification.setStatus(HarvNotification.STATUS_ACKED);
        notification.setAckBy(request.getAckBy());
        notification.setAckTime(LocalDateTime.now());
        notification.setAckRemark(request.getAckRemark());
        notificationMapper.updateById(notification);
        evidenceService.record(HarvChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                HarvChangeEvidence.ACTION_NOTICE_ACK, null, request.getAckBy(), before, notification,
                "客户签收回执：" + request.getAckRemark());
        return notification;
    }

    public HarvNotification getOrThrow(Long id) {
        HarvNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new IllegalArgumentException("通知不存在，id=" + id);
        }
        return notification;
    }

    public List<HarvNotification> list(Long customerId, String status) {
        QueryWrapper<HarvNotification> wrapper = new QueryWrapper<>();
        wrapper.eq(customerId != null, "customer_id", customerId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return notificationMapper.selectList(wrapper);
    }

    private HarvNotification copyOf(HarvNotification notification) {
        HarvNotification copy = new HarvNotification();
        org.springframework.beans.BeanUtils.copyProperties(notification, copy);
        return copy;
    }
}
