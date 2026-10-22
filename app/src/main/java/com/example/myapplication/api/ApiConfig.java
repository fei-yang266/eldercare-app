package com.example.myapplication.api;

/**
 * 后端连接配置（对齐 docs/app-integration.md v0.2.0 SPEC 01–06）
 * 生产后端：http://47.108.147.192:18080（公网，Swagger 同址 /docs；2026-10-14 起云端统一 18080）
 * 本地联调：Android 模拟器访问宿主机用 10.0.2.2；真机调试用电脑局域网 IP。
 * 明文 HTTP 放行域见 res/xml/network_security_config.xml（仅限公网 IP 与模拟器宿主机）。
 */
public final class ApiConfig {

    private ApiConfig() {
    }

    /** REST / Swagger 基址（生产公网后端，端口随云端统一为 18080） */
    public static final String BASE_URL = "http://47.108.147.192:18080";

    /** REST API 前缀 */
    public static final String API_BASE = BASE_URL + "/api/v1";

    /** WebSocket 地址（token 走查询参数，必须 URL 编码，勿打印完整 URL） */
    public static final String WS_URL = "ws://47.108.147.192:18080/api/v1/ws";

    /** WebSocket 指数退避重连：1s 起、最大 30s */
    public static final long WS_RECONNECT_BASE_DELAY_MS = 1_000L;
    public static final long WS_RECONNECT_MAX_DELAY_MS = 30_000L;
}
