package com.example.myapplication.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapters.HistoryAdapter;
import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.EventRecord;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 历史记录页（对齐 docs/app-integration.md 事件接口）
 * - 数据源为只读事件列表（GET /events），删除功能移除（文档无删除接口）
 * - 筛选：搜索 / 事件类型 chips（fall、sos、geofence_exit、low_battery、device_offline）/ 日期范围 chips
 * - 任一筛选变化立即刷新（输入即过滤）
 */
public class HistoryFragment extends Fragment {

    private EditText searchEdit;
    private RecyclerView historyRecycler;
    private TextView emptyView;
    private TextView recordCountText;

    private LinearLayout typeChipRow;
    private LinearLayout dateChipRow;

    private HistoryAdapter adapter;
    private final List<EventRecord> allEvents = new ArrayList<>();
    private final List<EventRecord> filteredEvents = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 类型筛选：显示中文，映射到后端事件 type key（空串 = 全部） */
    private String[] typeFilterKeys;
    private final List<TextView> typeChips = new ArrayList<>();
    private final List<TextView> dateChips = new ArrayList<>();
    private String currentTypeKey = "";
    /** 日期筛选 key：空 = 全部；today / 7d / 30d（走后端 start 参数，App 只保留搜索词本地过滤） */
    private String currentDateKey = "";

