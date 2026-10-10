/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SettingsMainSwitchPreference;

/** Per-user secure setting with a main switch and an explanation on unsupported hardware. */
abstract class ArkuiFeatureToggleFragment extends SettingsPreferenceFragment {
    protected abstract int resource();
    protected abstract String setting();
    protected abstract boolean supported();
    private boolean mUpdating;
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { if (isAdded()) refresh(); }
    };

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(resource());
        SettingsMainSwitchPreference toggle = findPreference(setting());
        toggle.addOnSwitchChangeListener((button, enabled) -> {
            if (!mUpdating && !Settings.Secure.putIntForUser(requireContext().getContentResolver(),
                    setting(), enabled ? 1 : 0, requireContext().getUserId())) refresh();
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (requireContext().getSystemService(UserManager.class).isManagedProfile()) {
            finish();
            return;
        }
        requireContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(setting()), false, mObserver);
        refresh();
    }

    @Override public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    private void refresh() {
        boolean available = supported();
        SettingsMainSwitchPreference toggle = findPreference(setting());
        mUpdating = true;
        toggle.setChecked(available && Settings.Secure.getIntForUser(
                requireContext().getContentResolver(), setting(), 0, requireContext().getUserId()) != 0);
        toggle.setEnabled(available);
        findPreference("arkui_feature_hardware_note").setVisible(!available);
        mUpdating = false;
    }
}
