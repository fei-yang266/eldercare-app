package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.fragments.HistoryFragment;
import com.example.myapplication.fragments.ProfileFragment;
import com.example.myapplication.fragments.RealTimeFragment;
import com.example.myapplication.fragments.SOSFragment;
import com.example.myapplication.service.AlertMonitorService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主界面：底部导航 + Fragment 容器
 * - 登录校验：未登录 / 会话过期 → 回登录页
 * - 401 / WS 4401 会话失效：清理会话并回登录页（主线程回调）
 * - L1 系统通知：登录后启动前台服务 AlertMonitorService（WS+轮询常驻），并申请通知权限
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_NOTIFICATION_PERMISSION = 100;

    private BottomNavigationView bottomNavigationView;

    private RealTimeFragment realtimeFragment;
    private HistoryFragment historyFragment;
    private SOSFragment sosFragment;
    private ProfileFragment profileFragment;

    private Fragment currentFragment;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 会话失效防抖：多个轮询线程并发收到 401 时只跳一次登录页 */
    private boolean sessionInvalidHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HealthDataService service = HealthDataService.getInstance(this);
        if (!service.isLoggedIn()) {
            goToLogin();
            return;
        }
        // 会话失效（401 / WS 4401/4403）统一回登录页
        service.setSessionInvalidListener(this::onSessionInvalid);

        // L1：登录成功后启动前台服务（WS + 告警轮询常驻，退后台/锁屏仍监控并系统通知）
        AlertMonitorService.start(this);
        requestNotificationPermissionIfNeeded();
        requestIgnoreBatteryOptimizationsIfNeeded();

        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this::onNavItemSelected);

        // 默认展示实时监测页
        showFragment(getRealtimeFragment());
    }

    /** Android 13+ 运行时通知权限：拒绝仅降级（App 内已无弹窗，通知为唯一触达通道，建议允许） */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATION_PERMISSION);
        }
    }

    /** Android 6+ 电池优化白名单：缓解 Doze 休眠下网络受限导致的告警通知延迟/丢失（国产 ROM 后台清理） */
    private void requestIgnoreBatteryOptimizationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            // 部分 ROM 不支持该意图（无豁免入口），静默忽略，服务 START_STICKY + 自重启兜底
            Log.w(TAG, "ignore battery optimizations request failed", e);
        }
    }

    private void onSessionInvalid() {
        // ApiClient 的 401 可能发生在工作线程，切回主线程再跳转；并发 401 只处理一次
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed() || sessionInvalidHandled) {
                return;
            }
            sessionInvalidHandled = true;
            // 会话失效：停止前台监控服务，避免无 Token 空转轮询
            AlertMonitorService.stop(this);
            goToLogin();
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_realtime) {
            return showFragment(getRealtimeFragment());
        } else if (id == R.id.nav_history) {
            return showFragment(getHistoryFragment());
        } else if (id == R.id.nav_sos) {
            return showFragment(getSosFragment());
        } else if (id == R.id.nav_profile) {
            return showFragment(getProfileFragment());
        }
        return false;
    }

    /** 懒加载：首次选中才创建 Fragment，视图创建一次后长期保留 */
    private RealTimeFragment getRealtimeFragment() {
        if (realtimeFragment == null) {
            realtimeFragment = new RealTimeFragment();
        }
        return realtimeFragment;
    }

    private HistoryFragment getHistoryFragment() {
        if (historyFragment == null) {
            historyFragment = new HistoryFragment();
        }
        return historyFragment;
    }

    private SOSFragment getSosFragment() {
        if (sosFragment == null) {
            sosFragment = new SOSFragment();
        }
        return sosFragment;
    }

    private ProfileFragment getProfileFragment() {
        if (profileFragment == null) {
            profileFragment = new ProfileFragment();
        }
        return profileFragment;
    }

    /** show/hide 切换：保留各页 UI 状态与数据，避免切页重建视图 */
    private boolean showFragment(Fragment fragment) {
        if (fragment == null || fragment == currentFragment) {
            return fragment != null;
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (fragment.isAdded()) {
            ft.show(fragment);
        } else {
            ft.add(R.id.content_fragment, fragment);
        }
        if (currentFragment != null && currentFragment.isAdded()) {
            ft.hide(currentFragment);
        }
        currentFragment = fragment;
        ft.commit();
        return true;
    }
}
