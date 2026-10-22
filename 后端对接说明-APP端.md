# 子女端 App（Android）对接说明

> 面向后端开发者：本文档描述当前 App 端（`D:\as\MyApplication`）的**实际实现内容**，包括已对接的接口、字段依赖、行为约定与已知限制，供后端联调与排障参考。
> 更新日期：2026-10-14 ｜ 契约基线：`app-integration.md` v0.2.0（SPEC 01–06 全合入，commit cc3562b） ｜ App 包名：`com.example.myapplication` ｜ 应用名：跌倒检测健康监护

---

## 1. 连接配置（当前生效）

| 项 | 值 |
|---|---|
| REST / Swagger 基址 | `http://47.108.147.192:18080` |
| REST API 前缀 | `http://47.108.147.192:18080/api/v1` |
| WebSocket | `ws://47.108.147.192:18080/api/v1/ws?token=<URL 编码的 access_token>` |
| 明文 HTTP 放行 | `res/xml/network_security_config.xml`（仅放行 47.108.147.192 与模拟器宿主机 10.0.2.2；端口无关） |
| 超时 | 连接/读/写各 10s |
| WS 重连 | 指数退避 1s→2s→…→30s；4401/4403 停止重连回登录页 |

> ⚠️ 2026-10-14 云端端口已由 8080 统一为 **18080**，App 已同步（`api/ApiConfig.java` 的 `BASE_URL` / `WS_URL`）。当前为**明文 HTTP**，仅适合开发联调，生产建议启用 TLS。

---

## 2. 账号体系（App 端实现）

- **注册**：`POST /api/v1/auth/register`（公开，固定创建 **family 角色**）。App 本地校验与后端一致：
  - `username`：`^[A-Za-z0-9_-]{3,32}$`；
  - `password`：6–64 字符，UTF-8 字节 ≤72（对齐 bcrypt）；
  - `display_name` / `phone`：**可选**，phone ≤32 字符、不校验号码格式（后端不强制 11 位）。
  - 注册成功（201）回登录页并预填用户名；409/422 按后端 detail 提示。
- **登录**：`POST /api/v1/auth/login`；保存 `access_token / token_type / expires_in / role`，本地计算过期。
- **会话失效**：REST 401 或 WS 4401/4403 → 本地清会话回登录页（并发防抖只跳一次）。
- **记住我**：Base64（非加密）存本地；退出不清除、取消勾选才清除。
- **演示模式**：登录页显式切到内置 Mock 数据源（后端不可达时演示界面）。

---

## 3. App 实际调用的接口清单

| 接口 | 用途 | 调用频率 | 备注 |
|---|---|---|---|
| `POST /api/v1/auth/login` | 登录取 Token | 登录时 | 公开 |
| `POST /api/v1/auth/register` | 注册 family 账号 | 注册时 | 公开 |
| `GET /api/v1/elders` | 老人列表（多老人展示/切换） | 首次 + 切页 | 选中老人持久化 |
| `POST /api/v1/elders` | **新建老人档案（phone 必填）** | 我的页"添加老人" | SPEC 06；409 提示改用 add-by-phone |
| `POST /api/v1/elders/add-by-phone` | **按手机号自助绑定老人** | 我的页/实时页添加入口 | SPEC 06；404 档案不存在、409 已绑定 |
| `DELETE /api/v1/elders/{id}/access/me` | **自助解绑监护关系** | 我的页解绑按钮 | 仅 family；老人与历史保留 |
| `GET /api/v1/elders/{id}/contacts` | 紧急联系人列表 | 我的页进入/刷新时 | |
| `PUT /api/v1/elders/{id}/contacts` | **整体替换联系人** | 添加/删除/设主后 | ContactIn[]；最多一个 is_primary |
| `GET /api/v1/elders/{id}/devices` | 绑定设备（只读） | 我的页进入/刷新时 | family 只读 |
| `GET /api/v1/geofences/{elder_id}` | 电子围栏（只读） | 我的页进入/刷新时 | 首版只读展示 |
| `GET /api/v1/elders/{id}/telemetry?limit=50` | 实时遥测（心率/血氧/步数） | 实时页 3s，后台 10s | |
| `GET /api/v1/elders/{id}/health-trends?interval=hour\|day&limit=…&start=&end=` | 趋势图数据（时间范围切换） | 实时页刷新/切档时 | **start/end 时间窗口必带**（近24h/近7天/近30天）；窗口内无数据返回 `[]` |
| `GET /api/v1/events?elder_id={id}&type=&start=&end=&limit=500` | 事件列表（类型/日期可筛选） | 历史/SOS 页 10s 后台轮询 | 日期范围走后端 start（带时区 ISO），limit 500 |
| `GET /api/v1/alerts?elder_id={id}&status=new&limit=500` | 未处理告警列表 | 10s 轮询（通知兜底）+ SOS 状态卡 | **只拉 new**（SPEC 06 建议） |
| `POST /api/v1/alerts/{id}/ack` | 确认告警（幂等） | 通知确认 / SOS 二次确认联动 | 同事务 new→handled |
| `GET /api/v1/alerts/{id}/notifications` | 告警通知记录 | 预留（当前 UI 未展示） | console 模拟，不真发短信 |
| `WS /api/v1/ws?token=…` | 实时推送（alert / telemetry） | 常驻 | 断线退避重连；**ready 后 App 立即全量刷新一次** |

