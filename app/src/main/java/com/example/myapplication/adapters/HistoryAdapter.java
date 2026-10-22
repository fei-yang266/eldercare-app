package com.example.myapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.EventRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 事件列表适配器（对齐 EventOut 结构）
 * 事件为只读数据（无删除/修改接口），行内展示类型、彩色竖条、状态标签与时间。
 * 同时被历史记录页与 SOS 页复用（SOS 页固定 type=sos 数据源）。
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<EventRecord> eventList;
    private OnItemClickListener itemClickListener;

    public interface OnItemClickListener {
        void onItemClick(EventRecord event);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public HistoryAdapter(List<EventRecord> eventList) {
        this.eventList = eventList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_history_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventRecord event = eventList.get(position);
        long millis = event.getOccurredTimeMillis();

        holder.eventTypeView.setText(typeName(holder.itemView, event.getType()));
        holder.timeView.setText(dateTimeOf(millis));

        // 按事件类型着色：彩色竖条 + 状态标签（对齐预览图配色）
        int barColor;
        int badgeRes;
        int badgeTextRes = R.string.tag_critical;
        boolean darkTextOnBadge = false;
        String type = event.getType();

        if (EventRecord.TYPE_FALL.equals(type)) {
            barColor = R.color.red;
            badgeRes = R.drawable.bg_badge_fall;
        } else if (EventRecord.TYPE_SOS.equals(type)) {
            barColor = R.color.warning;
            badgeRes = R.drawable.bg_badge_warning;
        } else if (EventRecord.TYPE_GEOFENCE_EXIT.equals(type)) {
            barColor = R.color.info;
            badgeRes = R.drawable.bg_badge_info;
            badgeTextRes = R.string.tag_attention;
        } else if (EventRecord.TYPE_LOW_BATTERY.equals(type)) {
            barColor = R.color.notice;
            badgeRes = R.drawable.bg_badge_notice;
            badgeTextRes = R.string.tag_attention;
            darkTextOnBadge = true;
        } else { // device_offline / 未知
            barColor = R.color.remind;
            badgeRes = R.drawable.bg_badge_remind;
            badgeTextRes = R.string.tag_remind;
        }

        holder.colorBar.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.getContext(), barColor));
        holder.statusBadge.setBackgroundResource(badgeRes);
        holder.statusBadge.setText(holder.itemView.getContext().getString(badgeTextRes));
        holder.statusBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                darkTextOnBadge ? R.color.text_primary : R.color.white));

        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(event);
            }
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    private String typeName(View view, String type) {
        if (view == null || view.getContext() == null) {
            return type == null ? "" : type;
        }
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
            return type == null ? "" : type;
        }
        return view.getContext().getString(res);
    }

    /** 时间展示为 "MM-dd HH:mm"（预览图单行格式） */
    private String dateTimeOf(long millis) {
        if (millis <= 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View colorBar;
        final TextView eventTypeView;
        final TextView statusBadge;
        final TextView timeView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.history_color_bar);
            eventTypeView = itemView.findViewById(R.id.history_event_type);
            statusBadge = itemView.findViewById(R.id.history_status_badge);
            timeView = itemView.findViewById(R.id.history_time);
        }
    }
}
