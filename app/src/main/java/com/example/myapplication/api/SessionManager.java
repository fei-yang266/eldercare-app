package com.example.myapplication.api;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 会话管理（对齐 docs/app-integration.md 认证章节）
 * - 保存 access_token / username / role / 过期时间（由 expires_in 计算，不硬编码）
 * - 无 refresh token：401 或过期即 clear() 并回到登录页
 * - 后端未部署的演示阶段使用 mock 会话（mock_mode=true）
 */
public class SessionManager {

    private static final String PREF_NAME = "session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_MOCK = "mock_mode";
    private static final String KEY_SELECTED_ELDER_ID = "selected_elder_id";

    private static SessionManager instance;
    private final SharedPreferences preferences;

    private SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    /** 保存登录会话；expiresInSec 为接口返回的 expires_in */
    public void saveSession(String token, String username, String role, long expiresInSec) {
        preferences.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, role)
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSec * 1000L)
                .putBoolean(KEY_MOCK, false)
                .apply();
    }

    /** 演示模式会话：后端未部署时跳过真实登录 */
    public void saveMockSession(String username) {
        preferences.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, "family")
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + 24 * 3600 * 1000L)
                .putBoolean(KEY_MOCK, true)
                .apply();
    }

    public String getToken() {
        return preferences.getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return preferences.getString(KEY_USERNAME, null);
    }

    public String getRole() {
        return preferences.getString(KEY_ROLE, "family");
    }

    public boolean isMockSession() {
        return preferences.getBoolean(KEY_MOCK, false);
    }

    public boolean isLoggedIn() {
        return isMockSession() || (getToken() != null && !isExpired());
    }

    public boolean isExpired() {
        long expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L);
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    /** 本地过期时间（毫秒），用于"我的"页展示 */
    public long getExpiresAt() {
        return preferences.getLong(KEY_EXPIRES_AT, 0L);
    }

    /** 清除本地会话（服务端无 logout 接口，退出由本地清除 + 关闭 WebSocket 实现） */
    public void clear() {
        preferences.edit().clear().apply();
    }

    /** 当前查看的老人 id（多老人切换；-1 表示未显式选择，取第一个可见老人） */
    public long getSelectedElderId() {
        return preferences.getLong(KEY_SELECTED_ELDER_ID, -1L);
    }

    /** 持久化选中老人 id（登录/解绑后可调用 resetSelectedElderId 复位） */
    public void setSelectedElderId(long elderId) {
        preferences.edit().putLong(KEY_SELECTED_ELDER_ID, elderId).apply();
    }

    /** 复位选中老人：解绑后、或列表为空时回退到默认 */
    public void resetSelectedElderId() {
        preferences.edit().remove(KEY_SELECTED_ELDER_ID).apply();
    }
}
