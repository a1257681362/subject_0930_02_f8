package com.evops.aqua.harv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 捕捞预约模块变更证据：规则发布/订单/拆分/锁定/释放/分仓/作废重传/差异/换车/通知/签收/检验全部留痕。
 * 只追加，每条证据必须带操作者（operator）与幂等请求号；失败原因用独立事务留证，不随业务回滚。
 */
@TableName("t_harv_change_evidence")
public class HarvChangeEvidence extends BaseEntity {

    public static final String ENTITY_GRADE_VERSION = "GRADE_VERSION";
    public static final String ENTITY_ORDER = "CUSTOMER_ORDER";
    public static final String ENTITY_RESERVATION = "RESERVATION";
    public static final String ENTITY_LOCATION = "LOCATION";
    public static final String ENTITY_ALLOCATION = "ALLOCATION";
    public static final String ENTITY_VARIANCE = "VARIANCE";
    public static final String ENTITY_VEHICLE_CHANGE = "VEHICLE_CHANGE";
    public static final String ENTITY_NOTIFICATION = "NOTIFICATION";

    public static final String ACTION_GRADE_PUBLISH = "GRADE_PUBLISH";
    public static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
    public static final String ACTION_ORDER_SPLIT = "ORDER_SPLIT";
    public static final String ACTION_ORDER_COMPLETE = "ORDER_COMPLETE";
    public static final String ACTION_ORDER_CANCEL = "ORDER_CANCEL";
    public static final String ACTION_RESERVATION_LOCK = "RESERVATION_LOCK";
    public static final String ACTION_RESERVATION_UNLOCK = "RESERVATION_UNLOCK";
    public static final String ACTION_RESERVATION_COMPLETE = "RESERVATION_COMPLETE";
    public static final String ACTION_ALLOCATION_RECORD = "ALLOCATION_RECORD";
    public static final String ACTION_ALLOCATION_VOID = "ALLOCATION_VOID";
    public static final String ACTION_ALLOCATION_INSPECT = "ALLOCATION_INSPECT";
    public static final String ACTION_VARIANCE_CREATE = "VARIANCE_CREATE";
    public static final String ACTION_VARIANCE_HANDLE = "VARIANCE_HANDLE";
    public static final String ACTION_VEHICLE_CHANGE = "VEHICLE_CHANGE";
    public static final String ACTION_NOTIFY_CUSTOMER = "NOTIFY_CUSTOMER";
    public static final String ACTION_NOTICE_ACK = "NOTICE_ACK";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
