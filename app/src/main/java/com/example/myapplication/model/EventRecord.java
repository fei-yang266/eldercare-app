package com.example.myapplication.model;

import com.example.myapplication.utils.TimeUtils;

/**
 * 事件（EventOut）
 * 数据字典：id / elder_id / device_id / type / severity / confidence /
 *          occurred_at / payload_json / status
 * type 枚举：fall / sos / geofence_exit / low_battery / device_offline
 * severity：info / warning / critical；status：new / handled / closed
 * 注意：事件为只读列表，无删除/修改接口。
 */
public class EventRecord {

    public static final String TYPE_FALL = "fall";
    public static final String TYPE_SOS = "sos";
    public static final String TYPE_GEOFENCE_EXIT = "geofence_exit";
    public static final String TYPE_LOW_BATTERY = "low_battery";
    public static final String TYPE_DEVICE_OFFLINE = "device_offline";

    private long id;
    private long elderId;
    private long deviceId;
    private String type;
    private String severity;
    private Double confidence;
    private String occurredAt;
    private String payloadJson;
    private String status;

    public EventRecord() {
    }

    public EventRecord(long id, long elderId, long deviceId, String type, String severity,
                       Double confidence, String occurredAt, String payloadJson, String status) {
        this.id = id;
        this.elderId = elderId;
        this.deviceId = deviceId;
        this.type = type;
        this.severity = severity;
        this.confidence = confidence;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getElderId() {
        return elderId;
    }

    public void setElderId(long elderId) {
        this.elderId = elderId;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getOccurredTimeMillis() {
        return TimeUtils.parseIsoToMillis(occurredAt);
    }
}
