package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.api.HealthDataService;

/**
 * 注册页：POST /api/v1/auth/register（公开接口）
 * 固定创建 family 角色账号，账号落后端库；注册成功后回登录页并预填用户名。
 * 设备绑定不在 App 内（仅管理台 admin 可绑），注册页文案已说明。
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText usernameEdit;
    private EditText displayNameEdit;
    private EditText phoneEdit;
    private EditText passwordEdit;
    private EditText confirmEdit;
    private Button registerButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        usernameEdit = findViewById(R.id.register_username);
        displayNameEdit = findViewById(R.id.register_display_name);
        phoneEdit = findViewById(R.id.register_phone);
        passwordEdit = findViewById(R.id.register_password);
        confirmEdit = findViewById(R.id.register_confirm);
        registerButton = findViewById(R.id.register_button);

        registerButton.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String username = text(usernameEdit);
        String displayName = text(displayNameEdit);
        String phone = text(phoneEdit);
        String password = text(passwordEdit);
        String confirm = text(confirmEdit);

        // 校验对齐后端 auth/register（SPEC 06）：
        // username 3-32 位字母/数字/下划线/连字符；display_name/phone 可选；phone ≤32 字符不校验格式；
        // password 6-64 字符且不超过 bcrypt 72 字节（中文按 UTF-8 字节计）
        if (!username.matches("^[A-Za-z0-9_-]{3,32}$")) {
            toast(R.string.register_invalid_username);
            return;
        }
        if (!phone.isEmpty() && phone.length() > 32) {
            toast(R.string.register_invalid_phone);
            return;
        }
        if (password.length() < 6 || password.length() > 64) {
            toast(R.string.register_invalid_password);
            return;
        }
        if (utf8Length(password) > 72) {
            toast(R.string.register_invalid_password);
            return;
        }
        if (!password.equals(confirm)) {
            toast(R.string.register_password_mismatch);
            return;
        }

        registerButton.setEnabled(false);
        registerButton.setText(R.string.register_in_progress);
        HealthDataService service = HealthDataService.getInstance(this);

        // 注册涉及网络请求，放后台线程
        new Thread(() -> {
            try {
                service.register(username, displayName.isEmpty() ? null : displayName,
                        phone.isEmpty() ? null : phone, password);
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                    Intent data = new Intent();
                    data.putExtra("username", username);
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    registerButton.setEnabled(true);
                    registerButton.setText(R.string.register_button);
                    String msg = e.getMessage();
                    Toast.makeText(this,
                            getString(R.string.register_failed,
                                    msg == null ? "" : msg),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** 字符串 UTF-8 编码字节数（对齐 bcrypt 72 字节上限） */
    private static int utf8Length(String s) {
        try {
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            return s.length();
        }
    }

    private String text(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
