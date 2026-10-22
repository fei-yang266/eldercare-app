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
import com.example.myapplication.model.TokenResponse;
import com.example.myapplication.model.TrendBucket;
import com.example.myapplication.utils.TimeUtils;

import org.json.JSONObject;

import java.util.List;

/**
 * 数据访问门面（对齐 docs/app-integration.md）
 * - 统一数据源：生产后端已部署，默认 RemoteApiSource；演示模式（Mock）由登录页"演示模式"入口显式开启
 * - 认证：login 保存 Token（expires_in 计算过期），logout 本地清除 + 关闭推送
 * - 实时推送：Mock 模式模拟推送；Remote 模式走 WebSocket（断线指数退避 + 上层轮询兜底）
 */
public class HealthDataService {

    private static final String TAG = "HealthDataService";
    private static final int DEFAULT_LIMIT = 50;

    private static HealthDataService instance;

    private final Context context;
    private final SessionManager session;
    private final MockApiSource mockSource;
    private final RemoteApiSource remoteSource;

    private boolean mockMode = false;
    private PushListener pushListener;
    private WebSocketManager webSocketManager;
    private SessionInvalidListener sessionInvalidListener;
    /** WS 连接成功后上层需立即全量刷新（文档约定：收到 ready 先全量刷新一次，补齐断线期间数据） */
    private Runnable onWsReadyListener;

    public interface PushListener {
        void onAlert(AlertRecord alert);

        void onTelemetry(TelemetryPoint point);
    }

    /** 会话失效（401 / WS 4401/4403）：由 MainActivity 跳回登录页 */
    public interface SessionInvalidListener {
        void onSessionInvalid();
    }

    private HealthDataService(Context context) {
        this.context = context.getApplicationContext();
        this.session = SessionManager.getInstance(context);
        this.mockSource = MockApiSource.getInstance();
        this.remoteSource = new RemoteApiSource(this.context);
        this.remoteSource.getApiClient().setUnauthorizedListener(this::handleUnauthorized);
    }

    public static synchronized HealthDataService getInstance(Context context) {
        if (instance == null) {
            instance = new HealthDataService(context);
        }
        return instance;
    }

