package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.EventRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 告警管理（对齐 docs/app-integration.md 告警与 WebSocket 章节）
 * - 进程级单例：由前台服务 AlertMonitorService 持有生命周期，App 退后台/锁屏仍监控
 * - 实时通道：监听 WebSocket alert 推送（Mock 模式为模拟推送）
 * - 兜底通道：每 10s REST 轮询 alerts，发现状态为 new 的【紧急】告警发系统通知（WS 断开时仍可靠）
 * - 触达方式（L1）：紧急告警统一走【系统通知】（高优先级横幅，后台/锁屏/通知栏可见），
 *   通知带「确认/忽略」动作，确认联动后端 ack（POST /alerts/{id}/ack）+ 写已确认集合；
 *   不做 App 内弹窗（弹窗确认路径曾引入并发崩溃，且后台无法触达，已整体移除）
 * - 紧急分级：仅紧急告警（severity=critical，或事件类型为 跌倒/SOS）发通知；
 *   低电量、离线等非紧急只进历史记录
 * - 本地告警：实时页检测到心率/血氧持续异常时通过 showLocalAlert 发通知（id 为负数，
 *   通知确认不调用后端 ack，仅写已确认集合）
 * - 去重：已通知告警 id 持久化到 SharedPreferences，同一告警重启/重登录也不重复通知
 *   （后端 ack 后 status 变 handled，天然不再通知；持久化去重兜底 ack 失败/未确认的情况）
 * - 线程安全：所有集合用 CopyOnWriteArraySet，后台线程（通知确认 ack、WS 回调）与主线程
 *   （SOS 页 10s 遍历）并发访问不抛 ConcurrentModificationException
 */
public class FallAlertManager {

    private static final String TAG = "FallAlertManager";
    private static final long POLLING_INTERVAL = 10_000L;
    /** 已通知告警 id 持久化（仅后端告警 id>0；本地告警 id 为负数不持久化） */
    private static final String PREF_SHOWN_ALERTS = "shown_alerts";
    private static final String KEY_SHOWN_IDS = "ids";
    /** 已检测过的紧急事件 id 持久化（后端告警通知时同步记录其 event_id，避免重复通知） */
    private static final String KEY_SHOWN_EVENT_IDS = "event_ids";
    /** 用户已确认处理的紧急事件 id（通知确认 / SOS 页二次确认后写入；SOS 页不再展示） */
    private static final String KEY_ACKED_EVENT_IDS = "acked_event_ids";
    /** 持久化集合上限：防止无界增长，超限重建 */
    private static final int MAX_SHOWN_IDS = 300;

    private static volatile FallAlertManager instance;

    private final Context context;
    private final HealthDataService service;
    /** 线程安全集合：后台线程（通知确认 ack、WS 回调）与主线程（SOS 页 10s 遍历）并发访问 */
    private final Set<Long> shownAlertIds = new CopyOnWriteArraySet<>();
    private final Set<Long> shownEventIds = new CopyOnWriteArraySet<>();
    /** 已确认紧急事件（通知确认 / SOS 页二次确认）；SOS 页每 10s 遍历读取 */
    private final Set<Long> ackedEventIds = new CopyOnWriteArraySet<>();

    private Handler handler;
    private boolean running;

