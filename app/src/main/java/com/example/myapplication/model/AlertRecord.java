package com.example.myapplication.model;

import com.example.myapplication.utils.TimeUtils;

/**
 * 告警（AlertOut + WebSocket alert）
 * REST 与 WebSocket 一致的数据字典：id / event_id / elder_id / status / created_at /
 *               handled_at / handled_by / event_type / severity / occurred_at / elder_name
 * （SPEC 06 起 REST AlertOut 同样携带 occurred_at 与 elder_name，App 无需再按 elder_id 缓存补全；
 *   缓存仅做字段缺失时的兜底。）
 * status：new / handled / closed；确认走 POST /api/v1/alerts/{id}/ack（幂等）。
 * 告警查询按文档建议加 status=new 过滤与 limit=500，App 侧只消费未处理告警。
 */
public class AlertRecord {

    private long id;
    private long eventId;
    private long elderId;
    private String elderName;   // REST 与 WS 均提供；字段缺失时由 App 缓存兜底
    private String eventType;
    private String severity;
    private String occurredAt;  // REST 与 WS 均提供；弹窗时间优先取此字段
    private String createdAt;
    private String handledAt;
    private Long handledBy;
    private String status;

    public AlertRecord() {
    }

    public AlertRecord(long id, long eventId, long elderId, String status,
                       String createdAt, String eventType, String severity) {
        this.id = id;
        this.eventId = eventId;
        this.elderId = elderId;
        this.status = status;
        this.createdAt = createdAt;
        this.eventType = eventType;
        this.severity = severity;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public long getElderId() {
        return elderId;
    }

    public void setElderId(long elderId) {
        this.elderId = elderId;
    }

    public String getElderName() {
        return elderName;
    }

    public void setElderName(String elderName) {
        this.elderName = elderName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(String handledAt) {
        this.handledAt = handledAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedTimeMillis() {
        return TimeUtils.parseIsoToMillis(createdAt);
    }

    /** WebSocket 推送的触发时间（毫秒）；无推送时间时回退到创建时间 */
    public long getOccurredTimeMillis() {
        long occurred = TimeUtils.parseIsoToMillis(occurredAt);
        return occurred > 0 ? occurred : getCreatedTimeMillis();
    }
}
