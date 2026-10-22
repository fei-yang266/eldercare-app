# 老年智能监测子女端 - 项目说明

## 项目概述
老年智能监测系统的子女端应用，实时获取心率、血氧、体温数据并展示图表，同时定期从后端API读取跌倒状态并显示提示。

## 功能特性

### 1. 实时数据监测
- **心率**：60-100 bpm
- **血氧**：95-100%
- **体温**：36.0-37.5℃
- 每3秒自动更新

### 2. 数据图表
- 使用MPAndroidChart绘制趋势图
- 显示最近20个数据点
- 贝塞尔曲线平滑显示

### 3. 设备状态
- 在线/离线状态
- 电量显示（60-90%）
- 设备名称：HealthMonitor-001

### 4. 跌倒检测
- 定期从后端API读取跌倒状态（每3秒）
- 检测到跌倒时显示警报对话框
- 防抖机制：10秒内不重复弹窗
- 显示详细警报信息（时间、设备、位置、电量）

## 技术栈

- Java
- Android SDK 36
- MPAndroidChart 3.1.0
- Material Components
- Gradle 8.13.0

## 项目结构

```
app/src/main/java/com/example/myapplication/
├── model/
│   ├── HealthData.java
│   ├── DeviceStatus.java
│   └── FallAlert.java
├── api/
│   ├── MockApiService.java
│   └── HealthDataService.java
├── view/
│   ├── HealthChartView.java
│   └── FallAlertDialog.java
├── utils/
│   ├── HealthChartManager.java
│   └── FallAlertManager.java
└── fragments/
    └── RealTimeFragment.java
```

## 构建和运行

### 构建项目
```bash
./gradlew build
```

### 安装APK
```bash
./gradlew installDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 运行应用
在Android Studio中点击Run按钮。

## 模拟数据说明

当前使用MockApiService生成模拟数据，用于开发测试。真实API对接后替换MockApiService即可。

### 健康数据
- 心率：60-100 bpm 随机波动
- 血氧：95-100% 随机波动
- 体温：36.0-37.5℃ 随机波动
- 设备状态：随机电量 60-90%

### 跌倒检测
- 检测概率：每次调用1%概率返回跌倒状态
- 检测间隔：每3秒检查一次
- 防抖机制：10秒内不重复弹窗

## 注意事项

1. 跌倒检测状态由后端API提供，子女端定期读取并提示
2. 模拟数据仅供开发测试使用
3. 需要网络连接下载MPAndroidChart库
4. 建议在Android 7.0及以上设备运行