    /** 进程级单例：首次以任意 Context 创建（SharedPreferences 用 app context） */
    public static FallAlertManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FallAlertManager.class) {
                if (instance == null) {
                    instance = new FallAlertManager(context);
                }
            }
        }
        return instance;
    }

    private FallAlertManager(Context context) {
        this.context = context.getApplicationContext();
        this.service = HealthDataService.getInstance(context);
        shownAlertIds.addAll(loadShownIds(KEY_SHOWN_IDS));
        shownEventIds.addAll(loadShownIds(KEY_SHOWN_EVENT_IDS));
        ackedEventIds.addAll(loadShownIds(KEY_ACKED_EVENT_IDS));
    }

    /** 已确认事件 id（供 SOS 页过滤，与 SOS 页二次确认共用同一持久化 key） */
    public Set<Long> getAckedEventIds() {
        return ackedEventIds;
    }

    /** 外部（SOS 页二次确认 / 通知确认）标记事件已确认：写入持久化，SOS 页不再展示 */
    public void acknowledgeEvent(long eventId) {
        if (eventId <= 0) {
            return;
        }
        ackedEventIds.add(eventId);
        persistAckedEventIds();
    }

    /** 是否紧急告警：critical 严重度，或事件类型为跌倒 / SOS（后端 severity 缺失时兜底） */
    public static boolean isEmergency(AlertRecord alert) {
        if (alert == null) {
            return false;
        }
        if ("critical".equals(alert.getSeverity())) {
            return true;
        }
        return EventRecord.TYPE_FALL.equals(alert.getEventType())
                || EventRecord.TYPE_SOS.equals(alert.getEventType());
    }

    /** 启动告警监听：注册推送 + 启动轮询兜底 */
    public void startCheck() {
        if (running) {
            return;
        }
        running = true;
        // WS 连接成功（收到 ready）后立即补拉一次全量数据，补齐断线/重连期间漏掉的告警与事件
        service.setOnWsReadyListener(() -> {
            Handler h = handler;
            if (h != null) {
                h.post(this::pollAlerts);
            }
        });
        service.startPush(pushListener);
        handler = new Handler(Looper.getMainLooper());
        handler.post(pollRunnable);
        Log.d(TAG, "alert monitoring started");
    }

    /** 停止监听（可真正取消已排队的回调） */
    public void stopCheck() {
        if (!running) {
            return;
        }
        running = false;
        service.stopPush();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        Log.d(TAG, "alert monitoring stopped");
    }

    private final HealthDataService.PushListener pushListener = new HealthDataService.PushListener() {
        @Override
        public void onAlert(AlertRecord alert) {
            // WS 回调在 OkHttp 读线程（非主线程），统一切主线程处理（去重集合/通知构造需主线程）
            Handler h = handler;
            if (h == null) {
                return;
            }
            h.post(() -> {
                Log.i(TAG, "[WS-ALERT] id=" + alert.getId()
                        + " type=" + alert.getEventType()
                        + " status=" + alert.getStatus()
                        + " severity=" + alert.getSeverity());
                // 仅紧急告警发通知，非紧急只进历史记录；记录 event_id 防止 REST 事件检测重复通知
                if (isEmergency(alert)) {
                    notifyAlertIfNew(alert);
                    if (alert.getEventId() > 0) {
                        shownEventIds.add(alert.getEventId());
                        persistShownEventIds();
                    }
                }
            });
        }

        @Override
        public void onTelemetry(com.example.myapplication.model.TelemetryPoint point) {
            // 遥测由实时页统一刷新，此处不处理
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollAlerts();
            if (running) {
                handler.postDelayed(this, POLLING_INTERVAL);
            }
        }
    };

    /**
     * REST 轮询兜底：拉取告警 + 事件列表。
     * - 后端告警（status=new 且紧急）→ 发系统通知（确认可 ack）
     * - 紧急事件检测：后端仅产生事件未生成告警时，App 本地发通知兜底（按 event_id 去重，
     *   与告警通知联动——后端有告警的事件已记录 event_id，不重复通知）
     * 告警/事件两个接口独立容错：一个失败不影响另一个；日志全链路可定位。
     */
    private void pollAlerts() {
        new Thread(() -> {
            List<AlertRecord> alerts = new ArrayList<>();
            List<EventRecord> events = new ArrayList<>();
            try {
                alerts = service.getAlerts();
            } catch (Exception e) {
                Log.e(TAG, "poll alerts failed", e);
            }
            try {
                events = service.getEvents(null);
            } catch (Exception e) {
                Log.e(TAG, "poll events failed", e);
            }
            Log.i(TAG, "[POLL] alerts=" + alerts.size() + " events=" + events.size());
            List<AlertRecord> finalAlerts = alerts;
            List<EventRecord> finalEvents = events;
            Handler h = handler;
            if (h != null) {
                h.post(() -> {
                    // 1. 后端告警：未处理的紧急告警发通知；所有紧急告警的 event_id 都记录，
                    //    防止同事件走本地事件检测重复通知
                    for (AlertRecord alert : finalAlerts) {
                        if (isEmergency(alert)) {
                            if ("new".equals(alert.getStatus())) {
                                notifyAlertIfNew(alert);
                            }
                            if (alert.getEventId() > 0) {
                                shownEventIds.add(alert.getEventId());
                            }
                        }
                    }
                    persistShownEventIds();
                    // 2. 紧急事件本地兜底：后端只记录事件未生成告警时发通知（新事件才发）
                    for (EventRecord event : finalEvents) {
                        if (!isEmergencyEvent(event) || shownEventIds.contains(event.getId())) {
                            continue;
                        }
                        Log.i(TAG, "[EMERGENCY-EVENT] id=" + event.getId()
                                + " type=" + event.getType()
                                + " severity=" + event.getSeverity());
                        shownEventIds.add(event.getId());
                        notifyEmergencyEvent(event);
                    }
                    persistShownEventIds();
                });
            }
        }).start();
    }

    /** 紧急事件判定：跌倒 / SOS，或后端标记 critical */
    private static boolean isEmergencyEvent(EventRecord event) {
        if (event == null) {
            return false;
        }
        return "critical".equals(event.getSeverity())
                || EventRecord.TYPE_FALL.equals(event.getType())
                || EventRecord.TYPE_SOS.equals(event.getType());
    }

    /** 紧急事件 → 本地告警通知（无后端 alertId，id 取负数标记，确认不调后端 ack） */
    private void notifyEmergencyEvent(EventRecord event) {
        AlertRecord alert = new AlertRecord();
        alert.setId(-event.getId());
        alert.setEventId(event.getId());
        alert.setElderId(event.getElderId());
        alert.setEventType(event.getType());
        alert.setSeverity(event.getSeverity() == null ? "critical" : event.getSeverity());
        alert.setStatus(event.getStatus());
        alert.setOccurredAt(event.getOccurredAt());
        notifyAlertIfNew(alert);
    }

    /** 本地健康异常告警入口（实时页检测到心率/血氧持续异常时调用）；id 为负数标记本地，确认不调后端 */
    public void showLocalAlert(AlertRecord alert) {
        notifyAlertIfNew(alert);
    }

    /** 去重后发系统通知（主线程调用）；同一告警只触发一次，确认/忽略动作由通知承载 */
    private void notifyAlertIfNew(AlertRecord alert) {
        // 防御：通知构造也要求主线程（PendingIntent），非主线程调用先切主线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler h = handler;
            if (h != null) {
                h.post(() -> notifyAlertIfNew(alert));
            }
            return;
        }
        if (alert == null || shownAlertIds.contains(alert.getId())) {
            return;
        }
        shownAlertIds.add(alert.getId());
        // 仅持久化后端告警（id>0）；本地健康异常告警 id 为负数，不落盘
        if (alert.getId() > 0) {
            persistShownIds();
        }
        NotificationHelper.notifyEmergency(context, alert);
        Log.i(TAG, "[NOTIFY] alertId=" + alert.getId()
                + " type=" + alert.getEventType()
                + " severity=" + alert.getSeverity());
    }

    /** 从持久化加载已通知告警/事件 id */
    private Set<Long> loadShownIds(String key) {
        SharedPreferences sp = context.getSharedPreferences(PREF_SHOWN_ALERTS, Context.MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(key, new HashSet<>());
        Set<Long> ids = new HashSet<>();
        for (String s : saved) {
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        return ids;
    }

    /** 持久化已通知告警 id（超上限时重建，避免无界增长） */
    private void persistShownIds() {
        if (shownAlertIds.size() > MAX_SHOWN_IDS) {
            shownAlertIds.clear();
        }
        Set<String> saved = new HashSet<>();
        for (Long id : shownAlertIds) {
            saved.add(String.valueOf(id));
        }
        context.getSharedPreferences(PREF_SHOWN_ALERTS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_SHOWN_IDS, saved).apply();
    }

    /** 持久化已检测紧急事件 id（超上限时重建） */
    private void persistShownEventIds() {
        if (shownEventIds.size() > MAX_SHOWN_IDS) {
            shownEventIds.clear();
        }
        Set<String> saved = new HashSet<>();
        for (Long id : shownEventIds) {
            saved.add(String.valueOf(id));
        }
        context.getSharedPreferences(PREF_SHOWN_ALERTS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_SHOWN_EVENT_IDS, saved).apply();
    }

    /** 持久化已确认事件 id（超上限时重建） */
    private void persistAckedEventIds() {
        if (ackedEventIds.size() > MAX_SHOWN_IDS) {
            ackedEventIds.clear();
        }
        Set<String> saved = new HashSet<>();
        for (Long id : ackedEventIds) {
            saved.add(String.valueOf(id));
        }
        context.getSharedPreferences(PREF_SHOWN_ALERTS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_ACKED_EVENT_IDS, saved).apply();
    }
}
