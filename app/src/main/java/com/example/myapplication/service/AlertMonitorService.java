package com.example.myapplication.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.utils.FallAlertManager;
import com.example.myapplication.utils.NotificationHelper;

/**
 * L1 前台服务：告警监控（WS + 10s REST 轮询）的常驻宿主。
 * - 登录后由 MainActivity 启动，App 退后台/锁屏/任务划掉后仍运行，保证告警可系统级通知
 * - 系统回收后 START_STICKY 重启，onStartCommand 里恢复监控
 * - 通知动作（确认/忽略）通过本服务的 onStartCommand 分发处理：
 *   确认 → POST /alerts/{id}/ack + 写已确认集合（SOS 页联动） + 取消通知
 */
public class AlertMonitorService extends Service {

    private static final String TAG = "AlertMonitorService";

    /** 前台常驻通知 id */
    private static final int ONGOING_NOTIFICATION_ID = 1001;

    public static final String ACTION_CONFIRM = "com.example.myapplication.action.CONFIRM_ALERT";
    public static final String ACTION_DISMISS = "com.example.myapplication.action.DISMISS_ALERT";
    public static final String EXTRA_ALERT_ID = "alert_id";
    public static final String EXTRA_EVENT_ID = "event_id";

    /** 用户主动停止（退出登录/会话失效）：置标志，onDestroy 不再自重启 */
    private static volatile boolean userStopped = false;

    /** 启动前台服务（Android 8+ 需 startForegroundService） */
    public static void start(Context context) {
        userStopped = false;
        Intent intent = new Intent(context, AlertMonitorService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    /** 停止前台服务（退出登录 / 会话失效时调用；退后台不清除） */
    public static void stop(Context context) {
        userStopped = true;
        context.stopService(new Intent(context, AlertMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
        Log.d(TAG, "service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONFIRM.equals(intent.getAction())) {
            handleConfirm(intent);
            return START_STICKY;
        }
        if (intent != null && ACTION_DISMISS.equals(intent.getAction())) {
            long alertId = intent.getLongExtra(EXTRA_ALERT_ID, 0L);
            NotificationHelper.cancelEmergency(this, alertId);
            Log.i(TAG, "dismiss alertId=" + alertId);
            return START_STICKY;
        }

        startForeground();
        // 恢复/确保告警监控运行（幂等；WS 断线重连 + 10s REST 轮询兜底都在 FallAlertManager 内）
        FallAlertManager.getInstance(this).startCheck();
        Log.d(TAG, "monitor started");
        return START_STICKY;
    }

    /** 前台常驻通知：启动前台服务（API 29+ 指定 dataSync 类型，适配 Android 14+ 前台服务类型要求） */
    private void startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_NOTIFICATION_ID,
                    NotificationHelper.ongoingNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ONGOING_NOTIFICATION_ID,
                    NotificationHelper.ongoingNotification(this));
        }
    }

    /** 用户从任务列表划掉 App：系统会移除前台通知，重新挂上常驻通知保持"监控中"可见 */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        startForeground();
        Log.d(TAG, "task removed, foreground notification re-attached");
    }

    /** 确认动作：后台线程 ack + 写已确认集合 + 取消通知（不弹 Toast，避免后台无意义提示） */
    private void handleConfirm(Intent intent) {
        final long alertId = intent.getLongExtra(EXTRA_ALERT_ID, 0L);
        final long eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L);
        new Thread(() -> {
            try {
                if (alertId > 0) {
                    boolean ok = HealthDataService.getInstance(this).ackAlert(alertId);
                    Log.i(TAG, "ack alertId=" + alertId + " -> " + ok);
                }
                if (eventId > 0) {
                    FallAlertManager.getInstance(this).acknowledgeEvent(eventId);
                }
            } catch (Exception e) {
                Log.e(TAG, "ack failed", e);
            } finally {
                NotificationHelper.cancelEmergency(this, alertId);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        // 停止监控（WS 断开 + 轮询取消）；会话失效/退出登录路径会先调 stopPush，这里兜底保证不空转
        FallAlertManager.getInstance(this).stopCheck();
        super.onDestroy();
        Log.d(TAG, "service destroyed");
        // 防国产 ROM 后台清理：非用户主动停止时延迟 2s 自重启，保证告警监控常驻；
        // 用户退出登录走 AlertMonitorService.stop()（置 userStopped），不会循环重启
        if (!userStopped) {
            Log.w(TAG, "service destroyed unexpectedly, restarting");
            getMainHandler().postDelayed(() -> {
                if (!userStopped) {
                    start(this);
                }
            }, 2000L);
        }
    }

    private final android.os.Handler restartHandler = new android.os.Handler(
            android.os.Looper.getMainLooper());

    private android.os.Handler getMainHandler() {
        return restartHandler;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
