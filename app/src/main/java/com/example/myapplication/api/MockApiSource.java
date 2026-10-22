package com.example.myapplication.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.Contact;
import com.example.myapplication.model.Device;
import com.example.myapplication.model.Elder;
import com.example.myapplication.model.EventRecord;
import com.example.myapplication.model.Geofence;
import com.example.myapplication.model.NotificationRecord;
import com.example.myapplication.model.TelemetryPoint;
import com.example.myapplication.model.TrendBucket;
import com.example.myapplication.utils.TimeUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 模拟数据源：后端未部署时提供符合真实接口结构的演示数据，
 * 字段与时间格式（ISO 8601 UTC）与后端 schema 保持一致，便于无缝切换。
 * 另模拟 WebSocket 推送（alert / telemetry），用于演示实时告警弹窗。
 */
public class MockApiSource implements ApiSource {

    private static final String TAG = "MockApiSource";
    private static final long PUSH_INTERVAL_MS = 20_000L;

    private static MockApiSource instance;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HealthDataService.PushListener pushListener;
    private boolean pushing;

    private MockApiSource() {
    }

    public static synchronized MockApiSource getInstance() {
        if (instance == null) {
            instance = new MockApiSource();
        }
        return instance;
    }

    @Override
    public List<Elder> getElders() {
        List<Elder> elders = new ArrayList<>();
        elders.add(new Elder(1L, "演示老人-张奶奶", "female", "1945-03-12",
                158, 56.0, "上海市浦东新区演示路 1 号", "课设演示数据"));
        return elders;
    }

    @Override
    public List<TelemetryPoint> getLatestTelemetry(int limit) {
        List<TelemetryPoint> points = new ArrayList<>();
        long now = System.currentTimeMillis();
        // 演示异常段：每 60 秒周期内第 20~35 秒，最新遥测点心率/血氧越界
        // （约持续 15 秒，足够触发"轻提醒 → 持续越界升级弹窗"的完整演示）
        long sec = (now / 1000L) % 60;
        boolean abnormalWindow = sec >= 20 && sec < 35;
        int count = Math.min(limit, 20);
        for (int i = 0; i < count; i++) {
            long t = now - (count - 1 - i) * 60_000L;
            boolean latest = (i == count - 1);
            double hr = (latest && abnormalWindow)
                    ? 108 + random.nextInt(8)   // 108~115 bpm 偏高
                    : 60 + random.nextInt(41);
            double sp = (latest && abnormalWindow)
                    ? 88 + random.nextInt(5)    // 88~92% 偏低
                    : 95 + random.nextInt(6);
            points.add(new TelemetryPoint(1000 + i, 2, 1, "heart_rate",
                    hr, null, null, null, iso(t)));
            points.add(new TelemetryPoint(2000 + i, 2, 1, "spo2",
                    sp, null, null, null, iso(t)));
            if (i % 5 == 0) {
                points.add(new TelemetryPoint(3000 + i, 2, 1, "steps",
                        (double) random.nextInt(60), null, null, null, iso(t)));
            }
        }
        return points;
    }

    @Override
    public List<TrendBucket> getHealthTrends(String interval, int limit) {
        return getHealthTrends(interval, limit, null, null);
    }

    @Override
    public List<TrendBucket> getHealthTrends(String interval, int limit, String start, String end) {
        List<TrendBucket> buckets = new ArrayList<>();
        long now = System.currentTimeMillis();
        long step = "day".equals(interval) ? 24 * 3600_000L : 3600_000L;
        int count = Math.min(limit, 24);
        for (int i = count - 1; i >= 0; i--) {
            long bucketTime = now - i * step;
            TrendBucket bucket = new TrendBucket();
            bucket.setBucketStart(iso(bucketTime));
            bucket.setHeartRate(new TrendBucket.MetricStats(
                    65 + random.nextInt(15), 55 + random.nextInt(10), 85 + random.nextInt(15), 6));
            bucket.setSpo2(new TrendBucket.MetricStats(
                    95 + random.nextInt(3), 93 + random.nextInt(3), 99, 6));
            bucket.setStepsTotal((double) random.nextInt(200));
            bucket.setActivity(new ArrayList<>());
            buckets.add(bucket);
        }
        return buckets;
    }

    @Override
    public List<EventRecord> getEvents(String type, int limit) {
        return getEvents(type, null, null, limit);
    }

    @Override
    public List<EventRecord> getEvents(String type, String start, String end, int limit) {
        List<EventRecord> events = new ArrayList<>();
        long now = System.currentTimeMillis();
        long day = 24 * 3600_000L;
        long startMillis = parseIso(start);
        addEvent(events, type, startMillis, new EventRecord(101L, 1L, 2L, EventRecord.TYPE_FALL,
                "critical", 0.96, iso(now - 30 * 60_000L), "{}", "new"));
        addEvent(events, type, startMillis, new EventRecord(102L, 1L, 2L, EventRecord.TYPE_SOS,
                "warning", null, iso(now - 2 * 3600_000L), "{}", "new"));
        addEvent(events, type, startMillis, new EventRecord(103L, 1L, 2L, EventRecord.TYPE_LOW_BATTERY,
                "warning", null, iso(now - 5 * 3600_000L), "{\"battery\":18}", "handled"));
        addEvent(events, type, startMillis, new EventRecord(104L, 1L, 2L, EventRecord.TYPE_GEOFENCE_EXIT,
                "info", null, iso(now - 1 * day), "{}", "closed"));
        addEvent(events, type, startMillis, new EventRecord(105L, 1L, 2L, EventRecord.TYPE_FALL,
                "critical", 0.88, iso(now - 3 * day), "{}", "handled"));
        return events;
    }

