# D1 验收清单

**日期**: 2026-09-24
**角色**: C (Android App开发)
**状态**: 待验收

## 1. Android工程搭建

- [ ] 项目初始化完成，包含基本依赖（appcompat、material、constraintlayout）
- [ ] build.gradle配置正确，compileSdk 36，minSdk 24
- [ ] 项目可成功编译运行
- [ ] 模拟器或真机可正常安装和启动
- [ ] MainActivity正确加载，底部导航正常显示

## 2. 应用架构

- [ ] 采用Fragment + BottomNavigationView架构
- [ ] 代码结构清晰，包划分合理（fragments、adapters）
- [ ] MainActivity负责Fragment切换逻辑
- [ ] 各Fragment职责分明

## 3. 底部导航

- [ ] 实时查看（nav_realtime）
- [ ] 历史查询（nav_history）
- [ ] SOS紧急呼叫（nav_sos）
- [ ] 联系人配置（nav_contacts）
- [ ] 设备管理（nav_settings）

## 4. 实时查看功能（fragment_realtime.xml）

- [ ] 显示跌倒检测状态（正常/告警）
- [ ] 显示心率数据
- [ ] 显示血氧数据
- [ ] 显示传感器状态
- [ ] 显示设备名称
- [ ] 显示连接状态
- [ ] 显示电量信息
- [ ] 数据实时更新（模拟）

## 5. 历史查询功能（fragment_history.xml）

- [ ] 显示历史记录列表
- [ ] 支持搜索功能
- [ ] 支持事件类型过滤（全部/跌倒/正常）
- [ ] 支持日期过滤（全部/今天/最近7天/最近30天）
- [ ] RecyclerView正确显示数据

## 6. SOS紧急呼叫功能（fragment_sos.xml）

- [ ] SOS紧急按钮显示
- [ ] 点击按钮可发送SOS请求
- [ ] 显示SOS发送状态
- [ ] 显示最后SOS记录

## 7. 联系人配置功能（fragment_contacts.xml）

- [ ] 显示联系人列表
- [ ] 可添加新联系人
- [ ] 联系人信息（姓名、电话）正确显示
- [ ] 可保存联系人信息
- [ ] 可取消编辑

## 8. 设备管理功能（fragment_settings.xml）

- [ ] 显示设备信息（名称、状态）
- [ ] 显示电量信息
- [ ] 低电量告警阈值设置（SeekBar）
- [ ] 备用传感器选项（手机传感器、设备传感器）

## 9. 权限配置（AndroidManifest.xml）

- [ ] 网络权限（INTERNET）
- [ ] 网络状态权限（ACCESS_NETWORK_STATE）
- [ ] WiFi状态权限（ACCESS_WIFI_STATE）
- [ ] 传感器权限（BODY_SENSORS、BODY_SENSORS_BACKGROUND）
- [ ] 位置权限（ACCESS_FINE_LOCATION、ACCESS_COARSE_LOCATION、ACCESS_BACKGROUND_LOCATION）

## 10. 字符串资源（strings.xml）

- [ ] 应用名称正确设置
- [ ] 所有界面文字都有对应的字符串资源
- [ ] 支持多语言

## 11. 颜色资源（colors.xml）

- [ ] 定义了必要颜色值（primary、green、red、gray等）
- [ ] 颜色值符合设计规范

## 12. 代码规范

- [ ] Java命名规范遵循Google Java Style Guide
- [ ] 代码注释清晰
- [ ] 方法命名语义化
- [ ] 避免硬编码，使用资源文件

## 13. 用户体验

- [ ] 界面布局合理，间距适中
- [ ] 文字大小和颜色符合可读性要求
- [ ] 交互反馈及时（Toast提示）
- [ ] 界面响应流畅

## 14. D1任务完成标志

- [ ] Android工程搭建完成
- [ ] 验收清单初稿已完成
- [ ] 向团队展示基本框架
- [ ] 确认后续任务方向

## 备注

- 当前阶段为D1，仅完成基础框架搭建
- 所有功能均使用模拟数据
- 需在D2之后与云平台联调
