package com.example.myapplication.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.LoginActivity;
import com.example.myapplication.R;
import com.example.myapplication.api.ApiConfig;
import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.Contact;
import com.example.myapplication.model.Device;
import com.example.myapplication.model.Elder;
import com.example.myapplication.model.Geofence;
import com.example.myapplication.service.AlertMonitorService;
import com.example.myapplication.utils.ElderPicker;
import com.example.myapplication.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 我的页（账号信息 + 老人管理 + 紧急联系人 + 绑定设备 + 电子围栏 + 退出登录）
 * SPEC 06：family 可查看/切换/解绑关联老人、按手机号自助绑定/新建档案、
 *          读写紧急联系人（一键拨打跳系统拨号盘）、只读设备与围栏。
 * 所有网络请求放后台线程，回调切回主线程；页面隐藏时由 onHiddenChanged 触发重载。
 */
public class ProfileFragment extends Fragment {

    private TextView usernameHeaderText;
    private TextView roleHeaderText;
    private TextView modeText;
    private TextView apiAddressText;

    private LinearLayout eldersContainer;
    private TextView eldersEmpty;
    private LinearLayout contactsContainer;
    private TextView contactsEmpty;
    private LinearLayout devicesContainer;
    private TextView devicesEmpty;
    private LinearLayout fencesContainer;
    private TextView fencesEmpty;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        usernameHeaderText = view.findViewById(R.id.profile_username_header);
        roleHeaderText = view.findViewById(R.id.profile_role_header);
        modeText = view.findViewById(R.id.profile_mode);
        apiAddressText = view.findViewById(R.id.profile_api_address);

        eldersContainer = view.findViewById(R.id.elders_container);
        eldersEmpty = view.findViewById(R.id.elders_empty);
        contactsContainer = view.findViewById(R.id.contacts_container);
        contactsEmpty = view.findViewById(R.id.contacts_empty);
        devicesContainer = view.findViewById(R.id.devices_container);
        devicesEmpty = view.findViewById(R.id.devices_empty);
        fencesContainer = view.findViewById(R.id.fences_container);
        fencesEmpty = view.findViewById(R.id.fences_empty);

        view.findViewById(R.id.logout_button).setOnClickListener(v -> confirmLogout());
        view.findViewById(R.id.elders_add_button).setOnClickListener(v ->
                ElderPicker.showAddOptions(this, this::refreshElders));
        view.findViewById(R.id.contacts_add_button).setOnClickListener(v ->
                showContactFormDialog(null));