    private void addEvent(List<EventRecord> events, String typeFilter, long startMillis, EventRecord event) {
        if (startMillis > 0 && TimeUtils.parseIsoToMillis(event.getOccurredAt()) < startMillis) {
            return;
        }
        if (typeFilter == null || typeFilter.isEmpty() || typeFilter.equals(event.getType())) {
            events.add(event);
        }
    }

    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        return TimeUtils.parseIsoToMillis(iso);
    }

    @Override
    public List<AlertRecord> getAlerts(int limit) {
        List<AlertRecord> alerts = new ArrayList<>();
        long now = System.currentTimeMillis();
        AlertRecord fall = new AlertRecord(10L, 101L, 1L, "new",
                iso(now - 30 * 60_000L), EventRecord.TYPE_FALL, "critical");
        fall.setElderName("演示老人-张奶奶");
        alerts.add(fall);
        AlertRecord sos = new AlertRecord(11L, 102L, 1L, "handled",
                iso(now - 2 * 3600_000L), EventRecord.TYPE_SOS, "warning");
        sos.setElderName("演示老人-张奶奶");
        alerts.add(sos);
        return alerts;
    }

    @Override
    public boolean ackAlert(long alertId) {
        Log.d(TAG, "ack alert " + alertId);
        return true;
    }

    @Override
    public List<NotificationRecord> getNotifications(long alertId) {
        List<NotificationRecord> notifications = new ArrayList<>();
        notifications.add(new NotificationRecord(31L, alertId, 3L, "console",
                "13800000001", "【critical】演示老人-张奶奶 触发跌倒，请及时处理。",
                "sent", iso(System.currentTimeMillis() - 30 * 60_000L)));
        return notifications;
    }

    // ============ SPEC 06：family 新能力（演示数据） ============

    @Override
    public List<Contact> getContacts(long elderId) {
        List<Contact> contacts = new ArrayList<>();
        contacts.add(new Contact(1L, "演示儿子", "13800000001", "son", 1, true));
        contacts.add(new Contact(2L, "演示女儿", "13800000002", "daughter", 2, false));
        return contacts;
    }

    @Override
    public List<Contact> saveContacts(long elderId, List<Contact> contacts) {
        Log.d(TAG, "save contacts count=" + contacts.size());
        return contacts;
    }

    @Override
    public List<Device> getDevices(long elderId) {
        List<Device> devices = new ArrayList<>();
        Device d = new Device();
        d.setId(2L);
        d.setDeviceUid("esp32-room-0001");
        d.setKind("esp32");
        d.setModel("ESP32-C3");
        d.setStatus("online");
        d.setElderId(1L);
        d.setBatteryPercent(86);
        d.setLastSeenAt(iso(System.currentTimeMillis() - 60_000L));
        d.setBoundAt(iso(System.currentTimeMillis() - 7L * 24 * 3600_000L));
        devices.add(d);
        return devices;
    }

    @Override
    public List<Geofence> getGeofences(long elderId) {
        List<Geofence> fences = new ArrayList<>();
        Geofence g = new Geofence();
        g.setId(5L);
        g.setName("居家安全围栏");
        g.setCenterLat(31.2304);
        g.setCenterLon(121.4737);
        g.setRadiusM(800);
        g.setEnabled(true);
        fences.add(g);
        return fences;
    }

    /** 开始模拟实时推送（替代 WebSocket） */
    public void startPush(HealthDataService.PushListener listener) {
        this.pushListener = listener;
        if (pushing) {
            return;
        }
        pushing = true;
        handler.postDelayed(pushRunnable, PUSH_INTERVAL_MS);
        Log.d(TAG, "mock push started");
    }

    public void stopPush() {
        pushing = false;
        pushListener = null;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "mock push stopped");
    }

    private final Runnable pushRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pushing || pushListener == null) {
                return;
            }
            if (random.nextDouble() < 0.35) {
                // 模拟收到跌倒告警推送（联调时便于观察弹窗）
                AlertRecord alert = new AlertRecord(1000 + random.nextInt(999), 1000 + random.nextInt(999),
                        1L, "new", iso(System.currentTimeMillis()),
                        EventRecord.TYPE_FALL, "critical");
                alert.setElderName("演示老人-张奶奶");
                alert.setOccurredAt(iso(System.currentTimeMillis()));
                pushListener.onAlert(alert);
            } else {
                TelemetryPoint point = new TelemetryPoint(0, 2, 1, "heart_rate",
                        (double) (60 + random.nextInt(41)), null, null, null,
                        iso(System.currentTimeMillis()));
                pushListener.onTelemetry(point);
            }
            handler.postDelayed(this, PUSH_INTERVAL_MS);
        }
    };

    private static String iso(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        return sdf.format(new Date(millis));
    }
}
