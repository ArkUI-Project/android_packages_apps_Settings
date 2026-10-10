/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class AdSkipSettingsFragment extends SettingsPreferenceFragment {
    private boolean mUpdating;
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { if (isAdded()) refresh(); }
    };

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_ad_skip_settings);
        SettingsMainSwitchPreference toggle = findPreference("ad_skip_enabled");
        toggle.addOnSwitchChangeListener((button, checked) -> {
            if (!mUpdating) AdSkipSettings.setEnabled(requireContext(), checked);
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (!available(requireContext())) { finish(); return; }
        for (String key : new String[] {Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                AdSkipSettings.EXCLUDED, AdSkipSettings.COUNT}) {
            requireContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),
                    false, mObserver);
        }
        refresh();
    }

    @Override public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    private void refresh() {
        mUpdating = true;
        SettingsMainSwitchPreference toggle = findPreference("ad_skip_enabled");
        toggle.setChecked(AdSkipSettings.enabled(requireContext()));
        mUpdating = false;
        findPreference("ad_skip_count").setSummary(getString(R.string.arkui_ad_skip_count_value,
                Settings.Secure.getInt(requireContext().getContentResolver(), AdSkipSettings.COUNT, 0)));
        findPreference("ad_skip_excluded").setSummary(getString(R.string.arkui_ad_skip_excluded_count,
                AdSkipSettings.excluded(requireContext()).size()));
    }

    private static boolean available(Context context) {
        return !context.getSystemService(UserManager.class).isManagedProfile();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_ad_skip_settings) {
                @Override protected boolean isPageSearchEnabled(Context context) {
                    return available(context);
                }
            };
}
