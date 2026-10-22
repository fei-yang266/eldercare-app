package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.HealthDataService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录页（对齐 docs/app-integration.md 认证章节）
 * - 真实登录：默认连接公网后端，POST /api/v1/auth/login → 保存 Token（expires_in 计算过期）
 * - 演示模式：后端连不上时显式切 Mock 数据（saveMockSession），便于界面演示与开发联调
 * - 记住我：勾选后保存账号密码（Base64 混淆），下次打开自动填充
 */
public class LoginActivity extends AppCompatActivity {

    private EditText usernameEdit;
    private EditText passwordEdit;
    private CheckBox rememberCheck;
    private Button loginButton;
    private Button demoButton;
    private TextView goRegister;

    private static final int REQUEST_REGISTER = 1;

    /** 记住我：独立于会话存储，退出登录不清除，仅由用户取消勾选时清除 */
    private static final String PREF_REMEMBER = "login_remember";
    private static final String KEY_REMEMBER_USERNAME = "username";
    private static final String KEY_REMEMBER_PASSWORD = "password";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEdit = findViewById(R.id.username_edit);
        passwordEdit = findViewById(R.id.password_edit);
        rememberCheck = findViewById(R.id.remember_checkbox);
        loginButton = findViewById(R.id.login_button);
        demoButton = findViewById(R.id.demo_button);
        goRegister = findViewById(R.id.go_register);

        // 每次回到登录页都回到真实后端模式（演示模式为显式选择）
        HealthDataService.getInstance(this).setMockMode(false);

        // 记住我：有保存的账号则自动填充（含密码），否则预填生产家属账号用户名
        restoreRemembered();

        loginButton.setOnClickListener(v -> doLogin());
        demoButton.setOnClickListener(v -> enterDemoMode());
        goRegister.setOnClickListener(v -> startActivityForResult(
                new Intent(this, RegisterActivity.class), REQUEST_REGISTER));
    }

    /** 读取"记住我"存储：恢复勾选状态与账号密码（存储损坏时兜底，不崩溃） */
    private void restoreRemembered() {
        SharedPreferences sp = getSharedPreferences(PREF_REMEMBER, MODE_PRIVATE);
        String savedUsername = sp.getString(KEY_REMEMBER_USERNAME, null);
        if (savedUsername != null && !savedUsername.isEmpty()) {
            usernameEdit.setText(savedUsername);
            rememberCheck.setChecked(true);
            String savedPassword = sp.getString(KEY_REMEMBER_PASSWORD, null);
            if (savedPassword != null) {
                try {
                    passwordEdit.setText(new String(
                            Base64.decode(savedPassword, Base64.NO_WRAP), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    // 存储数据损坏：清除损坏项，回退到默认填充
                    sp.edit().remove(KEY_REMEMBER_PASSWORD).apply();
                    passwordEdit.setText("");
                }
            }
        } else {
            usernameEdit.setText(getString(R.string.login_default_username));
        }
    }

    /** 登录成功后：勾选则保存账号密码，未勾选则清除已保存内容 */
    private void saveRemembered(String username, String password) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_REMEMBER, MODE_PRIVATE).edit();
        if (rememberCheck.isChecked()) {
            editor.putString(KEY_REMEMBER_USERNAME, username)
                    .putString(KEY_REMEMBER_PASSWORD,
                            Base64.encodeToString(password.getBytes(StandardCharsets.UTF_8),
                                    Base64.NO_WRAP));
        } else {
            editor.remove(KEY_REMEMBER_USERNAME).remove(KEY_REMEMBER_PASSWORD);
        }
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_REGISTER && resultCode == RESULT_OK && data != null) {
            String username = data.getStringExtra("username");
            if (username != null && !username.isEmpty()) {
                usernameEdit.setText(username);
            }
        }
    }

    private void doLogin() {
        String username = usernameEdit.getText() == null
                ? "" : usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText() == null
                ? "" : passwordEdit.getText().toString();
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.login_empty_fields,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText(R.string.login_in_progress);
        HealthDataService service = HealthDataService.getInstance(this);

        // 登录涉及网络请求（Remote 模式），放到后台线程
        new Thread(() -> {
            try {
                service.login(username, password);
                mainHandler.post(() -> {
                    saveRemembered(username, password);
                    enterMain();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loginButton.setEnabled(true);
                    loginButton.setText(R.string.login_button);
                    showLoginError(e);
                });
            }
        }).start();
    }

    /** 按失败原因给出明确提示：空输入由本地校验拦截；401/404/网络/其他分别提示 */
    private void showLoginError(Exception e) {
        if (e instanceof ApiClient.ApiException) {
            int code = ((ApiClient.ApiException) e).code;
            if (code == 401) {
                Toast.makeText(this, R.string.login_bad_credentials,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (code == 404) {
                Toast.makeText(this, R.string.login_user_not_found,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (e instanceof IOException) {
            Toast.makeText(this, R.string.login_network_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this,
                getString(R.string.login_failed, e.getMessage()),
                Toast.LENGTH_SHORT).show();
    }

    /** 演示模式：后端未部署或联调界面时，显式切 Mock 数据进入 */
    private void enterDemoMode() {
        try {
            HealthDataService service = HealthDataService.getInstance(this);
            service.setMockMode(true);
            service.login("family", "family123456");
            enterMain();
        } catch (Exception e) {
            showLoginError(e);
        }
    }

    private void enterMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
