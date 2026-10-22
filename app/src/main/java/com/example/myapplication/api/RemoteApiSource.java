package com.example.myapplication.api;

import android.content.Context;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程数据源：通过 ApiClient 调用真实后端（docs/app-integration.md 第 5 节）。
 * 后端部署完成且本机可达后，HealthDataService.setMockMode(false) 即切换至此实现。
 * 注意：所有请求在调用线程执行（同步），UI 层调用需放到后台线程。
 */
public class RemoteApiSource implements ApiSource {

    private static final String TAG = "RemoteApiSource";

    private final ApiClient apiClient;
    private final SessionManager session;
    private Long cachedElderId;
    /** 老人 id→名字 缓存：告警弹窗补全 elder_name 用（REST 已携带时优先用后端字段，缓存仅兜底） */
    private Map<Long, String> elderNameById = new HashMap<>();
    private long elderNameCacheAt = 0L;
    /** 缓存有效期：5 分钟（管理台可能新增/改名老人） */
    private static final long ELDER_NAME_CACHE_TTL_MS = 5 * 60_000L;

    public RemoteApiSource(Context context) {
        this.apiClient = new ApiClient(context);
        this.session = SessionManager.getInstance(context);
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    @Override
    public List<Elder> getElders() {
        List<Elder> elders = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(apiClient.get("/elders", null));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Elder elder = new Elder();
                elder.setId(o.optLong("id"));
                elder.setName(o.optString("name"));
                elder.setGender(o.isNull("gender") ? null : o.optString("gender"));
                elder.setBirthDate(o.isNull("birth_date") ? null : o.optString("birth_date"));
                elder.setHeightCm(o.isNull("height_cm") ? null : o.optInt("height_cm"));
                elder.setWeightKg(o.isNull("weight_kg") ? null : o.optDouble("weight_kg"));
                elder.setAddress(o.isNull("address") ? null : o.optString("address"));
                elder.setNotes(o.isNull("notes") ? null : o.optString("notes"));
                elder.setPhone(o.isNull("phone") ? null : o.optString("phone"));
                elders.add(elder);
            }
        } catch (Exception e) {
            Log.e(TAG, "getElders failed", e);
        }
        return elders;
    }

    @Override
    public List<TelemetryPoint> getLatestTelemetry(int limit) {
        List<TelemetryPoint> points = new ArrayList<>();
        try {
            Map<String, String> query = new HashMap<>();
            query.put("limit", String.valueOf(limit));
            JSONArray arr = new JSONArray(
                    apiClient.get("/elders/" + getElderId() + "/telemetry", query));
            for (int i = 0; i < arr.length(); i++) {
                points.add(parseTelemetry(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "getLatestTelemetry failed", e);
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
        try {
            Map<String, String> query = new HashMap<>();
            query.put("interval", interval);
            query.put("limit", String.valueOf(limit));
            if (start != null && !start.isEmpty()) {
                query.put("start", start);
            }
            if (end != null && !end.isEmpty()) {
                query.put("end", end);
            }
            JSONArray arr = new JSONArray(
                    apiClient.get("/elders/" + getElderId() + "/health-trends", query));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                TrendBucket bucket = new TrendBucket();
                bucket.setBucketStart(o.optString("bucket_start"));
                JSONObject hr = o.optJSONObject("heart_rate");
                if (hr != null) {
                    bucket.setHeartRate(parseStats(hr));
                }
                JSONObject spo2 = o.optJSONObject("spo2");
                if (spo2 != null) {
                    bucket.setSpo2(parseStats(spo2));
                }
                bucket.setStepsTotal(o.isNull("steps_total") ? null : o.optDouble("steps_total"));
                JSONArray activity = o.optJSONArray("activity");
                if (activity != null) {
                    List<TrendBucket.ActivityCount> counts = new ArrayList<>();
                    for (int j = 0; j < activity.length(); j++) {
                        JSONObject a = activity.getJSONObject(j);
                        counts.add(new TrendBucket.ActivityCount(
                                a.optString("activity"), a.optInt("count")));
                    }
                    bucket.setActivity(counts);
                }
                buckets.add(bucket);
            }
        } catch (Exception e) {
            Log.e(TAG, "getHealthTrends failed", e);
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
        try {
            Map<String, String> query = new HashMap<>();
            query.put("elder_id", String.valueOf(getElderId()));
            if (type != null && !type.isEmpty()) {
                query.put("type", type);
            }
            if (start != null && !start.isEmpty()) {
                query.put("start", start);
            }
            if (end != null && !end.isEmpty()) {
                query.put("end", end);
            }
            query.put("limit", String.valueOf(limit));
            JSONArray arr = new JSONArray(apiClient.get("/events", query));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                EventRecord event = new EventRecord();
                event.setId(o.optLong("id"));
                event.setElderId(o.optLong("elder_id"));
                event.setDeviceId(o.optLong("device_id"));
                event.setType(o.optString("type"));
                event.setSeverity(o.optString("severity"));
                event.setConfidence(o.isNull("confidence") ? null : o.optDouble("confidence"));
                event.setOccurredAt(o.optString("occurred_at"));
                event.setPayloadJson(o.isNull("payload_json") ? null : o.optString("payload_json"));
                event.setStatus(o.optString("status"));
                events.add(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "getEvents failed", e);
        }
        return events;
    }

    @Override
    public List<AlertRecord> getAlerts(int limit) {
        List<AlertRecord> alerts = new ArrayList<>();
        try {
            // 按文档建议：仅拉未处理告警（status=new）、limit=500；App 侧轮询/SOS 状态卡只消费 new
            Map<String, String> query = new HashMap<>();
            query.put("elder_id", String.valueOf(getElderId()));
            query.put("status", "new");
            query.put("limit", String.valueOf(Math.min(limit, 500)));
            JSONArray arr = new JSONArray(apiClient.get("/alerts", query));
            // SPEC 06 起 REST AlertOut 携带 elder_name；缓存仅做字段缺失兜底
            if (elderNameById.isEmpty()
                    || System.currentTimeMillis() - elderNameCacheAt > ELDER_NAME_CACHE_TTL_MS) {
                elderNameById = new HashMap<>();
                for (Elder elder : getElders()) {
                    elderNameById.put(elder.getId(), elder.getName());
                }
                elderNameCacheAt = System.currentTimeMillis();
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                AlertRecord alert = new AlertRecord();
                alert.setId(o.optLong("id"));
                alert.setEventId(o.optLong("event_id"));
                alert.setElderId(o.optLong("elder_id"));
                String elderName = o.optString("elder_name", null);
                alert.setElderName(elderName == null || elderName.isEmpty()
                        ? elderNameById.get(o.optLong("elder_id")) : elderName);
                alert.setStatus(o.optString("status"));
                alert.setCreatedAt(o.optString("created_at"));
                alert.setOccurredAt(o.optString("occurred_at"));
                alert.setHandledAt(o.isNull("handled_at") ? null : o.optString("handled_at"));
                alert.setHandledBy(o.isNull("handled_by") ? null : o.optLong("handled_by"));
                alert.setEventType(o.optString("event_type"));
                alert.setSeverity(o.optString("severity"));
                alerts.add(alert);
            }
        } catch (Exception e) {
            Log.e(TAG, "getAlerts failed", e);
        }
        return alerts;
    }

    @Override
    public boolean ackAlert(long alertId) {
        try {
            apiClient.post("/alerts/" + alertId + "/ack", null, true);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "ackAlert failed", e);
            return false;
        }
    }

    @Override
    public List<NotificationRecord> getNotifications(long alertId) {
        List<NotificationRecord> notifications = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(apiClient.get("/alerts/" + alertId + "/notifications", null));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NotificationRecord n = new NotificationRecord();
                n.setId(o.optLong("id"));
                n.setAlertId(o.optLong("alert_id"));
                n.setContactId(o.isNull("contact_id") ? null : o.optLong("contact_id"));
                n.setChannel(o.optString("channel"));
                n.setTarget(o.isNull("target") ? null : o.optString("target"));
                n.setContent(o.optString("content"));
                n.setStatus(o.optString("status"));
                n.setSentAt(o.isNull("sent_at") ? null : o.optString("sent_at"));
                notifications.add(n);
            }
        } catch (Exception e) {
            Log.e(TAG, "getNotifications failed", e);
        }
        return notifications;
    }

    /** 当前选中的老人 id（多老人切换，SPEC 06 family 可关联多名老人）；-1 表示未显式选择 */
    private long getSelectedElderId() {
        return session.getSelectedElderId();
    }

    /** 切换当前查看的老人：持久化选中 id 并失效本地缓存，后续请求立即生效 */
    public void selectElder(long elderId) {
        session.setSelectedElderId(elderId);
        cachedElderId = null;
        elderNameById = new HashMap<>();
        Log.i(TAG, "selected elder=" + elderId);
    }

    private long getElderId() throws ApiClient.ApiException, org.json.JSONException, java.io.IOException {
        long selected = getSelectedElderId();
        if (selected > 0) {
            return selected;
        }
        if (cachedElderId != null) {
            return cachedElderId;
        }
        List<Elder> elders = getElders();
        if (elders.isEmpty()) {
            throw new ApiClient.ApiException(404, "当前账号没有可见老人");
        }
        cachedElderId = elders.get(0).getId();
        return cachedElderId;
    }

    // ============ 老人管理（SPEC 06，family 可用） ============

    /** 新建老人档案：phone 必填（^1[3-9]\d{9}$）、全局唯一（重复 409 建议改用 add-by-phone）；成功返回新档案 */
    public Elder createElder(String name, String phone, String gender) throws Exception {
        JSONObject body = new JSONObject()
                .put("name", name)
                .put("phone", phone);
        if (gender != null && !gender.isEmpty()) {
            body.put("gender", gender);
        }
        JSONObject o = new JSONObject(apiClient.post("/elders", body.toString(), true));
        return parseElder(o);
    }

    /** 按手机号自助绑定已有老人档案（200 且自动建立关联；404 档案不存在、409 已绑定） */
    public Elder addElderByPhone(String phone) throws Exception {
        JSONObject body = new JSONObject().put("phone", phone);
        JSONObject o = new JSONObject(apiClient.post("/elders/add-by-phone", body.toString(), true));
        return parseElder(o);
    }

    /** 自助解绑当前账号与该老人的监护关系（仅 family；老人与历史数据保留） */
    public void unbindSelf(long elderId) throws Exception {
        apiClient.delete("/elders/" + elderId + "/access/me", true);
    }

    // ============ 紧急联系人（family 可读写） ============

    @Override
    public List<Contact> getContacts(long elderId) {
        List<Contact> contacts = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(apiClient.get("/elders/" + elderId + "/contacts", null));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Contact c = new Contact();
                c.setId(o.optLong("id"));
                c.setName(o.optString("name"));
                c.setPhone(o.optString("phone"));
                c.setRelation(o.isNull("relation") ? null : o.optString("relation"));
                c.setPriority(o.optInt("priority", 1));
                c.setPrimary(o.optBoolean("is_primary"));
                contacts.add(c);
            }
        } catch (Exception e) {
            Log.e(TAG, "getContacts failed", e);
        }
        return contacts;
    }

    /** 整体替换联系人（ContactIn[]）；返回保存后的完整列表 */
    @Override
    public List<Contact> saveContacts(long elderId, List<Contact> contacts) throws Exception {
        JSONArray arr = new JSONArray();
        for (Contact c : contacts) {
            JSONObject o = new JSONObject();
            o.put("name", c.getName());
            o.put("phone", c.getPhone());
            if (c.getRelation() != null && !c.getRelation().isEmpty()) {
                o.put("relation", c.getRelation());
            }
            o.put("priority", c.getPriority());
            o.put("is_primary", c.isPrimary());
            arr.put(o);
        }
        List<Contact> saved = new ArrayList<>();
        JSONArray resp = new JSONArray(
                apiClient.put("/elders/" + elderId + "/contacts", arr.toString(), true));
        for (int i = 0; i < resp.length(); i++) {
            JSONObject o = resp.getJSONObject(i);
            Contact c = new Contact();
            c.setId(o.optLong("id"));
            c.setName(o.optString("name"));
            c.setPhone(o.optString("phone"));
            c.setRelation(o.isNull("relation") ? null : o.optString("relation"));
            c.setPriority(o.optInt("priority", 1));
            c.setPrimary(o.optBoolean("is_primary"));
            saved.add(c);
        }
        return saved;
    }

    // ============ 绑定设备 / 电子围栏（family 只读） ============

    @Override
    public List<Device> getDevices(long elderId) {
        List<Device> devices = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(apiClient.get("/elders/" + elderId + "/devices", null));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Device d = new Device();
                d.setId(o.optLong("id"));
                d.setDeviceUid(o.optString("device_uid"));
                d.setKind(o.optString("kind"));
                d.setModel(o.isNull("model") ? null : o.optString("model"));
                d.setStatus(o.optString("status"));
                d.setElderId(o.isNull("elder_id") ? null : o.optLong("elder_id"));
                d.setBatteryPercent(o.isNull("battery_percent") ? null : o.optInt("battery_percent"));
                d.setLastSeenAt(o.isNull("last_seen_at") ? null : o.optString("last_seen_at"));
                d.setBoundAt(o.isNull("bound_at") ? null : o.optString("bound_at"));
                devices.add(d);
            }
        } catch (Exception e) {
            Log.e(TAG, "getDevices failed", e);
        }
        return devices;
    }

    @Override
    public List<Geofence> getGeofences(long elderId) {
        List<Geofence> fences = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(apiClient.get("/geofences/" + elderId, null));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Geofence g = new Geofence();
                g.setId(o.optLong("id"));
                g.setName(o.optString("name"));
                g.setCenterLat(o.optDouble("center_lat"));
                g.setCenterLon(o.optDouble("center_lon"));
                g.setRadiusM(o.optInt("radius_m"));
                g.setEnabled(o.optBoolean("enabled"));
                fences.add(g);
            }
        } catch (Exception e) {
            Log.e(TAG, "getGeofences failed", e);
        }
        return fences;
    }

    private Elder parseElder(JSONObject o) {
        Elder elder = new Elder();
        elder.setId(o.optLong("id"));
        elder.setName(o.optString("name"));
        elder.setGender(o.isNull("gender") ? null : o.optString("gender"));
        elder.setBirthDate(o.isNull("birth_date") ? null : o.optString("birth_date"));
        elder.setHeightCm(o.isNull("height_cm") ? null : o.optInt("height_cm"));
        elder.setWeightKg(o.isNull("weight_kg") ? null : o.optDouble("weight_kg"));
        elder.setAddress(o.isNull("address") ? null : o.optString("address"));
        elder.setNotes(o.isNull("notes") ? null : o.optString("notes"));
        elder.setPhone(o.isNull("phone") ? null : o.optString("phone"));
        return elder;
    }

    private TelemetryPoint parseTelemetry(JSONObject o) {
        TelemetryPoint point = new TelemetryPoint();
        point.setId(o.optLong("id"));
        point.setDeviceId(o.optLong("device_id"));
        point.setElderId(o.optLong("elder_id"));
        point.setMetric(o.optString("metric"));
        point.setValueNum(o.isNull("value_num") ? null : o.optDouble("value_num"));
        point.setValueText(o.isNull("value_text") ? null : o.optString("value_text"));
        point.setLat(o.isNull("lat") ? null : o.optDouble("lat"));
        point.setLon(o.isNull("lon") ? null : o.optDouble("lon"));
        point.setRecordedAt(o.optString("recorded_at"));
        return point;
    }

    private TrendBucket.MetricStats parseStats(JSONObject o) {
        return new TrendBucket.MetricStats(
                o.optDouble("avg"), o.optDouble("min"), o.optDouble("max"), o.optInt("count"));
    }
}
