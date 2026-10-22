package com.example.myapplication.utils;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.api.HealthDataService;
import com.example.myapplication.model.Elder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 老人选择与添加入口（SPEC 06：family 可关联多名老人、按手机号自助绑定、新建档案）
 * - showPicker：弹出当前账号可见老人单选列表（当前选中项打勾），选中即切换全局查看对象
 * - showAddOptions：弹出"按手机号绑定 / 新建档案"两个添加入口
 * 网络请求放后台线程，回调切回主线程；对话框基于 Fragment 的 context 构建。
 */
public final class ElderPicker {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface OnElderSelected {
        /** 老人切换成功（已持久化选中 id） */
        void onSelected(Elder elder);
    }

    public interface OnChanged {
        /** 老人列表发生变化（绑定/新建/解绑成功），调用方应刷新 */
        void onChanged();
    }

    private ElderPicker() {
    }

    /** 弹出老人选择列表；无可见老人时提示引导添加 */
    public static void showPicker(@NonNull Fragment fragment, @NonNull OnElderSelected callback) {
        final HealthDataService service = HealthDataService.getInstance(fragment.requireContext());
        new Thread(() -> {
            final List<Elder> elders = new ArrayList<>(service.getElders());
            final long selected = service.getSelectedElderId();
            MAIN.post(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (elders.isEmpty()) {
                    showAddOptions(fragment, null);
                    return;
                }
                String[] names = new String[elders.size()];
                int checked = 0;
                for (int i = 0; i < elders.size(); i++) {
                    Elder e = elders.get(i);
                    names[i] = e.getName() == null || e.getName().isEmpty()
                            ? fragment.getString(R.string.placeholder_dash) : e.getName();
                    if (e.getId() == selected) {
                        checked = i;
                    }
                }
                new AlertDialog.Builder(fragment.requireContext())
                        .setTitle(R.string.elder_picker_title)
                        .setSingleChoiceItems(names, checked, (dialog, which) -> {
                            Elder chosen = elders.get(which);
                            service.selectElder(chosen.getId());
                            dialog.dismiss();
                            callback.onSelected(chosen);
                        })
                        .setNeutralButton(R.string.elder_add, (dialog, which) ->
                                showAddOptions(fragment, null))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    /** 弹出添加老人方式选择：按手机号绑定 / 新建档案 */
    public static void showAddOptions(@NonNull Fragment fragment, @Nullable OnChanged onChanged) {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.elder_add)
                .setItems(new String[]{
                        fragment.getString(R.string.elder_add_by_phone),
                        fragment.getString(R.string.elder_create_new)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showBindByPhoneDialog(fragment, onChanged);
                    } else {
                        showCreateElderDialog(fragment, onChanged);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 按老人手机号自助绑定已有档案（POST /elders/add-by-phone） */
    private static void showBindByPhoneDialog(@NonNull Fragment fragment, @Nullable OnChanged onChanged) {
        final EditText input = new EditText(fragment.requireContext());
        input.setHint(R.string.elder_phone_hint);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout layout = new LinearLayout(fragment.requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(input, matchWrap(20, 0));

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.elder_add_by_phone)
                .setMessage(R.string.elder_bind_by_phone_desc)
                .setView(layout)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String phone = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!phone.matches("^1[3-9]\\d{9}$")) {
                        toast(fragment, R.string.elder_phone_invalid);
                        return;
                    }
                    submit(fragment, () -> {
                        HealthDataService service = HealthDataService.getInstance(fragment.requireContext());
                        Elder elder = service.addElderByPhone(phone);
                        if (elder != null && elder.getId() > 0) {
                            service.selectElder(elder.getId());
                        }
                        return null;
                    }, R.string.elder_bind_success, onChanged, dialog);
                }));
        dialog.show();
    }

    /** 新建老人档案（POST /elders，phone 必填、全局唯一） */
    private static void showCreateElderDialog(@NonNull Fragment fragment, @Nullable OnChanged onChanged) {
        LinearLayout root = new LinearLayout(fragment.requireContext());
        root.setOrientation(LinearLayout.VERTICAL);

        EditText nameInput = new EditText(fragment.requireContext());
        nameInput.setHint(R.string.elder_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);

        EditText phoneInput = new EditText(fragment.requireContext());
        phoneInput.setHint(R.string.elder_phone_hint);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText genderInput = new EditText(fragment.requireContext());
        genderInput.setHint(R.string.elder_gender_hint);
        genderInput.setInputType(InputType.TYPE_CLASS_TEXT);

        root.addView(nameInput, matchWrap(20, 0));
        root.addView(phoneInput, matchWrap(20, 0));
        root.addView(genderInput, matchWrap(0, 0));

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.elder_create_new)
                .setMessage(R.string.elder_create_desc)
                .setView(root)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = textOf(nameInput);
                    String phone = textOf(phoneInput);
                    String gender = textOf(genderInput);
                    if (name.isEmpty()) {
                        toast(fragment, R.string.elder_name_required);
                        return;
                    }
                    if (!phone.matches("^1[3-9]\\d{9}$")) {
                        toast(fragment, R.string.elder_phone_invalid);
                        return;
                    }
                    submit(fragment, () -> {
                        HealthDataService service = HealthDataService.getInstance(fragment.requireContext());
                        Elder elder = service.createElder(name, phone, gender);
                        if (elder != null && elder.getId() > 0) {
                            service.selectElder(elder.getId());
                        }
                        return null;
                    }, R.string.elder_create_success, onChanged, dialog);
                }));
        dialog.show();
    }

    /** 通用提交：后台线程执行 action，成功/失败分别提示（校验失败时弹窗保持不关） */
    private static void submit(Fragment fragment, Callable<Object> action, int successRes,
                               OnChanged onChanged, AlertDialog dialog) {
        new Thread(() -> {
            try {
                action.call();
                MAIN.post(() -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    dialog.dismiss();
                    Toast.makeText(fragment.requireContext(), successRes, Toast.LENGTH_SHORT).show();
                    if (onChanged != null) {
                        onChanged.onChanged();
                    }
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    Toast.makeText(fragment.requireContext(),
                            fragment.getString(R.string.elder_op_failed,
                                    e.getMessage() == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static String textOf(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private static void toast(Fragment fragment, int resId) {
        Toast.makeText(fragment.requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private static LinearLayout.LayoutParams matchWrap(int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        return lp;
    }
}
