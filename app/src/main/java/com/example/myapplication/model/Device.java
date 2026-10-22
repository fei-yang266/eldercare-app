package com.example.myapplication.model;

/**
 * 绑定设备（DeviceOut）
 * 数据字典：id / device_uid / kind / model / status / elder_id /
 *               battery_percent / last_seen_at / bound_at
 * family 仅只读（GET /elders/{id}/devices）；kind 含 esp32/phone/wearable/lora_node。
 */
public class Device {

    private long id;
    private String deviceUid;
    private String kind;
    private String model;
    private String status;      // online / offline / disabled
    private Long elderId;
    private Integer batteryPercent;
    private String lastSeenAt;
    private String boundAt;

    public Device() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDeviceUid() {
        return deviceUid;
    }

    public void setDeviceUid(String deviceUid) {
        this.deviceUid = deviceUid;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public Integer getBatteryPercent() {
        return batteryPercent;
    }

    public void setBatteryPercent(Integer batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public String getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(String boundAt) {
        this.boundAt = boundAt;
    }
}
