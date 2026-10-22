package com.example.myapplication.model;

import com.example.myapplication.utils.TimeUtils;

/**
 * 遥测点（TelemetryOut）
 * 数据字典：id / device_id / elder_id / metric / value_num / value_text / lat / lon / recorded_at
 * metric 枚举：heart_rate / spo2 / steps / activity / gait / location / battery
 */
public class TelemetryPoint {

    private long id;
    private long deviceId;
    private long elderId;
    private String metric;
    private Double valueNum;
    private String valueText;
    private Double lat;
    private Double lon;
    private String recordedAt;

    public TelemetryPoint() {
    }

    public TelemetryPoint(long id, long deviceId, long elderId, String metric,
                          Double valueNum, String valueText, Double lat, Double lon, String recordedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.elderId = elderId;
        this.metric = metric;
        this.valueNum = valueNum;
        this.valueText = valueText;
        this.lat = lat;
        this.lon = lon;
        this.recordedAt = recordedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public long getElderId() {
        return elderId;
    }

    public void setElderId(long elderId) {
        this.elderId = elderId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Double getValueNum() {
        return valueNum;
    }

    public void setValueNum(Double valueNum) {
        this.valueNum = valueNum;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    /** 记录时间毫秒（本地展示用） */
    public long getRecordedTimeMillis() {
        return TimeUtils.parseIsoToMillis(recordedAt);
    }
}
