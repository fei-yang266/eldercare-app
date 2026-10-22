package com.example.myapplication.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 时间工具：后端时间统一为带时区的 ISO 8601（如 2026-10-09T02:00:00Z 或 +08:00），
 * App 解析后转本地时区展示。SimpleDateFormat 非线程安全，均在方法内创建实例。
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /** 解析带时区 ISO 8601（兼容 Z / +00:00 / +08:00，兼容毫秒/微秒小数秒），失败返回 0 */
    public static long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        // 去掉小数秒（毫秒/微秒）：2026-10-10T06:35:00.123456Z -> 2026-10-10T06:35:00Z
        String normalized = iso;
        int dot = iso.indexOf('.');
        if (dot > 0) {
            int tzIdx = -1;
            for (int i = dot; i < iso.length(); i++) {
                char c = iso.charAt(i);
                if (c == 'Z' || c == '+' || c == '-') {
                    tzIdx = i;
                    break;
                }
            }
            normalized = tzIdx > dot
                    ? iso.substring(0, dot) + iso.substring(tzIdx)
                    : iso.substring(0, dot);
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            Date date = sdf.parse(normalized);
            return date != null ? date.getTime() : 0L;
        } catch (ParseException e) {
            // 无时区后缀的兜底（视为本地时间）
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date date = sdf.parse(normalized);
                return date != null ? date.getTime() : 0L;
            } catch (ParseException e2) {
                return 0L;
            }
        }
    }

    /** 毫秒时间戳 → 本地时区 "yyyy-MM-dd HH:mm:ss" */
    public static String formatMillis(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /** 毫秒时间戳 → 本地时区 "MM-dd HH:mm"（列表短格式） */
    public static String formatShortMillis(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /** 毫秒时间戳 → 本地时区 "HH:mm"（图表 X 轴刻度） */
    public static String formatHourMinute(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /** 毫秒时间戳 → 本地时区 "MM/dd HH:mm"（图表 X 轴刻度，带日期，与后端管理台一致） */
    public static String formatMonthDayHourMinute(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /** 毫秒时间戳 → 本地时区 "MM/dd"（按天聚合图表 X 轴刻度） */
    public static String formatMonthDay(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /** 毫秒时间戳 → 带时区 ISO 8601（与后端 parseIsoToMillis 互逆，如 2026-10-10T09:30:00+08:00） */
    public static String formatIso(long millis) {
        if (millis <= 0) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
}
