package com.example.myapplication.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.AlertRecord;
import com.example.myapplication.model.Elder;
import com.example.myapplication.model.TelemetryPoint;
import com.example.myapplication.model.TrendBucket;
import com.example.myapplication.utils.FallAlertManager;
import com.example.myapplication.utils.ElderPicker;
import com.example.myapplication.utils.HealthChartManager;
import com.example.myapplication.utils.TimeUtils;
import com.example.myapplication.view.HealthChartView;

import java.util.List;
import java.util.Locale;

/**
 * 实时监测页（对齐 docs/app-integration.md 遥测接口）
 * - 展示最新遥测：心率 / 血氧 / 步数（后端指标枚举无体温，温度卡已移除）
 * - 设备信息卡已删除：family 角色无设备接口
 * - 数据源徽章标识"演示数据 / 实时连接"
 * - 定时刷新与图表、告警管理统一由一个 3s 循环驱动，切页隐藏时自动暂停
 * - 健康异常本地检测：心率/血氧越界时先轻提醒；连续 3 个采样点（约 6~9 秒）仍越界
 *   → 生成本地"健康异常"紧急告警并弹窗（与后端跌倒/SOS 同一弹窗链路），确认后不再打扰
 */
public class RealTimeFragment extends Fragment {

    private static final long UPDATE_INTERVAL = 3_000L;
    /** 距最新遥测超过该时长视为设备离线（设备约每 30s 上报一次，3 分钟无新数据即离线） */
    private static final long OFFLINE_AFTER_MS = 180_000L;
    /** 页面隐藏时降频刷新（保持最新数据，兼顾省电） */
    private static final long BACKGROUND_UPDATE_INTERVAL = 10_000L;
    /** UI 展示口径：心率正常区间 60-100 bpm、血氧不低于 95% */
    private static final int HEART_RATE_MIN = 60;
    private static final int HEART_RATE_MAX = 100;
    private static final int SPO2_MIN = 95;
    /** 连续越界采样点达到该次数（约 6~9 秒）升级为紧急弹窗 */
    private static final int ABNORMAL_STREAK_THRESHOLD = 3;
    /** 趋势图（health-trends 按小时聚合）刷新间隔：30s，避免每 3s 打一次聚合接口 */
    private static final long TRENDS_REFRESH_INTERVAL_MS = 30_000L;
    private static final String TAG = "RealTimeFragment";

    private TextView elderNameText;
    private TextView dataSourceText;
    private TextView statusText;
    private TextView heartRateText;
    private TextView heartRateBadge;
    private TextView bloodOxygenText;
    private TextView bloodOxygenBadge;
    private TextView stepsText;
    private TextView stepsBadge;

    private HealthChartManager chartManager;
    private FallAlertManager fallAlertManager;
    private Handler mainHandler;
    private boolean updating;

    /** 连续越界采样点计数（心率或血氧任一越界 +1，恢复正常清零） */
    private int abnormalStreak;
    /** 本次异常段是否已轻提醒（恢复正常后重置，避免反复 Toast） */
    private boolean streakReminderShown;
    /** 已加载的老人名缓存（供本地健康异常告警展示，避免主线程网络请求） */
    private String elderNameCache;
    /** 当前显示的选中老人 id：onResume 检测"我的"页切换，变化时刷新名字并清空趋势图 */
    private long lastShownElderId = -1;
    /** 趋势图上次拉取时间（health-trends 聚合接口节流） */
    private long lastTrendsAt;
    /** 本次异常段内是否已发本地"健康异常"通知（恢复后重置，防止持续越界轰炸） */
    private boolean localAlertSent;
    /** 近 24 小时累计步数（来自 health-trends 各桶 steps_total 求和）；-1 表示尚未拉到 */
    private int totalSteps = -1;
    /** 趋势图时间范围：0=近24小时(小时聚合24桶) / 1=近7天(小时聚合168桶) / 2=近30天(天聚合30桶) */
    private int trendsRange = 0;

    @Override
    public void onResume() {
        super.onResume();
        syncSelectedElderIfChanged();
    }

    /**
     * "我的"页可能切换了选中老人（selected_elder_id 持久化）；本页老人名是缓存的一次性加载，
     * 必须主动检测变化：名字不同步会导致"切了王爷爷实时页还显示张奶奶"，
     * 本地健康异常通知也会沿用旧老人名（elderNameCache）
     */
    private void syncSelectedElderIfChanged() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        long selected = service.getSelectedElderId();
        if (selected != lastShownElderId) {
            lastShownElderId = selected;
            loadElderName(service);
            if (chartManager != null) {
                chartManager.clearChart();
            }
            totalSteps = -1; // 切老人后累计步数待新老人聚合返回再填
            lastTrendsAt = 0; // 切换老人后强制下轮拉新老人的聚合趋势
            updateRealtimeData();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_realtime, container, false);