    /** 后台自动刷新间隔：10s（页面隐藏时也持续更新，切回即最新） */
    private static final long AUTO_REFRESH_INTERVAL = 10_000L;
    /** 历史页拉取上限：按文档建议取 500，避免默认 50 截断日期范围数据 */
    private static final int HISTORY_LIMIT = 500;
    private boolean refreshing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        initViews(view);
        setupChips();
        setupLiveFilter();
        refreshEvents();
        startAutoRefresh();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoRefresh();
    }

    /** 后台自动刷新：即使切到其他页也持续拉最新事件（进程存活期间保持最新记录） */
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
            refreshEvents();
            if (refreshing) {
                mainHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        }
    };

    private void initViews(View view) {
        searchEdit = view.findViewById(R.id.search_edit);
        historyRecycler = view.findViewById(R.id.history_recycler);
        emptyView = view.findViewById(R.id.empty_view);
        recordCountText = view.findViewById(R.id.record_count);
        typeChipRow = view.findViewById(R.id.type_chip_row);
        dateChipRow = view.findViewById(R.id.date_chip_row);

        historyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(filteredEvents);
        historyRecycler.setAdapter(adapter);
    }

    /** 动态构建类型与日期筛选 chips（选中态青绿实底白字，未选浅灰底深字） */
    private void setupChips() {
        // 类型筛选 chips：硬件不会主动触发 SOS（仅跌倒/围栏/电量/离线），筛选不提供 SOS 项；
        // 历史列表对 SOS 事件仍按类型名正常展示（记录展示保留）
        String[] typeLabels = new String[]{
                getString(R.string.filter_all),
                getString(R.string.event_type_fall),
                getString(R.string.event_type_geofence_exit),
                getString(R.string.event_type_low_battery),
                getString(R.string.event_type_device_offline)};
        typeFilterKeys = new String[]{
                "",
                EventRecord.TYPE_FALL,
                EventRecord.TYPE_GEOFENCE_EXIT,
                EventRecord.TYPE_LOW_BATTERY,
                EventRecord.TYPE_DEVICE_OFFLINE};

        for (int i = 0; i < typeLabels.length; i++) {
            final int index = i;
            final TextView chip = createChip(typeLabels[i], i == 0, v -> {
                currentTypeKey = typeFilterKeys[index];
                refreshChipSelection(typeChips, index);
                performSearch();
            });
            typeChipRow.addView(chip);
            typeChips.add(chip);
        }

        String[] dateLabels = new String[]{
                getString(R.string.filter_all),
                getString(R.string.date_today),
                getString(R.string.date_7days),
                getString(R.string.date_30days)};
        String[] dateKeys = new String[]{"", "today", "7d", "30d"};
        for (int i = 0; i < dateLabels.length; i++) {
            final int index = i;
            final TextView chip = createChip(dateLabels[i], i == 0, v -> {
                currentDateKey = dateKeys[index];
                refreshChipSelection(dateChips, index);
                // 日期范围走后端 start 参数（避免默认 50 条截断），变化时重新拉取
                refreshEvents();
            });
            dateChipRow.addView(chip);
            dateChips.add(chip);
        }
        currentDateKey = dateKeys[0];
    }

    private TextView createChip(String text, boolean selected, View.OnClickListener listener) {
        TextView chip = new TextView(requireContext());
        chip.setText(text);
        chip.setTextSize(13f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setBackground(ContextCompat.getDrawable(requireContext(),
                selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected));
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.white : R.color.text_primary));
        chip.setOnClickListener(listener);
        return chip;
    }

    private void refreshChipSelection(List<TextView> chips, int selectedIndex) {
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            boolean selected = i == selectedIndex;
            chip.setBackground(ContextCompat.getDrawable(requireContext(),
                    selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected));
            chip.setTextColor(ContextCompat.getColor(requireContext(),
                    selected ? R.color.white : R.color.text_primary));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 输入即过滤：搜索词 / 类型 / 日期任一变化立即刷新列表 */
    private void setupLiveFilter() {
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /** 从数据源拉取事件列表（网络请求放后台线程；日期范围走后端 start 参数） */
    private void refreshEvents() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        final long startMillis = dateRangeStartMillis();
        new Thread(() -> {
            List<EventRecord> events = service.getEvents(null, startMillis, 0L, HISTORY_LIMIT);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                allEvents.clear();
                allEvents.addAll(events);
                performSearch();
            });
        }).start();
    }

    /** 日期筛选起点（毫秒）：空=不限；today=当天 00:00；7d/30d 按自然日回退 */
    private long dateRangeStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if ("today".equals(currentDateKey)) {
            return cal.getTimeInMillis();
        }
        if ("7d".equals(currentDateKey)) {
            cal.add(Calendar.DAY_OF_YEAR, -6);
            return cal.getTimeInMillis();
        }
        if ("30d".equals(currentDateKey)) {
            cal.add(Calendar.DAY_OF_YEAR, -29);
            return cal.getTimeInMillis();
        }
        return 0L;
    }

    private void performSearch() {
        if (searchEdit == null || adapter == null) {
            return;
        }

        String query = searchEdit.getText() == null
                ? "" : searchEdit.getText().toString().trim().toLowerCase(Locale.ROOT);

        // 日期范围起点与服务端一致（服务端已按 start 过滤，本地再做一次幂等过滤）
        long rangeStart = dateRangeStartMillis();

        filteredEvents.clear();
        for (EventRecord event : allEvents) {
            boolean searchMatch = query.isEmpty() || matchesQuery(event, query);
            boolean typeMatch = currentTypeKey.isEmpty() || currentTypeKey.equals(event.getType());
            boolean dateMatch = rangeStart <= 0
                    || event.getOccurredTimeMillis() >= rangeStart;

            if (searchMatch && typeMatch && dateMatch) {
                filteredEvents.add(event);
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private boolean matchesQuery(EventRecord event, String query) {
        if (event.getType() == null) {
            return false;
        }
        // 匹配英文 type key 与中文类型名（如搜索"跌倒"可命中 fall 事件）
        String typeKey = event.getType().toLowerCase(Locale.ROOT);
        if (typeKey.contains(query)) {
            return true;
        }
        String zh = typeNameZh(event.getType());
        return zh != null && zh.toLowerCase(Locale.ROOT).contains(query);
    }

    private String typeNameZh(String type) {
        int res;
        if (EventRecord.TYPE_FALL.equals(type)) {
            res = R.string.event_type_fall;
        } else if (EventRecord.TYPE_SOS.equals(type)) {
            res = R.string.event_type_sos;
        } else if (EventRecord.TYPE_GEOFENCE_EXIT.equals(type)) {
            res = R.string.event_type_geofence_exit;
        } else if (EventRecord.TYPE_LOW_BATTERY.equals(type)) {
            res = R.string.event_type_low_battery;
        } else if (EventRecord.TYPE_DEVICE_OFFLINE.equals(type)) {
            res = R.string.event_type_device_offline;
        } else {
            return null;
        }
        return getString(res);
    }

    private void updateEmptyState() {
        boolean empty = filteredEvents.isEmpty();
        historyRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (recordCountText != null) {
            recordCountText.setText(getString(R.string.record_count, filteredEvents.size()));
        }
    }

    /** 切回本页时自动刷新（MainActivity 用 show/hide 切换，不触发 onResume，需在此拉最新事件） */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) {
            refreshEvents();
        }
    }
}