---

## 4. 字段契约与兼容处理（后端注意）

1. **时间格式**：App 兼容 `Z / +00:00 / +08:00` 时区后缀及毫秒/微秒小数秒；展示统一转本地时区。**App 发出的 `start` 参数带时区 ISO 8601**（`TimeUtils.formatIso`）。
2. **AlertOut（REST 与 WS）均含 `elder_name` 与 `occurred_at`（SPEC 06 已确认）**：App 通知时间优先取 `occurred_at`（回退 `created_at`），老人名优先后端字段（本地 elder 名缓存仅做字段缺失兜底，缓存 TTL 5 分钟）。
3. **事件类型枚举**：`fall / sos / geofence_exit / low_battery / device_offline`（App 另有本地 `health_anomaly`）；severity：`info / warning / critical`；status：`new / handled / closed`。**对未知 type/severity 一律容忍展示，不当脏数据。** 历史记录页的类型筛选 chips 仅提供 全部/跌倒/围栏/低电量/设备离线（硬件不会主动触发 SOS，故筛选不提供 sos 项）；后端若返回 sos 事件，列表仍按类型名正常展示（记录展示保留）。
4. **遥测 metric 枚举**：`heart_rate / spo2 / steps`（实时页仅展示这三项，无体温）。
5. **趋势桶（health-trends）字段**：`bucket_start`（ISO 8601）+ `heart_rate / spo2`（各含 `avg / min / max / count`）+ `steps_total`（该桶区间内累计步数，可为 null 或 0）+ `activity`。App 心率/血氧取每桶 `avg`（count>0 才画点），步数柱取 `steps_total`（>0 才画柱）。**`steps_total` 为桶内区间步数（非瞬时）**，App 步数卡取当前时间范围窗口内各桶 `steps_total` 求和（累计语义）。
6. **趋势时间窗口**：App 请求 `health-trends` 一律带 `start`/`end`（带时区 ISO 8601，App 用 `TimeUtils.formatIso` 输出 `+08:00`，后端已实测可解析）。**`from`/`to` 参数后端忽略**，勿用；不带窗口时后端返回"最近 limit 个有数据桶"（设备离线时会翻到很久以前的数据），App 已不再使用该语义。
7. **多老人**：App 已支持账号关联多名老人（`GET /elders` 全量返回）；实时/历史/SOS/告警/联系人/设备/围栏**均按当前选中老人**拉取。选中 id 持久化（未选中回退第一个可见老人）。**无可见老人时**：遥测/事件/告警接口 404 或空 → App 显示 `--` 与空态，并提供"按手机号绑定/新建档案"引导。
8. **WS 消息结构**：按 `type` 分派 `ready / alert / telemetry`；alert 内嵌对象同 AlertOut（含 elder_name/occurred_at）；遥测消息用 `elder_id`（REST 用 `device_id`，App 内部已分别处理）。WS 无业务心跳，协议层 ping/pong 由 OkHttp 30s 维持。
9. **错误体**：App 兼容 `detail` 为**字符串**或**数组**（取首元素 `msg`）；401 统一触发会话失效。
10. **通知为 console 模拟**：后端当前只创建 `channel="console"` 的模拟通知，App 未在 UI 展示通知状态（避免误导用户认为已真发短信/微信）。

---

## 5. App 端行为约定（重点）

### 5.1 紧急事件与系统通知（后端需知晓）

- **触达通道（L1，纯系统通知，App 内无弹窗）**：
  1. **WS 推送**：后端实时推 alert → App 秒级**系统通知**（高优先级横幅，锁屏/通知栏可见）。
  2. **REST 轮询兜底**：每 10s 拉 `GET /alerts?status=new` + `GET /events`（拉取自动跟随当前选中老人）。
  3. **WS ready 补拉**：WS 连接成功（收到 `ready`）后，App 立即补拉一次告警+事件，补齐断线/重连期间漏掉的数据。
