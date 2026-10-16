/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.security;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;

/** Owner-authenticated configuration. New duress values are never tried as unlock credentials. */
@SearchIndexable
public final class DuressPasswordSettings extends SettingsPreferenceFragment {
    private static final int CONFIRM_OWNER = 301;
    private static final int CONFIGURE = 1;
    private static final int DISABLE = 2;
    private LockPatternUtils mUtils;
    private Preference mStatus;
    private Preference mConfigure;
    private Preference mDisable;
    private AlertDialog mDialog;
    private EditText mInput;
    private LockscreenCredential mOwner;
    private LockscreenCredential mFirst;
    private LockscreenCredential mPin;
    private LockscreenCredential mPassword;
    private int mAction;
    private int mStep;
    private int mOperation;
    private boolean mAwaitingOwner;
    private boolean mVisible;
    private boolean mBusy;
    private boolean mOriginallySecure;

    @Override
    public int getMetricsCategory() { return SettingsEnums.SECURITY; }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mUtils = new LockPatternUtils(requireContext());
        addPreferencesFromResource(R.xml.arkui_duress_password_settings);
        mStatus = findPreference("duress_status");
        mConfigure = findPreference("duress_configure");
        mDisable = findPreference("duress_disable");
        mConfigure.setOnPreferenceClickListener(preference -> {
            showWarning();
            return true;
        });
        mDisable.setOnPreferenceClickListener(preference -> {
            authenticate(DISABLE);
            return true;
        });
        if (state != null) {
            mAction = state.getInt("action");
            mAwaitingOwner = state.getBoolean("awaiting_owner");
        }
        mOriginallySecure = (requireActivity().getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SECURE) != 0;
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        requireActivity().getWindow().setHideOverlayWindows(true);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        view.setFilterTouchesWhenObscured(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVisible = true;
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            finish();
            return;
        }
        if (!StorageManager.isFileEncrypted()) {
            toast(R.string.arkui_duress_encryption_required);
            finish();
            return;
        }
        requireActivity().setTitle(R.string.arkui_duress_title);
        refresh();
        resumeFlow();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        // Never parcel the owner credential or any partially entered duress credentials.
        state.putInt("action", mAction);
        state.putBoolean("awaiting_owner", mAwaitingOwner);
    }

    @Override
    public void onPause() {
        mVisible = false;
        ++mOperation;
        clearCredentials();
        dismissDialog();
        mBusy = false;
        if (!mAwaitingOwner) mAction = 0;
        super.onPause();
    }

    @Override
    public void onDestroy() {
        clearCredentials();
        if (getActivity() != null && !mOriginallySecure) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onDestroy();
    }

    private void refresh() {
        try {
            boolean supported = mUtils.isLockPasswordEnabled(UserHandle.USER_SYSTEM);
            boolean enabled = mUtils.hasDuressCredentials();
            mStatus.setTitle(enabled ? R.string.arkui_duress_enabled
                    : R.string.arkui_duress_disabled);
            mStatus.setSummary(supported ? R.string.arkui_duress_warning
                    : R.string.arkui_duress_lock_required);
            mConfigure.setTitle(enabled ? R.string.arkui_duress_change
                    : R.string.arkui_duress_configure);
            mConfigure.setEnabled(supported && !mBusy);
            mDisable.setVisible(enabled);
            mDisable.setEnabled(!mBusy);
        } catch (RuntimeException e) {
            toast(R.string.arkui_duress_error);
            finish();
        }
    }

    private void showWarning() {
        if (!mVisible || mBusy || mDialog != null || mAwaitingOwner) return;
        mDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.arkui_duress_title)
                .setMessage(R.string.arkui_duress_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.arkui_duress_continue,
                        (dialog, which) -> authenticate(CONFIGURE)).create();
        showSecureDialog();
    }

    private void authenticate(int action) {
        if (!mVisible || mBusy || mAwaitingOwner) return;
        if (!StorageManager.isFileEncrypted()) {
            toast(R.string.arkui_duress_encryption_required);
            return;
        }
        mAction = action;
        mAwaitingOwner = true;
        boolean launched = new ChooseLockSettingsHelper.Builder(requireActivity(), this)
                .setRequestCode(CONFIRM_OWNER)
                .setTitle(getString(R.string.arkui_duress_confirm_owner))
                .setUserId(UserHandle.USER_SYSTEM)
                .setReturnCredentials(true)
                .setForegroundOnly(true)
                .show();
        if (!launched) {
            mAwaitingOwner = false;
            mAction = 0;
            toast(R.string.arkui_duress_lock_required);
        }
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CONFIRM_OWNER || !mAwaitingOwner) return;
        mAwaitingOwner = false;
        if (result != Activity.RESULT_OK || data == null) {
            mAction = 0;
            return;
        }
        mOwner = data.getParcelableExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD,
                LockscreenCredential.class);
        data.removeExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
        if (mOwner == null) {
            mAction = 0;
            toast(R.string.arkui_duress_error);
        } else if (mVisible) {
            resumeFlow();
        }
    }

    private void resumeFlow() {
        if (mOwner == null || mDialog != null || mBusy) return;
        if (mAction == DISABLE) {
            mDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.arkui_duress_disable)
                    .setMessage(R.string.arkui_duress_disable_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.arkui_duress_disable,
                            (dialog, which) -> save(false)).create();
            showSecureDialog();
            return;
        }
        if (mAction != CONFIGURE) {
            clearCredentials();
            return;
        }
        mStep = 0;
        mInput = new EditText(requireContext());
        mInput.setSingleLine(true);
        mInput.setSaveEnabled(false);
        mInput.setSaveFromParentEnabled(false);
        mInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        mInput.setFilterTouchesWhenObscured(true);
        mInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(128) });
        mInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        mInput.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            if (mStep < 4) nextStep();
            return true;
        });
        LinearLayout container = new LinearLayout(requireContext());
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);
        container.setSaveFromParentEnabled(false);
        container.addView(mInput, new LinearLayout.LayoutParams(-1, -2));
        mDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.arkui_duress_enter_pin)
                .setMessage(R.string.arkui_duress_input_help)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.arkui_duress_continue, null).create();
        showSecureDialog();
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> nextStep());
        updateStep();
    }

    private void updateStep() {
        int[] titles = { R.string.arkui_duress_enter_pin, R.string.arkui_duress_repeat_pin,
                R.string.arkui_duress_enter_password, R.string.arkui_duress_repeat_password,
                R.string.arkui_duress_enable };
        mDialog.setTitle(titles[mStep]);
        mDialog.setMessage(getString(mStep == 4 ? R.string.arkui_duress_warning
                : R.string.arkui_duress_input_help));
        mInput.setText("");
        mInput.setError(null);
        mInput.setVisibility(mStep == 4 ? View.GONE : View.VISIBLE);
        mInput.setInputType(mStep < 2
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mInput.setHint(titles[mStep]);
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(mStep == 4
                ? R.string.arkui_duress_enable : R.string.arkui_duress_continue);
        if (mStep < 4) mInput.requestFocus();
    }

    private void nextStep() {
        if (mBusy || mOwner == null) return;
        if (mStep == 4) {
            save(true);
            return;
        }
        CharSequence text = mInput.getText();
        boolean valid = text.length() >= 4 && text.length() <= 128;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            valid &= mStep < 2 ? c >= '0' && c <= '9' : c >= 32 && c <= 126;
        }
        if (!valid) {
            mInput.setError(getString(R.string.arkui_duress_input_help));
            return;
        }
        LockscreenCredential value = mStep < 2 ? LockscreenCredential.createPin(text)
                : LockscreenCredential.createPassword(text);
        mInput.setText("");
        if (Arrays.equals(value.getCredential(), mOwner.getCredential())) {
            value.zeroize();
            mInput.setError(getString(R.string.arkui_duress_same_as_lock));
            return;
        }
        if ((mStep & 1) == 0) {
            mFirst = value;
        } else {
            boolean same = mFirst.equals(value);
            value.zeroize();
            if (!same) {
                mInput.setError(getString(R.string.arkui_duress_mismatch));
                return;
            }
            if (mStep == 1) mPin = mFirst;
            else mPassword = mFirst;
            mFirst = null;
        }
        ++mStep;
        updateStep();
    }

    private void save(boolean enable) {
        if (mBusy || mOwner == null) return;
        LockscreenCredential owner = mOwner.duplicate();
        LockscreenCredential pin = enable ? mPin.duplicate() : LockscreenCredential.createNone();
        LockscreenCredential password = enable ? mPassword.duplicate()
                : LockscreenCredential.createNone();
        mBusy = true;
        int operation = ++mOperation;
        dismissDialog();
        clearCredentials();
        mAction = 0;
        refresh();
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean success;
            try (owner; pin; password) {
                mUtils.setDuressCredentials(owner, pin, password);
                success = true;
            } catch (RuntimeException e) {
                success = false;
            }
            boolean saved = success;
            ThreadUtils.postOnMainThread(() -> {
                if (!mVisible || operation != mOperation) return;
                mBusy = false;
                toast(saved ? (enable ? R.string.arkui_duress_saved
                        : R.string.arkui_duress_disabled) : R.string.arkui_duress_error);
                refresh();
            });
        });
    }

    private void showSecureDialog() {
        final AlertDialog dialog = mDialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (mDialog != dialog) return;
            mDialog = null;
            if (mInput != null) mInput.setText("");
            mInput = null;
            if (!mAwaitingOwner && !mBusy) {
                clearCredentials();
                mAction = 0;
            }
        });
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getWindow().setHideOverlayWindows(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }

    private void dismissDialog() {
        if (mDialog != null) {
            AlertDialog dialog = mDialog;
            mDialog = null;
            dialog.dismiss();
        }
        if (mInput != null) mInput.setText("");
        mInput = null;
    }

    private void clearCredentials() {
        if (mOwner != null) mOwner.zeroize();
        if (mFirst != null) mFirst.zeroize();
        if (mPin != null) mPin.zeroize();
        if (mPassword != null) mPassword.zeroize();
        mOwner = mFirst = mPin = mPassword = null;
    }

    private void toast(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_duress_password_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return UserHandle.myUserId() == UserHandle.USER_SYSTEM
                            && StorageManager.isFileEncrypted();
                }
            };
}