        refreshAccount();
        refreshElders();
        refreshContacts();
        refreshDevices();
        refreshFences();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccount();
    }

    /** 切回本页时重载（MainActivity 用 show/hide 切换，不触发 onResume） */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) {
            refreshAccount();
            refreshElders();
            refreshContacts();
            refreshDevices();
            refreshFences();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void refreshAccount() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        String username = service.getSession().getUsername();
        String role = service.getSession().getRole();
        boolean mock = service.getSession().isMockSession();

        usernameHeaderText.setText(username == null ? getString(R.string.placeholder_dash) : username);
        roleHeaderText.setText("family".equals(role)
                ? getString(R.string.role_family) : getString(R.string.role_admin));
        modeText.setText(mock ? getString(R.string.mode_mock) : getString(R.string.mode_remote));
        apiAddressText.setText(ApiConfig.BASE_URL);
    }

    // ============ 关联老人 ============

    private void refreshElders() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            List<Elder> elders = new ArrayList<>(service.getElders());
            long selected = service.getSelectedElderId();
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                eldersContainer.removeAllViews();
                if (elders.isEmpty()) {
                    eldersEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                eldersEmpty.setVisibility(View.GONE);
                for (Elder elder : elders) {
                    eldersContainer.addView(buildElderRow(elder, elder.getId() == selected));
                }
            });
        }).start();
    }

    private View buildElderRow(Elder elder, boolean current) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_list_divider_bottom));

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(elder.getName() == null || elder.getName().isEmpty()
                ? getString(R.string.placeholder_dash) : elder.getName());
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setTextSize(15f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (current) {
            TextView tag = new TextView(requireContext());
            tag.setText(R.string.elder_picker_title_selected);
            tag.setTextSize(11f);
            tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            tag.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_badge_normal));
            tag.setPadding(dp(8), dp(2), dp(8), dp(2));
            top.addView(tag, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView switchBtn = new TextView(requireContext());
        switchBtn.setText(R.string.profile_elder_switch);
        switchBtn.setTextSize(13f);
        switchBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
        switchBtn.setPadding(dp(12), dp(4), dp(4), dp(4));
        switchBtn.setOnClickListener(v -> {
            HealthDataService.getInstance(requireContext()).selectElder(elder.getId());
            Toast.makeText(requireContext(),
                    getString(R.string.profile_elder_switched,
                            elder.getName() == null ? "" : elder.getName()),
                    Toast.LENGTH_SHORT).show();
            refreshElders();
        });
        top.addView(switchBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView unbindBtn = new TextView(requireContext());
        unbindBtn.setText(R.string.profile_elder_unbind);
        unbindBtn.setTextSize(13f);
        unbindBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
        unbindBtn.setPadding(dp(8), dp(4), 0, dp(4));
        unbindBtn.setOnClickListener(v -> confirmUnbind(elder));
        top.addView(unbindBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(top);
        return row;
    }

    private void confirmUnbind(Elder elder) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.unbind_confirm_title)
                .setMessage(R.string.unbind_confirm_message)
                .setPositiveButton(R.string.profile_elder_unbind, (d, w) -> doUnbind(elder))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doUnbind(Elder elder) {
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            try {
                service.unbindSelf(elder.getId());
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.unbind_success, Toast.LENGTH_SHORT).show();
                    refreshElders();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(),
                            getString(R.string.elder_op_failed,
                                    e.getMessage() == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ============ 紧急联系人 ============

    private void refreshContacts() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            List<Contact> contacts = new ArrayList<>(service.getContacts(service.getResolvedElderId()));
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                contactsContainer.removeAllViews();
                if (contacts.isEmpty()) {
                    contactsEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                contactsEmpty.setVisibility(View.GONE);
                for (Contact contact : contacts) {
                    contactsContainer.addView(buildContactRow(contact));
                }
            });
        }).start();
    }

    private View buildContactRow(Contact contact) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_list_divider_bottom));

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(contact.getName());
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setTextSize(15f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (contact.isPrimary()) {
            TextView tag = new TextView(requireContext());
            tag.setText(R.string.profile_contacts_primary);
            tag.setTextSize(11f);
            tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            tag.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_badge_warning));
            tag.setPadding(dp(8), dp(2), dp(8), dp(2));
            top.addView(tag, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        row.addView(top);

        String relation = contact.getRelation();
        TextView meta = new TextView(requireContext());
        meta.setText(relation == null || relation.isEmpty()
                ? contact.getPhone() : relation + " · " + contact.getPhone());
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        meta.setTextSize(12f);
        meta.setPadding(0, dp(2), 0, 0);
        row.addView(meta);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(4), 0, 0);

        actions.addView(actionButton(R.string.contact_call, R.color.primary,
                v -> confirmCall(contact)));
        if (!contact.isPrimary()) {
            actions.addView(actionButton(R.string.profile_contacts_primary, R.color.primary,
                    v -> setPrimaryContact(contact)));
        }
        actions.addView(actionButton(R.string.contact_delete, R.color.red,
                v -> deleteContact(contact)));

        row.addView(actions);
        return row;
    }

    private TextView actionButton(int textRes, int colorRes, View.OnClickListener listener) {
        TextView btn = new TextView(requireContext());
        btn.setText(textRes);
        btn.setTextSize(13f);
        btn.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        btn.setPadding(0, dp(6), dp(16), dp(4));
        btn.setOnClickListener(listener);
        return btn;
    }

    /** 一键拨打：ACTION_DIAL 跳系统拨号盘（无需权限；ACTION_CALL 需 CALL_PHONE 权限，首版用 DIAL） */
    private void confirmCall(Contact contact) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.contact_call_title, contact.getName()))
                .setMessage(getString(R.string.contact_call_msg,
                        contact.getName(), contact.getPhone()))
                .setPositiveButton(R.string.contact_call, (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL,
                            Uri.parse("tel:" + Uri.encode(contact.getPhone())));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setPrimaryContact(Contact target) {
        updateContacts(list -> {
            for (Contact c : list) {
                c.setPrimary(c.getId() == target.getId());
            }
        });
    }

    private void deleteContact(Contact target) {
        updateContacts(list -> list.removeIf(c -> c.getId() == target.getId()));
    }

    /** 添加联系人表单（contact 为 null 表示新增） */
    private void showContactFormDialog(@Nullable Contact existing) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint(R.string.contact_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null) {
            nameInput.setText(existing.getName());
        }

        EditText phoneInput = new EditText(requireContext());
        phoneInput.setHint(R.string.contact_phone_hint);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        if (existing != null) {
            phoneInput.setText(existing.getPhone());
        }

        EditText relationInput = new EditText(requireContext());
        relationInput.setHint(R.string.contact_relation_hint);
        relationInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null && existing.getRelation() != null) {
            relationInput.setText(existing.getRelation());
        }

        root.addView(nameInput, matchWrap(20, 0));
        root.addView(phoneInput, matchWrap(20, 0));
        root.addView(relationInput, matchWrap(0, 0));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.profile_contacts_add : R.string.profile_contacts_edit)
                .setView(root)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = textOf(nameInput);
                    String phone = textOf(phoneInput);
                    String relation = textOf(relationInput);
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.contact_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (phone.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.contact_phone_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateContacts(list -> {
                        if (existing != null) {
                            for (Contact c : list) {
                                if (c.getId() == existing.getId()) {
                                    c.setName(name);
                                    c.setPhone(phone);
                                    c.setRelation(relation.isEmpty() ? null : relation);
                                }
                            }
                        } else {
                            list.add(new Contact(0L, name, phone,
                                    relation.isEmpty() ? null : relation,
                                    list.size() + 1, list.isEmpty()));
                        }
                    });
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /** 联系人变更统一入口：后台线程改造列表 → 整体替换保存（PUT 语义） → 主线程刷新 */
    private void updateContacts(java.util.function.Consumer<List<Contact>> mutator) {
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            try {
                long elderId = service.getResolvedElderId();
                List<Contact> contacts = new ArrayList<>(service.getContacts(elderId));
                mutator.accept(contacts);
                service.saveContacts(elderId, contacts);
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.contact_saved, Toast.LENGTH_SHORT).show();
                    refreshContacts();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(),
                            getString(R.string.elder_op_failed,
                                    e.getMessage() == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ============ 绑定设备（只读） ============

    private void refreshDevices() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            List<Device> devices = new ArrayList<>(service.getDevices(service.getResolvedElderId()));
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                devicesContainer.removeAllViews();
                if (devices.isEmpty()) {
                    devicesEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                devicesEmpty.setVisibility(View.GONE);
                for (Device device : devices) {
                    devicesContainer.addView(buildDeviceRow(device));
                }
            });
        }).start();
    }

    private View buildDeviceRow(Device device) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_list_divider_bottom));

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView uid = new TextView(requireContext());
        uid.setText(device.getDeviceUid());
        uid.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        uid.setTextSize(14f);
        uid.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(uid, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = new TextView(requireContext());
        status.setText(deviceStatusName(device.getStatus()));
        status.setTextSize(12f);
        boolean online = "online".equals(device.getStatus());
        status.setTextColor(ContextCompat.getColor(requireContext(),
                online ? R.color.green : R.color.text_secondary));
        top.addView(status);
        row.addView(top);

        String kind = deviceKindName(device.getKind());
        StringBuilder meta = new StringBuilder();
        if (kind != null) {
            meta.append(kind);
        }
        if (device.getBatteryPercent() != null) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(getString(R.string.device_battery, device.getBatteryPercent()));
        }
        TextView metaText = new TextView(requireContext());
        metaText.setText(meta.toString());
        metaText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        metaText.setTextSize(12f);
        metaText.setPadding(0, dp(2), 0, 0);
        row.addView(metaText);

        if (device.getLastSeenAt() != null && !device.getLastSeenAt().isEmpty()) {
            long seen = TimeUtils.parseIsoToMillis(device.getLastSeenAt());
            TextView seenText = new TextView(requireContext());
            seenText.setText(getString(R.string.device_last_seen,
                    seen > 0 ? TimeUtils.formatMillis(seen) : getString(R.string.placeholder_dash)));
            seenText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            seenText.setTextSize(12f);
            seenText.setPadding(0, dp(2), 0, 0);
            row.addView(seenText);
        }
        return row;
    }

    private String deviceStatusName(String status) {
        if ("online".equals(status)) {
            return getString(R.string.device_status_online);
        }
        if ("disabled".equals(status)) {
            return getString(R.string.device_status_disabled);
        }
        return getString(R.string.device_status_offline);
    }

    private String deviceKindName(String kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "esp32":
                return getString(R.string.device_kind_esp32);
            case "phone":
                return getString(R.string.device_kind_phone);
            case "wearable":
                return getString(R.string.device_kind_wearable);
            case "lora_node":
                return getString(R.string.device_kind_lora_node);
            default:
                return kind;
        }
    }

    // ============ 电子围栏（只读） ============

    private void refreshFences() {
        if (getContext() == null) {
            return;
        }
        HealthDataService service = HealthDataService.getInstance(requireContext());
        new Thread(() -> {
            List<Geofence> fences = new ArrayList<>(service.getGeofences(service.getResolvedElderId()));
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                fencesContainer.removeAllViews();
                if (fences.isEmpty()) {
                    fencesEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                fencesEmpty.setVisibility(View.GONE);
                for (Geofence fence : fences) {
                    fencesContainer.addView(buildFenceRow(fence));
                }
            });
        }).start();
    }

    private View buildFenceRow(Geofence fence) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_list_divider_bottom));

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(fence.getName());
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setTextSize(14f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView enabled = new TextView(requireContext());
        enabled.setText(fence.isEnabled() ? R.string.fence_enabled : R.string.fence_disabled);
        enabled.setTextSize(12f);
        enabled.setTextColor(ContextCompat.getColor(requireContext(),
                fence.isEnabled() ? R.color.green : R.color.text_secondary));
        top.addView(enabled);
        row.addView(top);

        TextView detail = new TextView(requireContext());
        detail.setText(getString(R.string.fence_detail,
                String.format(Locale.getDefault(), "%.4f", fence.getCenterLat()),
                String.format(Locale.getDefault(), "%.4f", fence.getCenterLon()),
                fence.getRadiusM()));
        detail.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        detail.setTextSize(12f);
        detail.setPadding(0, dp(2), 0, 0);
        row.addView(detail);
        return row;
    }

    // ============ 通用 ============

    private void confirmLogout() {
        if (getContext() == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.logout_button, (dialog, which) -> doLogout())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doLogout() {
        HealthDataService.getInstance(requireContext()).logout();
        // L1：停止前台监控服务，避免退出登录后后台空转
        AlertMonitorService.stop(requireContext());
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private String textOf(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