- **系统通知说明**：紧急告警（fall/sos/critical）发**系统级通知**（锁屏/通知栏/横幅可见，前台与后台均有效）；
  通知带「确认 / 忽略」动作：**确认 → `POST /alerts/{id}/ack` + 写本地已确认集合（SOS 页联动）**，忽略 → 仅关闭通知。
  通知去重与 SOS 页联动共用同一持久化集合，同一告警只触发一次。
- **前台服务常驻**：登录后由 `AlertMonitorService`（dataSync 前台服务）持有 WS + 轮询，App 退后台/锁屏/划掉任务后仍监控；
  服务 `START_STICKY`，**onDestroy 非用户登出时延迟 2s 自重启**（防国产 ROM 后台清理）；MainActivity 启动时请求**电池优化白名单**（系统设置弹窗，缓解 Doze 休眠）。
- **通知条件**：仅"紧急"事件发通知 —— `severity == critical` **或** 类型 ∈ {`fall`, `sos`}。低电量、离线、围栏等非紧急只进历史记录。
- **事件级本地通知兜底**：若后端**只生成事件（event）未生成告警（alert）**，App 轮询 `GET /events` 发现新紧急事件会**本地生成通知**（不依赖 alert），确认**不调 ack**。
- **去重**：已通知 alert id / event id 持久化（上限 300）；alert 与 event 按 `event_id` 联动，同一事件只触发一次。
- **确认**：通知「确认」→ `POST /alerts/{id}/ack`（幂等）；确认后事件从 SOS 页移除（本地已确认集合），历史页保留。

### 5.2 页面刷新节奏

| 页面 | 前台 | 后台（切到其他 Tab） |
|---|---|---|
| 实时监测页 | 3s 拉遥测 + 趋势 | 10s 降频（保持最新） |
| 历史记录页 | 10s 轮询事件 | 10s 持续轮询（日期范围走后端 start） |
| SOS（紧急事件）页 | 10s 轮询事件+告警 | 10s 持续轮询 |
| 告警通知检测 | WS + 10s REST（status=new） | 同前台（常驻） |

> 后端侧会看到 App 保持 10s 级低频轮询。**告警接口现在只拉 `status=new`**，已确认（handled/closed）不再被 App 轮询命中，负载更低。

### 5.3 实时页趋势图（时间范围切换 + 窗口语义）

- **三档时间范围**（与后端管理台一致，标题行三按钮切换，切换即重拉）：
  | 档位 | interval | limit | start/end 窗口 |
  |---|---|---|---|
  | 近24小时 | `hour` | 24 | `[now-24h, now]` |
  | 近7天 | `hour` | 168 | `[now-7d, now]` |
  | 近30天 | `day` | 30 | `[now-30d, now]` |
- **窗口语义（重要）**：请求必带 `start`/`end`。设备离线 → 窗口内无桶 → 后端返回 `[]` → App 三张图显示"暂无数据"（不再回退展示很久以前的旧桶）。
- **X 轴时间标签带日期**：按小时 `MM/dd HH:mm`（如 `10/16 15:00`），按天 `MM/dd`（如 `10/15`）；每张图最多显示**首 / 正中间 / 尾** 3 个刻度（点少则少显示），避免长标签在窄屏重叠。
- **图表标题对齐管理台**：`心率趋势 / 平均心率·按小时聚合`（近30天切为"按天聚合"）、`血氧趋势 / 平均血氧·…`、`步数趋势 / 区间步数·…`。

### 5.4 健康卡实时语义（设备在线/离线区分）

- **离线判定**：最新遥测 `recorded_at` 距今超过 **3 分钟**（设备约 30s 上报一次）即视为设备离线。
- **离线时**：心率/血氧卡显示 `--` + 徽标"离线"，状态横幅显示"设备离线"（灰）——**不显示最后一条旧数据、不误报异常**；步数卡保留近24h累计（步数为累计口径，离线仍有意义）。
- **在线时**：正常显示实时值 + 正常/偏低/偏高判定，恢复上报后 3s 内自动切回实时显示。
- **趋势图与在线状态无关**：设备离线也能查看后端聚合历史（health-trends），仅健康卡体现实时语义。

### 5.5 本地健康异常检测（App 端，无需后端参与）

- 实时页检测最新遥测：心率 <60 或 >100 bpm、血氧 <95%，连续 3 个采样点越界（约 6~9 秒）→ App 本地生成 `health_anomaly` **系统通知**（id 为负数标记本地，确认**不调后端 ack**，仅写本地已确认集合）。

