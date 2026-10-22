package com.example.myapplication.api;

import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.Contact;
import com.example.myapplication.model.Device;
import com.example.myapplication.model.Elder;
import com.example.myapplication.model.EventRecord;
import com.example.myapplication.model.Geofence;
import com.example.myapplication.model.NotificationRecord;
import com.example.myapplication.model.TelemetryPoint;
import com.example.myapplication.model.TrendBucket;

import java.util.List;

/**
 * 数据源契约：MockApiSource（后端未部署，模拟演示）与
 * RemoteApiSource（真实后端）实现同一接口，UI 层不感知数据源切换。
 */
public interface ApiSource {

    List<Elder> getElders();

    /** 最新遥测（按 recorded_at 降序取 limit 条） */
    List<TelemetryPoint> getLatestTelemetry(int limit);

    /** 健康趋势（interval=hour|day） */
    List<TrendBucket> getHealthTrends(String interval, int limit);

    /** 健康趋势（按时间窗口过滤：start/end 为带时区 ISO 8601，可空表示不限） */
    List<TrendBucket> getHealthTrends(String interval, int limit, String start, String end);

    /** 事件列表（type 为空表示全部；start/end 为带时区 ISO 8601，可空表示不限） */
    List<EventRecord> getEvents(String type, int limit);

    /** 事件列表（按日期范围过滤，时间参数走后端；start/end 可空） */
    List<EventRecord> getEvents(String type, String start, String end, int limit);

    /** 告警列表（status 为空表示全部） */
    List<AlertRecord> getAlerts(int limit);

    /** 确认告警（幂等），返回是否成功 */
    boolean ackAlert(long alertId);

    /** 告警通知记录 */
    List<NotificationRecord> getNotifications(long alertId);

    // ============ SPEC 06：family 新能力 ============

    /** 紧急联系人列表（只读） */
    List<Contact> getContacts(long elderId);

    /** 整体替换紧急联系人；返回保存后的完整列表 */
    List<Contact> saveContacts(long elderId, List<Contact> contacts) throws Exception;

    /** 绑定设备列表（family 只读） */
    List<Device> getDevices(long elderId);

    /** 电子围栏列表（family 只读） */
    List<Geofence> getGeofences(long elderId);
}
