/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;

import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.arkui.SmartPixelsPattern;
import com.android.settingslib.arkui.SmartPixelsSettings;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.SliderPreference;

@SearchIndexable
public final class SmartPixelsSettingsFragment extends SettingsPreferenceFragment {
    private boolean mUpdating;
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { if (isAdded()) refresh(); }
    };

    @Override public int getMetricsCategory() { return SettingsEnums.DISPLAY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_smart_pixels);
        SettingsMainSwitchPreference toggle = findPreference(SmartPixelsSettings.ENABLED);
        toggle.addOnSwitchChangeListener((button, enabled) -> {
            if (!mUpdating) save(SmartPixelsSettings.ENABLED, enabled ? 1 : 0);
        });
        findPreference(SmartPixelsSettings.POWER_SAVE_ONLY).setOnPreferenceChangeListener((p, value) ->
                save(SmartPixelsSettings.POWER_SAVE_ONLY, (Boolean) value ? 1 : 0));
        SliderPreference percent = findPreference(SmartPixelsSettings.PERCENT);
        percent.setMin(SmartPixelsPattern.MIN_PERCENT);
        percent.setMax(SmartPixelsPattern.MAX_PERCENT);
        percent.setSliderIncrement(1);
        percent.setShowSliderValue(true);
        percent.setUpdatesContinuously(false);
        percent.setSliderContentDescription(getString(R.string.arkui_smart_pixels_percent));
        percent.setLabelFormater(value -> getString(R.string.arkui_smart_pixels_percent_value,
                Math.round(value)));
        percent.setOnPreferenceChangeListener((p, value) ->
                save(SmartPixelsSettings.PERCENT, SmartPixelsPattern.clamp((Integer) value)));
    }

    private boolean save(String key, int value) {
        return Settings.Secure.putIntForUser(requireContext().getContentResolver(), key, value,
                requireContext().getUserId());
    }

    @Override public void onResume() {
        super.onResume();
        if (!available(requireContext())) { finish(); return; }
        for (String key : SmartPixelsSettings.KEYS) {
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
        var resolver = requireContext().getContentResolver();
        int user = requireContext().getUserId();
        boolean enabled = SmartPixelsSettings.enabled(resolver, user);
        mUpdating = true;
        SettingsMainSwitchPreference toggle = findPreference(SmartPixelsSettings.ENABLED);
        toggle.setChecked(enabled);
        TwoStatePreference saverOnly = findPreference(SmartPixelsSettings.POWER_SAVE_ONLY);
        saverOnly.setChecked(SmartPixelsSettings.powerSaveOnly(resolver, user));
        saverOnly.setEnabled(enabled);
        SliderPreference percent = findPreference(SmartPixelsSettings.PERCENT);
        int value = SmartPixelsSettings.percent(resolver, user);
        percent.setValue(value);
        percent.setSummary(getString(R.string.arkui_smart_pixels_percent_value, value));
        percent.setEnabled(enabled);
        mUpdating = false;
    }

    private static boolean available(Context context) {
        return !context.getSystemService(UserManager.class).isManagedProfile();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_smart_pixels) {
                @Override protected boolean isPageSearchEnabled(Context context) {
                    return available(context);
                }
            };
}
