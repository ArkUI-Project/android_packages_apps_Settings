/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.app.PrivacyPasswordManager;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Standard Settings page; credential changes always require a fresh verification. */
public final class PrivacyPasswordSettings extends SettingsPreferenceFragment {
    private final IBinder mOwner = new Binder();
    private PrivacyPasswordManager mManager;
    private IBinder mAuthorization;
    private AlertDialog mDialog;
    private Preference mStatus;
    private Preference mChange;
    private Preference mDisable;
    private boolean mOriginalSecure;
    private boolean mVisible;
    private boolean mBusy;
    private int mOperation;

    @Override public int getMetricsCategory() { return SettingsEnums.SECURITY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mManager = new PrivacyPasswordManager(requireContext());
        addPreferencesFromResource(R.xml.arkui_privacy_password_settings);
        mStatus = findPreference("privacy_password_status");
        mChange = findPreference("privacy_password_change");
        mDisable = findPreference("privacy_password_disable");
        mChange.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), PrivacyPasswordActivity.class)
                    .setAction(PrivacyPasswordActivity.ACTION_SET_PASSWORD));
            return true;
        });
        mDisable.setOnPreferenceClickListener(preference -> {
            authenticateForDisable();
            return true;
        });
        mOriginalSecure = (requireActivity().getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SECURE) != 0;
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        requireActivity().getWindow().setHideOverlayWindows(true);
    }

    @Override public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        view.setFilterTouchesWhenObscured(true);
    }

    @Override public void onResume() {
        super.onResume();
        mVisible = true;
        if (requireContext().getSystemService(UserManager.class).isManagedProfile()) {
            finish();
            return;
        }
        requireActivity().setTitle(R.string.arkui_privacy_password_title);
        refresh();
    }

    @Override public void onPause() {
        mVisible = false;
        ++mOperation;
        endAuthorization();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        mBusy = false;
        super.onPause();
    }

    @Override public void onDestroy() {
        endAuthorization();
        if (!mOriginalSecure && getActivity() != null) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onDestroy();
    }

    private void refresh() {
        try {
            boolean configured = mManager.getState(UserHandle.myUserId())
                    .getBoolean(PrivacyPasswordManager.KEY_CONFIGURED);
            mStatus.setTitle(configured ? R.string.arkui_privacy_password_enabled
                    : R.string.arkui_privacy_password_not_set);
            mStatus.setSummary(configured ? R.string.arkui_privacy_password_enabled_summary
                    : R.string.arkui_privacy_password_intro);
            mChange.setTitle(configured ? R.string.arkui_privacy_password_change
                    : R.string.arkui_privacy_password_create);
            mDisable.setVisible(configured);
            getPreferenceScreen().setEnabled(!mBusy);
        } catch (RuntimeException error) {
            toast(R.string.arkui_privacy_password_unavailable);
            finish();
        }
    }

    private void authenticateForDisable() {
        if (!mVisible || mBusy || mDialog != null) return;
        try {
            Bundle state = mManager.getState(UserHandle.myUserId());
            if (!state.getBoolean(PrivacyPasswordManager.KEY_CONFIGURED)) {
                refresh();
                return;
            }
            mAuthorization = mManager.beginAuthentication(UserHandle.myUserId(), mOwner);
            final IBinder challenge = mAuthorization;
            mDialog = PrivacyPasswordDialog.show(requireContext(),
                    state.getInt(PrivacyPasswordManager.KEY_TYPE),
                    password -> mManager.verify(challenge, password),
                    response -> {
                        mDialog = null;
                        if (!mVisible) {
                            endAuthorization();
                            return;
                        }
                        mAuthorization = response.getBinder(PrivacyPasswordManager.KEY_TOKEN);
                        confirmDisable();
                    }, () -> {
                        mDialog = null;
                        endAuthorization();
                    });
        } catch (RuntimeException error) {
            endAuthorization();
            toast(R.string.arkui_privacy_password_unavailable);
        }
    }

    private void confirmDisable() {
        if (mAuthorization == null) return;
        mDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.arkui_privacy_password_disable)
                .setMessage(R.string.arkui_privacy_password_disable_summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.arkui_privacy_password_disable,
                        (dialog, button) -> disablePassword()).create();
        mDialog.setOnDismissListener(dialog -> {
            mDialog = null;
            if (!mBusy) endAuthorization();
        });
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.show();
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        mDialog.getWindow().setHideOverlayWindows(true);
    }

    private void disablePassword() {
        if (!mVisible || mBusy || mAuthorization == null) return;
        final IBinder authorization = mAuthorization;
        final int operation = ++mOperation;
        mBusy = true;
        getPreferenceScreen().setEnabled(false);
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean cleared;
            try {
                mManager.clearPassword(UserHandle.myUserId(), authorization);
                cleared = true;
            } catch (RuntimeException error) {
                cleared = false;
            }
            final boolean success = cleared;
            ThreadUtils.postOnMainThread(() -> {
                if (!mVisible || operation != mOperation) return;
                mBusy = false;
                endAuthorization();
                toast(success ? R.string.arkui_privacy_password_disabled
                        : R.string.arkui_privacy_password_unavailable);
                refresh();
            });
        });
    }

    private void endAuthorization() {
        if (mAuthorization == null || mManager == null) return;
        try { mManager.endAuthentication(mAuthorization); }
        catch (RuntimeException ignored) { }
        mAuthorization = null;
    }

    private void toast(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
