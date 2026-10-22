package com.example.myapplication.api;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.model.TokenResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 后端 REST 客户端（对齐 docs/app-integration.md）
 * - 除登录外一律携带 Authorization: Bearer <token>
 * - 收到 401 清理本地会话并通知上层回登录页（无 refresh token，不做重试）
 * - 错误体 detail 兼容字符串与数组两种形态
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 10;

    private final OkHttpClient client;
    private final SessionManager session;
    private UnauthorizedListener unauthorizedListener;

    public interface UnauthorizedListener {
        /** 401：令牌缺失/无效/过期，清理会话并回登录页 */
        void onUnauthorized();
    }

    public ApiClient(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.session = SessionManager.getInstance(context);
    }

    public void setUnauthorizedListener(UnauthorizedListener listener) {
        this.unauthorizedListener = listener;
    }

    /** 登录（公开接口） */
    public TokenResponse login(String username, String password) throws IOException, ApiException {
        String body;
        try {
            body = new JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString();
        } catch (JSONException e) {
            throw new ApiException(0, "请求参数构造失败");
        }
        Request request = new Request.Builder()
                .url(ApiConfig.API_BASE + "/auth/login")
                .post(RequestBody.create(body, JSON))
                .build();
        String responseBody = execute(request, false);
        try {
            JSONObject json = new JSONObject(responseBody);
            TokenResponse token = new TokenResponse();
            token.setAccessToken(json.optString("access_token"));
            token.setTokenType(json.optString("token_type"));
            token.setExpiresIn(json.optLong("expires_in"));
            token.setRole(json.optString("role"));
            return token;
        } catch (JSONException e) {
            throw new ApiException(0, "响应解析失败");
        }
    }

    /** 注册家属账号（公开接口，固定创建 family 角色；成功返回新账号信息，不含 Token） */
    public JSONObject register(String username, String displayName, String phone, String password)
            throws IOException, ApiException {
        String body;
        try {
            body = new JSONObject()
                    .put("username", username)
                    .put("display_name", displayName)
                    .put("phone", phone)
                    .put("password", password)
                    .toString();
        } catch (JSONException e) {
            throw new ApiException(0, "请求参数构造失败");
        }
        Request request = new Request.Builder()
                .url(ApiConfig.API_BASE + "/auth/register")
                .post(RequestBody.create(body, JSON))
                .build();
        String responseBody = execute(request, false);
        try {
            return new JSONObject(responseBody);
        } catch (JSONException e) {
            throw new ApiException(0, "响应解析失败");
        }
    }

    /** GET 请求（自动附加查询参数与 Bearer 头；参数值 URL 编码，防止特殊字符破坏请求） */
    public String get(String path, Map<String, String> query) throws IOException, ApiException {
        StringBuilder url = new StringBuilder(ApiConfig.API_BASE).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                url.append(entry.getKey()).append('=')
                        .append(java.net.URLEncoder.encode(
                                entry.getValue(), java.nio.charset.StandardCharsets.UTF_8.name()));
                first = false;
            }
        }
        Request request = new Request.Builder().url(url.toString()).get().build();
        return execute(request, true);
    }

    /** POST 请求；needAuth=true 时携带 Bearer 头 */
    public String post(String path, String jsonBody, boolean needAuth) throws IOException, ApiException {
        Request.Builder builder = new Request.Builder()
                .url(ApiConfig.API_BASE + path)
                .post(RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON));
        return execute(builder.build(), needAuth);
    }

    /** PUT 请求（整体替换语义）；needAuth=true 时携带 Bearer 头 */
    public String put(String path, String jsonBody, boolean needAuth) throws IOException, ApiException {
        Request.Builder builder = new Request.Builder()
                .url(ApiConfig.API_BASE + path)
                .put(RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON));
        return execute(builder.build(), needAuth);
    }

    /** DELETE 请求（204 无响应体）；needAuth=true 时携带 Bearer 头 */
    public void delete(String path, boolean needAuth) throws IOException, ApiException {
        Request.Builder builder = new Request.Builder()
                .url(ApiConfig.API_BASE + path)
                .delete();
        execute(builder.build(), needAuth);
    }

    private String execute(Request request, boolean needAuth) throws IOException, ApiException {
        Request.Builder builder = request.newBuilder();
        if (needAuth) {
            String token = session.getToken();
            if (token == null || session.isExpired()) {
                handleUnauthorized();
                throw new ApiException(401, "会话已过期，请重新登录");
            }
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) {
                return responseBody;
            }
            if (response.code() == 401) {
                handleUnauthorized();
            }
            throw new ApiException(response.code(), extractErrorMessage(responseBody));
        }
    }

    private void handleUnauthorized() {
        session.clear();
        if (unauthorizedListener != null) {
            unauthorizedListener.onUnauthorized();
        }
    }

    /** 兼容 detail 为字符串 / 数组两种错误体 */
    public static String extractErrorMessage(String body) {
        if (body == null || body.isEmpty()) {
            return "请求失败";
        }
        try {
            JSONObject json = new JSONObject(body);
            Object detail = json.opt("detail");
            if (detail instanceof String) {
                return (String) detail;
            }
            if (detail instanceof JSONArray) {
                JSONArray arr = (JSONArray) detail;
                if (arr.length() > 0) {
                    JSONObject first = arr.optJSONObject(0);
                    if (first != null) {
                        String msg = first.optString("msg");
                        if (!msg.isEmpty()) {
                            return msg;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Error body parse failed: " + body);
        }
        return "请求失败";
    }

    /** 业务异常：携带 HTTP 状态码与后端 detail 文案 */
    public static class ApiException extends Exception {
        public final int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
