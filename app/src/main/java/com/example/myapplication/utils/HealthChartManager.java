package com.example.myapplication.utils;

import com.example.myapplication.model.TrendBucket;
import com.example.myapplication.view.HealthChartView;

import java.util.List;

/**
 * 健康图表管理器
 * 数据源为后端 health-trends 按小时聚合（每 30s 随刷新循环拉取重建曲线），
 * 与后端管理台展示一致；不再累积实时遥测点（指标卡负责最新值）。
 */
public class HealthChartManager {

    private final HealthChartView chartView;

    public HealthChartManager(HealthChartView chartView) {
        this.chartView = chartView;
    }

    /** 用后端聚合桶重建曲线（心率/血氧各取每桶 avg；dayGranularity=true 时 X 轴标 MM/dd） */
    public void setTrendData(List<TrendBucket> buckets, boolean dayGranularity) {
        if (chartView != null) {
            chartView.setTrendData(buckets, dayGranularity);
        }
    }

    /** 清空图表 */
    public void clearChart() {
        if (chartView != null) {
            chartView.clearChart();
        }
    }
}