### 5.6 老人管理与联系人（SPEC 06 新增，后端需知晓）

- **添加老人**：我的页"添加老人"→ ①按手机号绑定（`add-by-phone`，11 位 `^1[3-9]\d{9}$`）；②新建档案（`POST /elders`，**phone 必填**，重复 409 后端 detail 建议"改用添加老人"）。成功即自动切换为该老人。
- **解绑**：我的页每行"解绑"→ 确认 → `DELETE /elders/{id}/access/me`；解绑的是当前选中老人时 App 复位选中项。
- **一键拨打**：联系人行"拨打"→ 确认弹窗 → `ACTION_DIAL` 跳系统拨号盘（**无需 CALL_PHONE 权限**，仅进入拨号界面，不直接呼出）。
- **主联系人**：最多一个 `is_primary`；App 在添加/设主时保证整表唯一。

---

## 6. 已知限制与待办（App 侧如实说明）

1. **设备绑定不在 App 内**：family 无设备管理接口；设备由管理台 admin 绑定，App 只读展示（UID/类型/在线/电量/最后在线）。
2. **无 refresh token**：access_token 过期即本地清会话回登录页，无自动续期。
3. **系统通知已上线（L1，纯通知、无 App 内弹窗）**：紧急事件由前台服务发系统级通知（锁屏/横幅/通知栏可见，后台有效），通知「确认」联动后端 ack。**后台可靠性说明**：①Android 系统（尤其国产 ROM 省电策略/Doze 休眠）可能冻结后台网络或杀前台服务，导致**通知延迟**（如熄屏后不立即通知、亮屏/进 App 才补发）——已做 `START_STICKY` + onDestroy 自重启 + 电池优化白名单请求缓解，另需用户手动把 App 加入系统"允许自启动/后台运行"白名单：**华为/荣耀**：设置→应用→应用管理→本 App→电池→允许后台活动（或 设置→电池→更多电池设置→休眠时始终保持网络连接）；**小米/红米**：设置→应用设置→授权管理→自启动管理→允许，省电策略改"无限制"；**OPPO/一加**：设置→电池→应用耗电管理→允许后台运行；**vivo**：设置→电池→后台耗电管理→允许后台高耗电，i管家→应用管理→自启动→允许；**原生/类原生**：设置→应用→本 App→电池→不受限制（App 首次启动已请求）。②通知权限被拒时通知不显示（App 内已无弹窗兜底，请在系统设置允许通知）；③通知「忽略」不调 ack（后端 alert 仍为 new，App 本地去重不重复提醒）。**实时性兜底（L2/L3）未做**：进程被系统彻底杀死后需后端推送（FCM/厂商通道）才能保证秒级可达，见方案文档。
4. **明文 HTTP**：见第 1 节，生产需 TLS。
5. **记住我密码为 Base64 存储**：仅混淆非加密，建议用户端理解风险。
6. **围栏/设备/联系人仅"我的"页只读展示**：围栏与联系人 App 侧支持写（联系人已实现整表替换；围栏 PUT 未接入 UI），如需 App 端配置围栏可后续补。
7. **通知状态未展示**：后端 console 模拟通知不真发，App 不展示避免误导。

---

## 7. 快速排障指引

| 现象 | 排查点 |
|---|---|
| 历史/实时/SOS 页无数据、显示 `--` | 账号下是否有 elder？`GET /elders` 是否为空；**端口是否 18080**（`curl http://47.108.147.192:18080/api/v1/elders -H "Authorization: Bearer <token>"`） |
| 跌倒后无通知 | logcat `FallAlertManager`：`[WS-ALERT]` 有推送但没 `[NOTIFY]`？`[POLL] alerts=N events=M` 是否增长；事件是否 fall/sos/critical；系统是否允许通知/加入电池白名单 |
| 通知重复发 | 已持久化去重；重启仍发旧告警 → event_id 联动未命中 |
| 登录后立刻被登出 | Token 是否过期（`expires_in`）；WS 是否 4401/4403 关闭；REST 是否 401 |
| 时间显示差 8 小时 | 后端时间字符串是否带时区；App 兼容 `Z`/`+08:00`/小数秒 |
| 添加老人提示"手机号已存在" | 该老人档案已被其他账号关联 → 用"按手机号绑定"（add-by-phone，幂等返回 200） |
| 解绑后列表未更新 | `DELETE /access/me` 后 App 本地复位选中；如仍显示说明后端 403/404，看 logcat `ProfileFragment` |

---

*本说明随 App 代码维护；代码位置：`app/src/main/java/com/example/myapplication/{api, fragments, utils, view, model, adapters}`。*
