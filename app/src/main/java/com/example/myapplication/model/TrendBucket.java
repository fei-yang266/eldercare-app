package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康趋势桶（TrendBucket）
 * 数据字典：bucket_start / heart_rate(MetricStats) / spo2(MetricStats) /
 *          steps_total / activity(ActivityCount[])
 * 聚合口径：heart_rate/spo2 取 avg/min/max/count；steps 按桶求和；activity 按文本计数
 */
public class TrendBucket {

    private String bucketStart;
    private MetricStats heartRate;
    private MetricStats spo2;
    private Double stepsTotal;
    private List<ActivityCount> activity = new ArrayList<>();

    public TrendBucket() {
    }

    public TrendBucket(String bucketStart, MetricStats heartRate, MetricStats spo2,
                       Double stepsTotal, List<ActivityCount> activity) {
        this.bucketStart = bucketStart;
        this.heartRate = heartRate;
        this.spo2 = spo2;
        this.stepsTotal = stepsTotal;
        this.activity = activity;
    }

    public String getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(String bucketStart) {
        this.bucketStart = bucketStart;
    }

    public MetricStats getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(MetricStats heartRate) {
        this.heartRate = heartRate;
    }

    public MetricStats getSpo2() {
        return spo2;
    }

    public void setSpo2(MetricStats spo2) {
        this.spo2 = spo2;
    }

    public Double getStepsTotal() {
        return stepsTotal;
    }

    public void setStepsTotal(Double stepsTotal) {
        this.stepsTotal = stepsTotal;
    }

    public List<ActivityCount> getActivity() {
        return activity;
    }

    public void setActivity(List<ActivityCount> activity) {
        this.activity = activity;
    }

    /** 指标统计（MetricStats） */
    public static class MetricStats {
        private double avg;
        private double min;
        private double max;
        private int count;

        public MetricStats() {
        }

        public MetricStats(double avg, double min, double max, int count) {
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.count = count;
        }

        public double getAvg() {
            return avg;
        }

        public void setAvg(double avg) {
            this.avg = avg;
        }

        public double getMin() {
            return min;
        }

        public void setMin(double min) {
            this.min = min;
        }

        public double getMax() {
            return max;
        }

        public void setMax(double max) {
            this.max = max;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    /** 活动计数（ActivityCount） */
    public static class ActivityCount {
        private String activity;
        private int count;

        public ActivityCount() {
        }

        public ActivityCount(String activity, int count) {
            this.activity = activity;
            this.count = count;
        }

        public String getActivity() {
            return activity;
        }

        public void setActivity(String activity) {
            this.activity = activity;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