    public void setMockMode(boolean mockMode) {
        if (this.mockMode == mockMode) {
            return;
        }
        this.mockMode = mockMode;
        Log.d(TAG, "mockMode=" + mockMode);
        if (!mockMode) {
            stopPush();
        }
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public void setSessionInvalidListener(SessionInvalidListener listener) {
        this.sessionInvalidListener = listener;
    }

    /** 注册 WS ready 回调（WS 连接成功后由 FallAlertManager 立即补拉一次全量数据） */
    public void setOnWsReadyListener(Runnable listener) {
        this.onWsReadyListener = listener;
    }

    public SessionManager getSession() {
        return session;
    }

    public ApiClient getApiClient() {
        return remoteSource.getApiClient();
    }

    // ============ 认证 ============

    /** 注册家属账号：公开接口、账号落后端库，固定 family 角色；成功后再调 login 获取 Token */
    public JSONObject register(String username, String displayName, String phone, String password)
            throws Exception {
        return remoteSource.getApiClient().register(username, displayName, phone, password);
    }

    /** 登录：Mock 模式直接建立演示会话；Remote 模式调真实接口 */
    public TokenResponse login(String username, String password) throws Exception {
        if (mockMode) {
            session.saveMockSession(username);
            return new TokenResponse("mock-token", "bearer", 24 * 3600L, "family");
        }
        TokenResponse token = remoteSource.getApiClient().login(username, password);
        session.saveSession(token.getAccessToken(), username, token.getRole(), token.getExpiresIn());
        return token;
    }

    /** 退出登录：服务端无 logout 接口，本地清除会话并关闭推送 */
    public void logout() {
        stopPush();
        session.clear();
    }

    public boolean isLoggedIn() {
        return session.isLoggedIn();
    }

    private void handleUnauthorized() {
        stopPush();
        if (sessionInvalidListener != null) {
            sessionInvalidListener.onSessionInvalid();
        }
    }

    // ============ 老人 ============

    public List<Elder> getElders() {
        return mockMode ? mockSource.getElders() : remoteSource.getElders();
    }

    /** 切换当前查看的老人（多老人切换）；切换后各页按各自刷新节奏拉到新老人数据 */
    public void selectElder(long elderId) {
        if (!mockMode) {
            remoteSource.selectElder(elderId);
        }
        session.setSelectedElderId(elderId);
    }

    /** 当前选中老人 id（-1 表示未选择，回退到第一个可见老人） */
    public long getSelectedElderId() {
        return session.getSelectedElderId();
    }

    /** 解析实际生效的老人 id：优先选中项，未选中/失效时取第一个可见老人；无老人返回 -1 */
    public long getResolvedElderId() {
        long selected = session.getSelectedElderId();
        if (selected > 0) {
            return selected;
        }
        List<Elder> elders = getElders();
        return elders.isEmpty() ? -1L : elders.get(0).getId();
    }

    // ============ SPEC 06：老人管理 / 联系人 / 设备 / 围栏 ============

    /** 新建老人档案（phone 必填） */
    public Elder createElder(String name, String phone, String gender) throws Exception {
        return mockMode
                ? mockSource.getElders().get(0)
                : remoteSource.createElder(name, phone, gender);
    }

    /** 按手机号自助绑定已有老人档案 */
    public Elder addElderByPhone(String phone) throws Exception {
        return mockMode
                ? mockSource.getElders().get(0)
                : remoteSource.addElderByPhone(phone);
    }

    /** 自助解绑当前账号与该老人的监护关系 */
    public void unbindSelf(long elderId) throws Exception {
        if (!mockMode) {
            remoteSource.unbindSelf(elderId);
        }
        if (session.getSelectedElderId() == elderId) {
            session.resetSelectedElderId();
        }
    }

    public List<Contact> getContacts(long elderId) {
        return mockMode
                ? mockSource.getContacts(elderId)
                : remoteSource.getContacts(elderId);
    }

    public List<Contact> saveContacts(long elderId, List<Contact> contacts) throws Exception {
        return mockMode
                ? mockSource.saveContacts(elderId, contacts)
                : remoteSource.saveContacts(elderId, contacts);
    }

    public List<Device> getDevices(long elderId) {
        return mockMode
                ? mockSource.getDevices(elderId)
                : remoteSource.getDevices(elderId);
    }

    public List<Geofence> getGeofences(long elderId) {
        return mockMode
                ? mockSource.getGeofences(elderId)
                : remoteSource.getGeofences(elderId);
    }

    // ============ 遥测与趋势 ============

    public List<TelemetryPoint> getLatestTelemetry() {
        return mockMode
                ? mockSource.getLatestTelemetry(DEFAULT_LIMIT)
                : remoteSource.getLatestTelemetry(DEFAULT_LIMIT);
    }

    public List<TrendBucket> getHealthTrends(String interval, int limit) {
        return mockMode
                ? mockSource.getHealthTrends(interval, limit)
                : remoteSource.getHealthTrends(interval, limit);
    }

    /** 健康趋势（按时间窗口过滤：startMillis/endMillis 为毫秒时间戳，App 内转带时区 ISO 传给后端；窗口外无数据时返回空列表） */
    public List<TrendBucket> getHealthTrends(String interval, int limit, long startMillis, long endMillis) {
        String start = startMillis > 0 ? TimeUtils.formatIso(startMillis) : null;
        String end = endMillis > 0 ? TimeUtils.formatIso(endMillis) : null;
        return mockMode
                ? mockSource.getHealthTrends(interval, limit, start, end)
                : remoteSource.getHealthTrends(interval, limit, start, end);
    }

    // ============ 事件 / 告警 / 通知 ============

    public List<EventRecord> getEvents(String type) {
        return mockMode
                ? mockSource.getEvents(type, DEFAULT_LIMIT)
                : remoteSource.getEvents(type, DEFAULT_LIMIT);
    }

    /** 事件列表（按日期范围过滤：start/end 为毫秒时间戳，App 内转带时区 ISO 传给后端） */
    public List<EventRecord> getEvents(String type, long startMillis, long endMillis, int limit) {
        String start = startMillis > 0 ? TimeUtils.formatIso(startMillis) : null;
        String end = endMillis > 0 ? TimeUtils.formatIso(endMillis) : null;
        return mockMode
                ? mockSource.getEvents(type, start, end, limit)
                : remoteSource.getEvents(type, start, end, limit);
    }

    public List<AlertRecord> getAlerts() {
        return mockMode ? mockSource.getAlerts(DEFAULT_LIMIT) : remoteSource.getAlerts(DEFAULT_LIMIT);
    }

    /** 确认告警（幂等），返回是否成功 */
    public boolean ackAlert(long alertId) {
        return mockMode ? mockSource.ackAlert(alertId) : remoteSource.ackAlert(alertId);
    }

    public List<NotificationRecord> getNotifications(long alertId) {
        return mockMode
                ? mockSource.getNotifications(alertId)
                : remoteSource.getNotifications(alertId);
    }

    // ============ 实时推送 ============

    /** 启动实时推送：Mock 模拟推送；Remote 连接 WebSocket */
    public void startPush(PushListener listener) {
        this.pushListener = listener;
        if (mockMode) {
            mockSource.startPush(listener);
            return;
        }
        String token = session.getToken();
        if (token == null) {
            return;
        }
        webSocketManager = new WebSocketManager();
        webSocketManager.connect(token, new WebSocketManager.Listener() {
            @Override
            public void onReady() {
                Log.d(TAG, "ws ready, trigger full refresh");
                if (onWsReadyListener != null) {
                    onWsReadyListener.run();
                }
            }

            @Override
            public void onAlert(AlertRecord alert) {
                if (pushListener != null) {
                    pushListener.onAlert(alert);
                }
            }

            @Override
            public void onTelemetry(TelemetryPoint point) {
                if (pushListener != null) {
                    pushListener.onTelemetry(point);
                }
            }

            @Override
            public void onSessionInvalid() {
                handleUnauthorized();
            }

            @Override
            public void onDisconnected() {
                // 断线期间由 FallAlertManager 的 30s REST 轮询兜底
                Log.d(TAG, "ws disconnected, polling fallback active");
            }
        });
    }

    public void stopPush() {
        if (mockMode) {
            mockSource.stopPush();
        } else if (webSocketManager != null) {
            webSocketManager.disconnect();
            webSocketManager = null;
        }
        pushListener = null;
    }
}
