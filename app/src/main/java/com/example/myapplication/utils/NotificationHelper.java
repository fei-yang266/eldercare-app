package com.example.myapplication.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.EventRecord;
import com.example.myapplication.service.AlertMonitorService;

/**
 * 系统通知助手（L1：告警后台可达，对应 app-integration 告警链路）
 * - emergency 渠道：高优先级（heads-up 横幅 + 声音 + 震动 + 锁屏可见）→ 紧急告警
 * - ongoing 渠道：低优先级常驻通知 → 前台服务监控状态
 * - 告警通知带「确认 / 忽略」动作：确认联动 ack（POST /alerts/{id}/ack）并写已确认集合
 * - 通知 id 与告警 id 一一对应（负数本地告警转正），去重与弹窗共用 FallAlertManager 集合
 */
public final class NotificationHelper {

    public static final String CHANNEL_EMERGENCY = "emergency";
    public static final String CHANNEL_ONGOING = "monitor";

    private NotificationHelper() {
    }

    /** 幂等创建通知渠道（Android 8+ 生效；低版本自动忽略） */
    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        NotificationChannel emergency = new NotificationChannel(
                CHANNEL_EMERGENCY,
                context.getString(R.string.notification_channel_emergency),
                NotificationManager.IMPORTANCE_HIGH);
        emergency.setDescription(context.getString(R.string.notification_channel_emergency));
        emergency.enableVibration(true);
        emergency.setShowBadge(true);
        nm.createNotificationChannel(emergency);

        NotificationChannel ongoing = new NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.notification_channel_ongoing),
                NotificationManager.IMPORTANCE_LOW);
        ongoing.setShowBadge(false);
        nm.createNotificationChannel(ongoing);
    }

    /** 前台服务常驻通知（低优先级、不可划掉） */
    public static Notification ongoingNotification(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(context.getString(R.string.notification_ongoing_title))
                .setContentText(context.getString(R.string.notification_ongoing_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /** 紧急告警通知：高优先级横幅 + 确认/忽略动作；点击打开主界面 */
    public static void notifyEmergency(Context context, AlertRecord alert) {
        if (context == null || alert == null) {
            return;
        }
        ensureChannels(context);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (!nm.areNotificationsEnabled()) {
            // 用户在系统设置关闭了通知：静默降级（弹窗仍在）
            return;
        }
        // Android 13+ 运行时权限：未授予不发通知（lint MissingPermission 显式检查）
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String title = emergencyTitle(context, alert);
        String elderName = alert.getElderName();
        long time = alert.getOccurredTimeMillis() > 0
                ? alert.getOccurredTimeMillis() : alert.getCreatedTimeMillis();
        String text = context.getString(R.string.notification_alert_text,
                elderName == null || elderName.isEmpty()
                        ? context.getString(R.string.placeholder_dash) : elderName,
                time > 0 ? TimeUtils.formatMillis(time) : "");

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 确认：联动后端 ack + 写入已确认集合 + 取消通知
        Intent confirm = new Intent(context, AlertMonitorService.class)
                .setAction(AlertMonitorService.ACTION_CONFIRM)
                .putExtra(AlertMonitorService.EXTRA_ALERT_ID, alert.getId())
                .putExtra(AlertMonitorService.EXTRA_EVENT_ID, alert.getEventId());
        PendingIntent confirmIntent = PendingIntent.getService(context, notificationId(alert.getId()), confirm,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 忽略：仅取消通知（不 ack，后端仍为 new，轮询不会重复弹——去重集合已写入）
        Intent dismiss = new Intent(context, AlertMonitorService.class)
                .setAction(AlertMonitorService.ACTION_DISMISS)
                .putExtra(AlertMonitorService.EXTRA_ALERT_ID, alert.getId());
        PendingIntent dismissIntent = PendingIntent.getService(context, notificationId(alert.getId()) + 10000, dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
                .setSmallIcon(R.drawable.ic_sos_active)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(R.drawable.ic_sos_active,
                        context.getString(R.string.notification_action_confirm), confirmIntent)
                .addAction(0,
                        context.getString(R.string.notification_action_dismiss), dismissIntent)
                .build();

        nm.notify(notificationId(alert.getId()), notification);
    }

    /** 取消指定告警的通知（确认/忽略后调用） */
    public static void cancelEmergency(Context context, long alertId) {
        NotificationManagerCompat.from(context).cancel(notificationId(alertId));
    }

    /** 通知 id：告警 id 转非负 int（本地负 id 与后端正 id 均唯一映射，避免冲突） */
    private static int notificationId(long alertId) {
        return (int) (alertId & 0x7fffffffL);
    }

    private static String emergencyTitle(Context context, AlertRecord alert) {
        String type = alert.getEventType();
        if (EventRecord.TYPE_FALL.equals(type)) {
            return context.getString(R.string.notification_alert_fall);
        }
        if (EventRecord.TYPE_SOS.equals(type)) {
            return context.getString(R.string.notification_alert_sos);
        }
        if ("health_anomaly".equals(type)) {
            return context.getString(R.string.notification_alert_anomaly);
        }
        return context.getString(R.string.notification_alert_emergency);
    }
}
