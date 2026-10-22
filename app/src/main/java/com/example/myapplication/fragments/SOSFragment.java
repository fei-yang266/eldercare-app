package com.example.myapplication.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapters.HistoryAdapter;
import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.EventRecord;
import com.example.myapplication.utils.FallAlertManager;
import com.example.myapplication.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SOS 页（只读，对齐 docs/app-integration.md）
 * SOS 事件由老人端设备触发，App 无制造入口（模拟按钮已删除）。
 * 展示：未处理紧急告警状态卡（联动已确认集合，确认后不再提示） +
 *       最近紧急事件列表（跌倒/SOS）+ 手动刷新。
 */
public class SOSFragment extends Fragment {

    private TextView sosStateText;
    private TextView sosStateSubtitle;
    private ImageView sosStateIcon;
    private View sosStateCard;
    private RecyclerView sosRecycler;
    private TextView sosEmptyView;

    private HistoryAdapter adapter;
    private final List<EventRecord> sosEvents = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 与弹窗确认共用同一已确认集合：确认过的紧急事件不再展示（二次确认可手动移除） */
    private FallAlertManager alertManager;

    /** 后台自动刷新间隔：10s（页面隐藏时也持续更新，切回即最新） */
    private static final long AUTO_REFRESH_INTERVAL = 10_000L;
    private boolean refreshing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sos, container, false);

        sosStateCard = view.findViewById(R.id.sos_state_card);
        sosStateIcon = view.findViewById(R.id.sos_state_icon);
        sosStateText = view.findViewById(R.id.sos_state_text);
        sosStateSubtitle = view.findViewById(R.id.sos_state_subtitle);
        sosRecycler = view.findViewById(R.id.sos_recycler);
        sosEmptyView = view.findViewById(R.id.sos_empty_view);

        sosRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(sosEvents);
        // 列表项点击 → 二次确认后从本页移除（已确认的事件不再展示）
        adapter.setOnItemClickListener(this::confirmRemoveEvent);
        sosRecycler.setAdapter(adapter);

        alertManager = FallAlertManager.getInstance(requireContext());

        view.findViewById(R.id.sos_refresh_button).setOnClickListener(v -> refreshSos());

        refreshSos();
        startAutoRefresh();
        return view;
    }

    /** 二次确认后移除紧急事件（事件接口只读，无删除能力；本页过滤移除，历史页保留记录，
     *  并联动 ack 后端对应告警，保证状态卡同步） */
    private void confirmRemoveEvent(EventRecord event) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sos_confirm_title)
                .setMessage(R.string.sos_confirm_first_msg)
                .setPositiveButton(R.string.confirm, (d, w) -> secondConfirmRemove(event))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void secondConfirmRemove(EventRecord event) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sos_confirm_title)
                .setMessage(R.string.sos_confirm_second_msg)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    alertManager.acknowledgeEvent(event.getId());
                    ackBackendAlertIfExists(event);
                    sosEvents.remove(event);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(requireContext(), R.string.sos_removed_toast,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 二次确认联动后端：找到该事件对应的未处理告警（alert.event_id 匹配且 status=new）并 ack，
     * 保证后端状态同步、SOS 页状态卡不再显示已处理告警。
     */
    private void ackBackendAlertIfExists(EventRecord event) {
        new Thread(() -> {
            HealthDataService service = HealthDataService.getInstance(requireContext());
            try {
                for (com.example.myapplication.model.AlertRecord alert : service.getAlerts()) {
                    if (alert.getEventId() == event.getId() && "new".equals(alert.getStatus())) {
                        service.ackAlert(alert.getId());
                        break;
                    }
                }
            } catch (Exception ignored) {
                // 后端不可达等异常不影响本地移除；状态卡由已确认集合兜底
            }
        }).start();
    }

    private void updateEmptyState() {
        boolean empty = sosEvents.isEmpty();
        sosRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        sosEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoRefresh();
    }

    /** 后台自动刷新：即使切到其他页也持续拉最新紧急事件（进程存活期间保持最新记录） */
    private void startAutoRefresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        mainHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    private void stopAutoRefresh() {
        refreshing = false;
        mainHandler.removeCallbacks(autoRefreshRunnable);
    }

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshSos();
            if (refreshing) {
                mainHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        }
    };

    /** 拉取紧急事件（跌倒/SOS）与未处理紧急告警状态（网络请求放后台线程，避免阻塞 UI） */
    private void refreshSos() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            // 紧急事件列表：跌倒 + SOS（对家属而言都是需要关注的紧急情况）；已确认移除的不再展示
            List<EventRecord> emergencyEvents = new ArrayList<>();
            for (EventRecord event : service.getEvents(null)) {
                boolean isEmergency = EventRecord.TYPE_FALL.equals(event.getType())
                        || EventRecord.TYPE_SOS.equals(event.getType());
                if (isEmergency && !alertManager.getAckedEventIds().contains(event.getId())) {
                    emergencyEvents.add(event);
                }
            }
            final long newestNewEmergencyTime = findNewestNewEmergencyTime(service);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                // 最近紧急事件列表
                sosEvents.clear();
                sosEvents.addAll(emergencyEvents);
                adapter.notifyDataSetChanged();
                updateEmptyState();

                // 未处理紧急告警 → 状态卡（idle 绿渐变 / active 红渐变，文字统一白色）
                if (newestNewEmergencyTime > 0L) {
                    sosStateCard.setBackgroundResource(R.drawable.bg_sos_active);
                    sosStateIcon.setImageResource(R.drawable.ic_sos_active);
                    sosStateText.setText(R.string.sos_active);
                    sosStateSubtitle.setText(getString(R.string.sos_active_time,
                            TimeUtils.formatMillis(newestNewEmergencyTime)));
                } else {
                    sosStateCard.setBackgroundResource(R.drawable.bg_sos_idle);
                    sosStateIcon.setImageResource(R.drawable.ic_sos_idle);
                    sosStateText.setText(R.string.sos_idle);
                    sosStateSubtitle.setText(R.string.sos_idle_subtitle);
                }
            });
        }).start();
    }

    /** 找未处理且未确认的紧急告警（跌倒/SOS）的最早触发时间（0 表示无）。
     *  联动已确认集合：用户确认过的事件，即使后端告警仍为 new（如 ack 未生效），也不显示红色状态卡。
     *  时间优先取 occurred_at（SPEC 06 REST 已提供），缺失回退 created_at。 */
    private long findNewestNewEmergencyTime(HealthDataService service) {
        long newest = 0L;
        Set<Long> acked = alertManager.getAckedEventIds();
        for (com.example.myapplication.model.AlertRecord alert : service.getAlerts()) {
            boolean emergencyType = EventRecord.TYPE_FALL.equals(alert.getEventType())
                    || EventRecord.TYPE_SOS.equals(alert.getEventType());
            if (emergencyType && "new".equals(alert.getStatus())
                    && !acked.contains(alert.getEventId())) {
                newest = Math.max(newest, alert.getOccurredTimeMillis());
            }
        }
        return newest;
    }

    /** 切回本页时自动刷新（MainActivity 用 show/hide 切换，不触发 onResume，需在此拉最新紧急事件） */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) {
            refreshSos();
        }
    }
}