        initViews(view);
        initChart(view);
        initStaticInfo();
        startUpdates();

        return view;
    }

    private void initViews(View view) {
        elderNameText = view.findViewById(R.id.elder_name_text);
        // 老人名可点击：弹出老人选择列表（多老人切换 / 添加老人）
        elderNameText.setOnClickListener(v -> ElderPicker.showPicker(this,
                elder -> {
                    elderNameCache = elder.getName();
                    elderNameText.setText(elderNameCache);
                    lastShownElderId = elder.getId();
                    if (chartManager != null) {
                        chartManager.clearChart();
                    }
                    totalSteps = -1;
                    lastTrendsAt = 0; // 切换老人后强制下轮拉新老人的聚合趋势
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_elder_switched, elderNameCache),
                            Toast.LENGTH_SHORT).show();
                    // 切换后立即刷新本页数据；历史/SOS 页由各自自动刷新跟随
                    updateRealtimeData();
                }));
        dataSourceText = view.findViewById(R.id.data_source_text);
        statusText = view.findViewById(R.id.status_text);
        heartRateText = view.findViewById(R.id.heart_rate_text);
        heartRateBadge = view.findViewById(R.id.heart_rate_badge);
        bloodOxygenText = view.findViewById(R.id.blood_oxygen_text);
        bloodOxygenBadge = view.findViewById(R.id.blood_oxygen_badge);
        stepsText = view.findViewById(R.id.steps_text);
        stepsBadge = view.findViewById(R.id.steps_badge);

        // 趋势图时间范围切换：近24小时 / 近7天 / 近30天（与后端管理台一致）
        TextView range24h = view.findViewById(R.id.range_24h);
        TextView range7d = view.findViewById(R.id.range_7d);
        TextView range30d = view.findViewById(R.id.range_30d);
        range24h.setOnClickListener(v -> selectTrendsRange(0, range24h, range7d, range30d));
        range7d.setOnClickListener(v -> selectTrendsRange(1, range24h, range7d, range30d));
        range30d.setOnClickListener(v -> selectTrendsRange(2, range24h, range7d, range30d));
        updateRangeBadges();
    }

    /** 切换趋势时间范围：更新选中样式并强制重拉聚合（lastTrendsAt=0） */
    private void selectTrendsRange(int range, TextView range24h, TextView range7d, TextView range30d) {
        if (trendsRange == range) {
            return;
        }
        trendsRange = range;
        updateRangeBadges();
        lastTrendsAt = 0;
        updateRealtimeData();
    }

    /** 按当前选中范围刷新三个按钮的选中样式（青绿实底=选中，浅色=未选） */
    private void updateRangeBadges() {
        View root = getView();
        if (root == null) {
            return;
        }
        TextView range24h = root.findViewById(R.id.range_24h);
        TextView range7d = root.findViewById(R.id.range_7d);
        TextView range30d = root.findViewById(R.id.range_30d);
        int colorSelected = getResources().getColor(R.color.white, null);
        int colorUnselected = getResources().getColor(R.color.text_secondary, null);
        range24h.setBackgroundResource(trendsRange == 0
                ? R.drawable.bg_badge_normal : R.drawable.bg_badge_light);
        range24h.setTextColor(trendsRange == 0 ? colorSelected : colorUnselected);
        range24h.setTypeface(null, trendsRange == 0 ? Typeface.BOLD : Typeface.NORMAL);
        range7d.setBackgroundResource(trendsRange == 1
                ? R.drawable.bg_badge_normal : R.drawable.bg_badge_light);
        range7d.setTextColor(trendsRange == 1 ? colorSelected : colorUnselected);
        range7d.setTypeface(null, trendsRange == 1 ? Typeface.BOLD : Typeface.NORMAL);
        range30d.setBackgroundResource(trendsRange == 2
                ? R.drawable.bg_badge_normal : R.drawable.bg_badge_light);
        range30d.setTextColor(trendsRange == 2 ? colorSelected : colorUnselected);
        range30d.setTypeface(null, trendsRange == 2 ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void initChart(View view) {
        HealthChartView chartView = view.findViewById(R.id.health_chart_view);
        chartManager = new HealthChartManager(chartView);

        fallAlertManager = FallAlertManager.getInstance(requireContext());
        fallAlertManager.startCheck();
    }

    /** 一次性静态信息：老人名（异步加载，避免主线程网络请求被静默吞掉导致"--"）与数据源徽章 */
    private void initStaticInfo() {
        HealthDataService service = HealthDataService.getInstance(requireContext());
        boolean mock = service.isMockMode();
        dataSourceText.setText(mock
                ? R.string.data_source_mock : R.string.data_source_online);
        loadElderName(service);
    }

    /** 异步加载当前查看老人名到顶部（首次或为空时执行；多老人时按选中 id 取） */
    private void loadElderName(HealthDataService service) {
        new Thread(() -> {
            try {
                List<Elder> elders = service.getElders();
                Elder display = displayElder(elders, service.getSelectedElderId());
                // initStaticInfo 先于 startUpdates 执行，此时 mainHandler 尚未创建，自建主线程 Handler
                Handler h = mainHandler != null ? mainHandler : new Handler(Looper.getMainLooper());
                h.post(() -> {
                        if (isAdded() && elderNameText != null && display != null) {
                            elderNameCache = display.getName();
                            elderNameText.setText(elderNameCache);
                            lastShownElderId = display.getId();
                        }
                    });
            } catch (Exception e) {
                // 网络异常时保持"--"，下一轮刷新重试
            }
        }).start();
    }

    /** 从老人列表取当前查看对象：优先选中 id 匹配，未选中/失效时取第一个 */
    private static Elder displayElder(List<Elder> elders, long selectedId) {
        if (elders == null || elders.isEmpty()) {
            return null;
        }
        for (Elder e : elders) {
            if (e.getId() == selectedId) {
                return e;
            }
        }
        return elders.get(0);
    }

    private void startUpdates() {
        startUpdates(UPDATE_INTERVAL);
    }

    private void startUpdates(long interval) {
        if (updating) {
            return;
        }
        updating = true;
        mainHandler = new Handler(Looper.getMainLooper());
        updateInterval = interval;
        mainHandler.postDelayed(updateRunnable, 0);
    }

    private long updateInterval = UPDATE_INTERVAL;

    private void stopUpdates() {
        updating = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateRealtimeData();
            if (updating) {
                mainHandler.postDelayed(this, updateInterval);
            }
        }
    };

    /** 遥测刷新：网络请求放子线程（主线程同步请求会被静默吞掉导致页面无数据），回调切回主线程更新 UI。
     *  指标卡用实时遥测；趋势图用后端 health-trends 按小时聚合（30s 节流），与后端管理台一致 */
    private void updateRealtimeData() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        // 顶部老人名仍为占位"--"时，本轮顺带补拉（成功后不再请求）
        boolean needElder = elderNameText != null
                && elderNameText.getText() != null
                && elderNameText.getText().toString().equals(getString(R.string.placeholder_dash));
        boolean needTrends = System.currentTimeMillis() - lastTrendsAt >= TRENDS_REFRESH_INTERVAL_MS;
        if (needTrends) {
            lastTrendsAt = System.currentTimeMillis();
        }
        new Thread(() -> {
            List<TelemetryPoint> telemetry = service.getLatestTelemetry();
            List<TrendBucket> trends = needTrends ? fetchTrends(service) : null;
            List<Elder> elders = needElder ? service.getElders() : null;
            Handler h = mainHandler;
            if (h != null) {
                h.post(() -> {
                    if (!isAdded() || getContext() == null) {
                        return;
                    }
                    if (elders != null && elderNameText != null) {
                        Elder display = displayElder(elders, service.getSelectedElderId());
                        if (display != null) {
                            elderNameCache = display.getName();
                            elderNameText.setText(elderNameCache);
                        }
                    }
                    if (trends != null && chartManager != null) {
                        if (trends.isEmpty()) {
                            Log.i(TAG, "[TRENDS] empty: elderId=" + service.getSelectedElderId());
                        } else {
                            Log.i(TAG, "[TRENDS] buckets=" + trends.size()
                                    + " first=" + trends.get(0).getBucketStart()
                                    + " spo2=" + (trends.get(0).getSpo2() == null
                                    ? "null" : String.valueOf((int) Math.round(trends.get(0).getSpo2().getAvg()))));
                        }
                        chartManager.setTrendData(trends, trendsRange == 2);
                        // 步数卡：按小时聚合桶的 steps_total 求和（累计语义，与趋势柱/管理台一致）
                        int sum = 0;
                        for (TrendBucket b : trends) {
                            if (b.getStepsTotal() != null) {
                                sum += b.getStepsTotal().intValue();
                            }
                        }
                        totalSteps = sum;
                        updateMetricCard(stepsText, stepsBadge, totalSteps, null, null);
                    }
                    applyTelemetry(telemetry);
                });
            }
        }).start();
    }

    /** 按当前时间范围请求聚合趋势（带时间窗口：start/end 过滤，设备离线窗口内无数据时后端返回空列表） */
    private List<TrendBucket> fetchTrends(HealthDataService service) {
        long now = System.currentTimeMillis();
        long day = 24 * 3600_000L;
        switch (trendsRange) {
            case 1:
                return service.getHealthTrends("hour", 168, now - 7 * day, now);
            case 2:
                return service.getHealthTrends("day", 30, now - 30 * day, now);
            default:
                return service.getHealthTrends("hour", 24, now - day, now);
        }
    }

    /** 主线程更新 UI：指标卡 / 状态 / 健康异常检测（趋势图由 health-trends 聚合驱动，见 updateRealtimeData） */
    private void applyTelemetry(List<TelemetryPoint> telemetry) {
        if (getContext() == null) {
            return;
        }
        try {
            // 设备离线判断：列表为空或最新遥测距今超过阈值（3 分钟）→ 实时卡不显示旧数据
            boolean offline = telemetry == null || telemetry.isEmpty()
                    || System.currentTimeMillis() - latestRecordedTime(telemetry) > OFFLINE_AFTER_MS;
            if (offline) {
                updateMetricCard(heartRateText, heartRateBadge, null, null, null);
                updateMetricCard(bloodOxygenText, bloodOxygenBadge, null, null, null);
                if (heartRateBadge != null) {
                    heartRateBadge.setText(R.string.metric_status_offline);
                    heartRateBadge.setBackgroundResource(R.drawable.bg_badge_notice);
                }
                if (bloodOxygenBadge != null) {
                    bloodOxygenBadge.setText(R.string.metric_status_offline);
                    bloodOxygenBadge.setBackgroundResource(R.drawable.bg_badge_notice);
                }
                // 步数卡为累计语义，保留已拉到的近 24h 累计；未拉到则保持 "--"
                if (totalSteps >= 0) {
                    updateMetricCard(stepsText, stepsBadge, totalSteps, null, null);
                }
                updateStatusOffline();
                return;
            }

            Integer heartRate = latestValue(telemetry, "heart_rate");
            Integer spo2 = latestValue(telemetry, "spo2");

            updateMetricCard(heartRateText, heartRateBadge, heartRate,
                    HEART_RATE_MIN, HEART_RATE_MAX);
            updateMetricCard(bloodOxygenText, bloodOxygenBadge, spo2,
                    SPO2_MIN, Integer.MAX_VALUE);
            // 步数为累计语义：使用 health-trends 聚合求和（totalSteps），不取最新遥测瞬时值；
            // 聚合尚未拉到（首 30s 内）时保持原值不覆盖
            if (totalSteps >= 0) {
                updateMetricCard(stepsText, stepsBadge, totalSteps, null, null);
            }

            updateStatus(heartRate, spo2);
            checkVitalAnomaly(heartRate, spo2);
        } catch (Exception e) {
            // 数据源异常时保持上次展示，不闪退
        }
    }

    /** telemetry 中最新一条的 recorded_at 毫秒时间戳（无有效解析时为 0） */
    private long latestRecordedTime(List<TelemetryPoint> telemetry) {
        long best = 0L;
        for (TelemetryPoint p : telemetry) {
            if (p.getRecordedTimeMillis() > best) {
                best = p.getRecordedTimeMillis();
            }
        }
        return best;
    }

    /** 设备离线状态横幅：不基于旧数据判定健康状态 */
    private void updateStatusOffline() {
        if (statusText == null) {
            return;
        }
        statusText.setText(R.string.status_offline_text);
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
    }

    /**
     * 健康异常本地检测：
     * - 心率/血氧首次越界 → 轻提醒（Toast 一次，不弹窗）
     * - 连续越界达到阈值（约 6~9 秒）→ 生成本地"健康异常"紧急告警通知
     * - 同一异常段内只发一次通知（避免硬件持续越界时每 9 秒轰炸一次）；
     *   恢复正常后计数、提醒与通知状态一并重置
     */
    private void checkVitalAnomaly(Integer heartRate, Integer spo2) {
        boolean hrAbnormal = heartRate != null
                && (heartRate < HEART_RATE_MIN || heartRate > HEART_RATE_MAX);
        boolean spAbnormal = spo2 != null && spo2 < SPO2_MIN;

        if (!hrAbnormal && !spAbnormal) {
            abnormalStreak = 0;
            streakReminderShown = false;
            localAlertSent = false;
            return;
        }

        abnormalStreak++;

        // 本次异常段内只轻提醒一次，不打扰
        if (!streakReminderShown) {
            streakReminderShown = true;
            String hint;
            if (hrAbnormal) {
                hint = getString(R.string.heart_rate_alert_hint, heartRate);
            } else {
                hint = getString(R.string.spo2_alert_hint, spo2);
            }
            Toast.makeText(requireContext(), hint, Toast.LENGTH_SHORT).show();
        }

        // 持续越界达到阈值 → 本地紧急告警通知（与后端跌倒/SOS 同一链路）；
        // 同一异常段内只发一次，避免持续越界时反复轰炸
        if (abnormalStreak >= ABNORMAL_STREAK_THRESHOLD && !localAlertSent) {
            localAlertSent = true;
            long now = System.currentTimeMillis();
            AlertRecord alert = new AlertRecord();
            alert.setId(-now); // 负数标记本地告警，确认时不调后端
            alert.setElderId(0);
            alert.setElderName(elderNameCache);
            alert.setEventType("health_anomaly");
            alert.setSeverity("critical");
            alert.setStatus("new");
            alert.setCreatedAt(TimeUtils.formatIso(now));
            fallAlertManager.showLocalAlert(alert);
        }
    }

    /** 取某指标时间最新的一个数值（后端 telemetry 返回"最新在前"降序，不能按列表末尾取，需按时间戳取最大） */
    private Integer latestValue(List<TelemetryPoint> telemetry, String metric) {
        Integer result = null;
        long bestTime = Long.MIN_VALUE;
        for (TelemetryPoint point : telemetry) {
            if (metric.equals(point.getMetric()) && point.getValueNum() != null
                    && point.getRecordedTimeMillis() >= bestTime) {
                result = point.getValueNum().intValue();
                bestTime = point.getRecordedTimeMillis();
            }
        }
        return result;
    }

    /** 更新指标卡：数值着色 + 右上角状态标签（正常绿 / 偏低橙 / 偏高红） */
    private void updateMetricCard(TextView textView, TextView badgeView, Integer value,
                                  Integer min, Integer max) {
        if (textView == null) {
            return;
        }
        if (value == null) {
            textView.setText(R.string.placeholder_dash);
            if (badgeView != null) {
                badgeView.setText(R.string.placeholder_dash);
            }
            return;
        }
        textView.setText(String.format(Locale.getDefault(), "%d", value));
        if (badgeView != null) {
            if (min != null && max != null && value < min) {
                badgeView.setText(R.string.metric_status_low);
                badgeView.setBackgroundResource(R.drawable.bg_badge_warning);
            } else if (min != null && max != null && value > max) {
                badgeView.setText(R.string.metric_status_high);
                badgeView.setBackgroundResource(R.drawable.bg_badge_fall);
            } else {
                badgeView.setText(R.string.metric_status_ok);
                badgeView.setBackgroundResource(R.drawable.bg_badge_normal);
            }
        }
    }

    private void updateStatus(Integer heartRate, Integer spo2) {
        if (statusText == null) {
            return;
        }
        boolean normal = heartRate != null && spo2 != null
                && heartRate >= HEART_RATE_MIN && heartRate <= HEART_RATE_MAX
                && spo2 >= SPO2_MIN;
        statusText.setText(normal ? R.string.status_normal : R.string.status_alert_text);
        statusText.setTextColor(ContextCompat.getColor(requireContext(),
                normal ? R.color.green : R.color.red));
    }

    /** 切页隐藏时降频刷新（10s）而非暂停，切回立即 3s 刷新：保证任何页面停留时数据都保持最新；
     *  切回时同步"我的"页可能的老人切换（show/hide 不触发 onResume） */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        stopUpdates();
        startUpdates(hidden ? BACKGROUND_UPDATE_INTERVAL : UPDATE_INTERVAL);
        if (!hidden && isAdded()) {
            syncSelectedElderIfChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopUpdates();
        if (chartManager != null) {
            chartManager.clearChart();
        }
        // 告警监控由 AlertMonitorService 前台服务持有（L1），Fragment 销毁不停止，
        // 保证 App 退后台/锁屏/页面重建后 WS 与轮询持续运行
    }
}
