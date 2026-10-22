# eldercare-app（适老化智能家居 · 子女端 App）

面向适老化智能家居场景的**子女端 Android App**，对接 `eldercare-cloud` 后端，实时监测老人的心率 / 血氧 / 步数，跌倒与 SOS 紧急事件通过**系统通知**秒级触达家属，支持多老人切换、健康趋势分析与历史记录回溯。

## 功能特性

- **实时监测**：心率 / 血氧 / 步数实时卡（设备离线时明确显示 `--` + "设备离线"，不展示旧数据误导）；本地健康异常检测（心率越界 / 血氧过低连续采样触发系统通知）
- **健康趋势图**：心率 / 血氧折线 + 步数柱状，三档时间范围可切换（**近24小时 / 近7天 / 近30天**），与后端管理台一致；按小时 / 按天聚合，X 轴带日期标签（首 / 正中 / 尾 3 刻度防重叠）；设备离线仍可查看后端聚合历史
- **紧急告警（纯系统通知）**：跌倒 / SOS / 健康异常 → 锁屏横幅系统通知，带「确认 / 忽略」动作；确认联动后端 `ack`；前台服务常驻 + 断线重连 + 电池白名单请求，退后台 / 锁屏仍监控
- **SOS / 紧急事件页**：最近紧急事件列表（跌倒 / SOS 等），确认后从列表移除（历史页保留）
- **历史记录**：事件搜索 + 类型 / 日期筛选（全部 / 跌倒 / 围栏 / 低电量 / 设备离线；日期支持今天 / 近7天 / 近30天）
- **多老人管理**：账号关联多名老人，实时 / 历史 / SOS / 告警均按选中老人拉取；支持按手机号绑定 / 新建档案 / 自助解绑
- **联系人 / 设备 / 围栏**：紧急联系人整体维护 + 一键拨打；绑定设备与电子围栏只读展示

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / 构建 | Java 11 · Gradle（`libs.versions.toml` 版本目录） |
| 图表 | MPAndroidChart 3.1.0（折线 / 柱状 + 自定义 X 轴刻度） |
| 网络 | OkHttp（REST + WebSocket 30s ping/pong 保活） |
| 后台 | 前台服务 `AlertMonitorService`（`START_STICKY` + onDestroy 自重启） |
| 通知 | 系统级通知（通知渠道 + 高优先级横幅 + 确认/忽略动作） |
| UI | Fragment + CardView，品牌色 `#0F766E`，圆角 16dp，纯 PNG 图标 |

## 后端对接

- 后端：`eldercare-cloud`（FastAPI），公网 `http://47.108.147.192:18080`（管理台 `:3000`）
- 接口清单、字段契约与 App 行为约定详见 **`后端对接说明-APP端.md`**（同仓库）
- 关键约定：趋势图请求一律带 `start/end` 时间窗口（设备离线窗口内无数据 → 图表"暂无数据"）；遥测 metric 枚举 `heart_rate / spo2 / steps`；事件类型 `fall / sos / geofence_exit / low_battery / device_offline`
- 演示账号：`family / family123456`（登录页演示登录）

## 环境与构建

1. Android Studio（Ladybug 及以上）打开本项目，等待 Gradle 同步
2. 命令行构建：`.\gradlew.bat assembleDebug`
3. 产物：`app\build\outputs\apk\debug\app-debug.apk`（minSdk 24 / targetSdk 36）
4. 真机安装：vivo / 小米等建议按 `后端对接说明-APP端.md` 第 6 节加入后台运行白名单，保证离线通知可达

## 目录结构

```
app/src/main/java/com/example/myapplication/
├── api/          # 数据层：HTTP / WS / Mock / 会话（HealthDataService 统一入口）
├── fragments/    # 页面：实时监测 / 历史记录 / SOS / 我的
├── model/        # 数据模型（Elder / TelemetryPoint / TrendBucket / EventRecord…）
├── service/      # AlertMonitorService 前台监控服务
├── utils/        # 图表管理 / 告警通知 / 时间工具 / 老人选择器
└── view/         # HealthChartView 自定义趋势图组件
```

## 说明

- 明文 HTTP 仅用于演示环境，生产部署需 TLS（见对接说明）
- 通知为系统级触达（App 内无弹窗），国产 ROM 需手动允许自启动 / 后台运行
