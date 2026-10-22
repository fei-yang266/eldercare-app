package com.example.myapplication.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.TelemetryPoint;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket 实时推送（对齐 docs/app-integration.md 第 8 节）
 * - 地址 ws://host/api/v1/ws?token=<URL 编码>
 * - 连接成功先收 {"type":"ready"}，之后 alert / telemetry 消息
 * - 关闭码 4401/4403：会话失效，停止重连由上层回登录页
 * - 其他关闭：指数退避重连（1s/2s/4s…最大 30s），并通知上层启动 REST 轮询兜底
 */
public class WebSocketManager {

    private static final String TAG = "WebSocketManager";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build();

    private WebSocket webSocket;
    private Listener listener;
    private String token;
    private boolean connected;
    private boolean manualClosed;
    private int reconnectAttempt;

    public interface Listener {
        void onReady();

        void onAlert(AlertRecord alert);

        void onTelemetry(TelemetryPoint point);

        /** 4401/4403：令牌失效，停止重连，上层清理会话回登录页 */
        void onSessionInvalid();

        /** 其他断开：上层启动 30s REST 轮询兜底 */
        void onDisconnected();
    }

    public void connect(String token, Listener listener) {
        this.token = token;
        this.listener = listener;
        this.manualClosed = false;
        this.reconnectAttempt = 0;
        open();
    }

    private void open() {
        try {
            String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name());
            Request request = new Request.Builder()
                    .url(ApiConfig.WS_URL + "?token=" + encoded)
                    .build();
            webSocket = client.newWebSocket(request, new SocketListener());
        } catch (Exception e) {
            Log.e(TAG, "open failed", e);
            scheduleReconnect();
        }
    }

    public void disconnect() {
        manualClosed = true;
        reconnectAttempt = 0;
        if (webSocket != null) {
            webSocket.close(1000, "client logout");
            webSocket = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    public boolean isConnected() {
        return connected;
    }

    private void scheduleReconnect() {
        if (manualClosed || connected) {
            return;
        }
        long delay = Math.min(
                ApiConfig.WS_RECONNECT_BASE_DELAY_MS * (1L << reconnectAttempt),
                ApiConfig.WS_RECONNECT_MAX_DELAY_MS);
        reconnectAttempt++;
        Log.d(TAG, "reconnect in " + delay + "ms (attempt " + reconnectAttempt + ")");
        mainHandler.postDelayed(this::open, delay);
    }

    private class SocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            connected = true;
            reconnectAttempt = 0;
            Log.d(TAG, "ws opened");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject json = new JSONObject(text);
                String type = json.optString("type");
                switch (type) {
                    case "ready":
                        if (listener != null) {
                            listener.onReady();
                        }
                        break;
                    case "alert":
                        JSONObject alertJson = json.optJSONObject("alert");
                        if (alertJson != null && listener != null) {
                            listener.onAlert(parseAlert(alertJson));
                        }
                        break;
                    case "telemetry":
                        JSONObject telemetryJson = json.optJSONObject("telemetry");
                        if (telemetryJson != null && listener != null) {
                            listener.onTelemetry(parseTelemetry(telemetryJson));
                        }
                        break;
                    default:
                        Log.w(TAG, "unknown ws message type: " + type);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "ws message parse failed", e);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            onMessage(webSocket, bytes.utf8());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            connected = false;
            Log.d(TAG, "ws closed code=" + code + " reason=" + reason);
            handleClose(code);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            connected = false;
            Log.w(TAG, "ws failure", t);
            handleClose(response != null ? response.code() : 0);
        }

        private void handleClose(int code) {
            if (manualClosed) {
                return;
            }
            if (code == 4401 || code == 4403) {
                if (listener != null) {
                    listener.onSessionInvalid();
                }
                return;
            }
            if (listener != null) {
                listener.onDisconnected();
            }
            scheduleReconnect();
        }
    }

    private AlertRecord parseAlert(JSONObject json) {
        AlertRecord alert = new AlertRecord();
        alert.setId(json.optLong("id"));
        alert.setEventId(json.optLong("event_id"));
        alert.setElderId(json.optLong("elder_id"));
        alert.setElderName(json.optString("elder_name", null));
        alert.setEventType(json.optString("event_type"));
        alert.setSeverity(json.optString("severity"));
        alert.setOccurredAt(json.optString("occurred_at", null));
        alert.setStatus(json.optString("status"));
        return alert;
    }

    private TelemetryPoint parseTelemetry(JSONObject json) {
        TelemetryPoint point = new TelemetryPoint();
        point.setElderId(json.optLong("elder_id"));
        point.setMetric(json.optString("metric"));
        point.setValueNum(json.has("value_num") ? json.optDouble("value_num") : null);
        point.setValueText(json.isNull("value_text") ? null : json.optString("value_text"));
        point.setLat(json.isNull("lat") ? null : json.optDouble("lat"));
        point.setLon(json.isNull("lon") ? null : json.optDouble("lon"));
        point.setRecordedAt(json.optString("recorded_at"));
        return point;
    }
}
