package com.example.myapplication.view;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import com.example.myapplication.R;
import com.example.myapplication.model.TrendBucket;
import com.example.myapplication.utils.TimeUtils;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康图表视图组件：显示心率、血氧的趋势与步数柱状图。
 * 数据源为后端 health-trends 按小时聚合（与后端管理台一致），
 * 左侧显示数值刻度（自适应数据范围）、底部显示时间（HH:mm）刻度；
 * 心率/血氧各自独立成点，步数按桶求和画柱（>0 才画）。
 */
public class HealthChartView extends CardView {

    private LineChart heartRateChart;
    private LineChart bloodOxygenChart;
    private BarChart stepsChart;

    private final List<Integer> heartRateData = new ArrayList<>();
    private final List<Integer> bloodOxygenData = new ArrayList<>();
    private final List<Float> stepsData = new ArrayList<>();
    private final List<String> heartRateLabels = new ArrayList<>();
    private final List<String> bloodOxygenLabels = new ArrayList<>();
    private final List<String> stepsLabels = new ArrayList<>();

    private TextView heartRateSubtitle;
    private TextView bloodOxygenSubtitle;
    private TextView stepsSubtitle;

    public HealthChartView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public HealthChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HealthChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_health_chart, this, true);
        heartRateChart = findViewById(R.id.heart_rate_chart);
        bloodOxygenChart = findViewById(R.id.blood_oxygen_chart);
        stepsChart = findViewById(R.id.steps_chart);
        heartRateSubtitle = findViewById(R.id.heart_rate_agg_subtitle);
        bloodOxygenSubtitle = findViewById(R.id.blood_oxygen_agg_subtitle);
        stepsSubtitle = findViewById(R.id.steps_agg_subtitle);

        setupChart(heartRateChart);
        setupChart(bloodOxygenChart);
        setupBarChart(stepsChart);

        // 空数据态：所选时间窗口内无数据（如设备离线）时显示提示而非空白
        String noDataText = "暂无数据";
        heartRateChart.setNoDataText(noDataText);
        bloodOxygenChart.setNoDataText(noDataText);
        stepsChart.setNoDataText(noDataText);
        heartRateChart.setNoDataTextColor(Color.parseColor("#6B7280"));
        bloodOxygenChart.setNoDataTextColor(Color.parseColor("#6B7280"));
        stepsChart.setNoDataTextColor(Color.parseColor("#6B7280"));
    }

    /** 折线图通用配置：X 轴底部时间、左轴数值（自适应范围）、右轴关闭 */
    private void setupChart(LineChart chart) {
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getDescription().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#6B7280"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        // 启用粒度：刻度位置按整数索引分布（24 个点时若间隔 0.958，相邻位置会取整到同一索引，
        // 导致首标签在左端重复渲染、看起来挤在一起）
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);
        chart.setExtraBottomOffset(16f);

        chart.getAxisLeft().setEnabled(true);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(Color.parseColor("#6B7280"));
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setLabelCount(4, false);
        // Y 轴刻度显示整数（心率/血氧均为整数语义）
        chart.getAxisLeft().setValueFormatter(new DefaultValueFormatter(0));
        // Y 轴自适应数据范围：血氧低于 90、心率高于 110 时也能完整显示
        chart.setAutoScaleMinMaxEnabled(true);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
    }

    /** 柱状图通用配置（步数） */
    private void setupBarChart(BarChart chart) {
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawValueAboveBar(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#6B7280"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);
        chart.setExtraBottomOffset(16f);

        chart.getAxisLeft().setEnabled(true);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(Color.parseColor("#6B7280"));
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setLabelCount(4, false);
        // 步数刻度显示整数
        chart.getAxisLeft().setValueFormatter(new DefaultValueFormatter(0));
        // 步数轴从 0 起；上限由 updateBarChart 按数据向上取整到 10 的倍数（避免单柱时刻度挤成小数/重叠）
        chart.getAxisLeft().setAxisMinimum(0f);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
    }

    /**
     * 追加最新遥测点（心率 / 血氧），保持最近 20 个数据点。
     * 已由 health-trends 聚合曲线取代（见 setTrendData），保留给兼容调用方。
     */
    public void updateChart(int heartRate, int spo2, long timestamp) {
        heartRateData.add(heartRate);
        bloodOxygenData.add(spo2);
        heartRateLabels.add(TimeUtils.formatMonthDayHourMinute(timestamp));
        bloodOxygenLabels.add(TimeUtils.formatMonthDayHourMinute(timestamp));

        if (heartRateData.size() > 20) {
            heartRateData.remove(0);
            bloodOxygenData.remove(0);
            heartRateLabels.remove(0);
            bloodOxygenLabels.remove(0);
        }

        updateLineChart(heartRateChart, heartRateData, heartRateLabels, Color.parseColor("#FF6B6B"));
        updateLineChart(bloodOxygenChart, bloodOxygenData, bloodOxygenLabels, Color.parseColor("#4ECDC4"));
    }

    /**
     * 用后端 health-trends 聚合桶重建曲线（与后端管理台同一数据源）。
     * 心率/血氧各自取每桶 avg；某桶缺该指标时跳过；
     * 步数取每桶 steps_total（>0 才画柱，0/无数据不画）。
     * @param dayGranularity true=按天聚合（X 轴标签 MM/dd）；false=按小时聚合（X 轴标签 MM/dd HH:mm）
     */
    public void setTrendData(List<TrendBucket> buckets, boolean dayGranularity) {
        // 副标题随聚合粒度切换：按天（近30天）显示"按天聚合"，其余"按小时聚合"
        updateSubtitle(heartRateSubtitle, R.string.chart_heart_rate_agg_subtitle,
                R.string.chart_heart_rate_agg_subtitle_day, dayGranularity);
        updateSubtitle(bloodOxygenSubtitle, R.string.chart_blood_oxygen_agg_subtitle,
                R.string.chart_blood_oxygen_agg_subtitle_day, dayGranularity);
        updateSubtitle(stepsSubtitle, R.string.chart_steps_agg_subtitle,
                R.string.chart_steps_agg_subtitle_day, dayGranularity);

        heartRateData.clear();
        bloodOxygenData.clear();
        stepsData.clear();
        heartRateLabels.clear();
        bloodOxygenLabels.clear();
        stepsLabels.clear();
        if (buckets != null) {
            for (TrendBucket b : buckets) {
                long millis = TimeUtils.parseIsoToMillis(b.getBucketStart());
                String label = dayGranularity
                        ? TimeUtils.formatMonthDay(millis)
                        : TimeUtils.formatMonthDayHourMinute(millis);
                TrendBucket.MetricStats hr = b.getHeartRate();
                if (hr != null && hr.getCount() > 0) {
                    heartRateData.add((int) Math.round(hr.getAvg()));
                    heartRateLabels.add(label);
                }
                TrendBucket.MetricStats sp = b.getSpo2();
                if (sp != null && sp.getCount() > 0) {
                    bloodOxygenData.add((int) Math.round(sp.getAvg()));
                    bloodOxygenLabels.add(label);
                }
                Double steps = b.getStepsTotal();
                if (steps != null && steps > 0) {
                    stepsData.add(steps.floatValue());
                    stepsLabels.add(label);
                }
            }
        }
        updateLineChart(heartRateChart, heartRateData, heartRateLabels, Color.parseColor("#FF6B6B"));
        updateLineChart(bloodOxygenChart, bloodOxygenData, bloodOxygenLabels, Color.parseColor("#4ECDC4"));
        updateBarChart(stepsChart, stepsData, stepsLabels);
    }

    /**
     * 清空图表数据
     */
    public void clearChart() {
        heartRateData.clear();
        bloodOxygenData.clear();
        stepsData.clear();
        heartRateLabels.clear();
        bloodOxygenLabels.clear();
        stepsLabels.clear();
        heartRateChart.clear();
        bloodOxygenChart.clear();
        stepsChart.clear();
    }

    /** 按聚合粒度切换副标题文案（dayGranularity=true → 按天） */
    private void updateSubtitle(TextView subtitle, int hourlyRes, int dailyRes, boolean dayGranularity) {
        if (subtitle != null) {
            subtitle.setText(dayGranularity ? dailyRes : hourlyRes);
        }
    }

    /**
     * X 轴标签：数据点不超过 3 个时全部显示；否则只显示"首 / 正中间 / 尾"3 个
     * （标签带日期较长如 10/16 15:00，3 个在窄屏间距充足，且三个图刻度形态统一）
     */
    private ValueFormatter threeLabelFormatter(List<String> labels) {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                int size = labels.size();
                if (idx < 0 || idx >= size) {
                    return "";
                }
                if (size <= 3) {
                    return labels.get(idx);
                }
                int mid = size / 2;
                if (idx == 0 || idx == mid || idx == size - 1) {
                    return labels.get(idx);
                }
                return "";
            }
        };
    }

    private void updateLineChart(LineChart chart, List<Integer> data, List<String> labels, int color) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, data.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setLineWidth(3f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        // X 轴刻度：每个数据点都设为刻度位置，格式化器只显示"首 / 正中间 / 尾"3 个标签
        // （不依赖 MPAndroidChart 的 labelCount 算法——它对不同点数会算出 2 或 3 个刻度，不可控）
        if (!labels.isEmpty()) {
            chart.getXAxis().setLabelCount(labels.size(), true);
            chart.getXAxis().setValueFormatter(threeLabelFormatter(labels));
        }

        chart.setData(new LineData(dataSets));
        chart.invalidate();
    }

    private void updateBarChart(BarChart chart, List<Float> data, List<String> labels) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new BarEntry(i, data.get(i)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(Color.parseColor("#0F766E"));
        dataSet.setDrawValues(false);

        // 步数轴上限：取数据最大值向上取整到 10 的倍数（最小 10），刻度始终为 0 起的整数，不重叠
        float maxSteps = 0f;
        for (Float v : data) {
            if (v != null && v > maxSteps) {
                maxSteps = v;
            }
        }
        float axisMax = (float) Math.ceil(maxSteps / 10f) * 10f;
        if (axisMax <= 0f) {
            axisMax = 10f;
        }
        chart.getAxisLeft().setAxisMaximum(axisMax);

        if (!labels.isEmpty()) {
            chart.getXAxis().setLabelCount(labels.size(), true);
            chart.getXAxis().setValueFormatter(threeLabelFormatter(labels));
        }

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        chart.setData(barData);
        chart.invalidate();
    }
}
