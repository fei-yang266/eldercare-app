package com.example.myapplication.model;

/**
 * 通知记录（NotificationOut）
 * 数据字典：id / alert_id / contact_id / channel / target / content / status / sent_at
 * channel：sms / wechat / console（当前写路径只产生 console 模拟通知）
 * status：pending / sent / failed
 */
public class NotificationRecord {

    private long id;
    private long alertId;
    private Long contactId;
    private String channel;
    private String target;
    private String content;
    private String status;
    private String sentAt;

    public NotificationRecord() {
    }

    public NotificationRecord(long id, long alertId, Long contactId, String channel,
                              String target, String content, String status, String sentAt) {
        this.id = id;
        this.alertId = alertId;
        this.contactId = contactId;
        this.channel = channel;
        this.target = target;
        this.content = content;
        this.status = status;
        this.sentAt = sentAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAlertId() {
        return alertId;
    }

    public void setAlertId(long alertId) {
        this.alertId = alertId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSentAt() {
        return sentAt;
    }

    public void setSentAt(String sentAt) {
        this.sentAt = sentAt;
    }
}
