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

import com.android.internal.arkui.SystemBarFollowSettings;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.SliderPreference;

@SearchIndexable
public class ArkuiSystemBarMotionSettings extends DashboardFragment {
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { if (isAdded()) refresh(); }
    };
    private static final String[] KEYS = {Settings.System.ARKUI_STATUS_BAR_FOLLOW,
            Settings.System.ARKUI_NAVIGATION_BAR_FOLLOW, Settings.System.ARKUI_NAVIGATION_BAR_FADE};

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override protected String getLogTag() { return "ArkuiSystemBarMotion"; }
    @Override protected int getPreferenceScreenResId() { return R.xml.arkui_system_bar_motion; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        for (int i = 0; i < 2; i++) {
            String key = KEYS[i];
            findPreference(key).setOnPreferenceChangeListener((preference, value) ->
                    Settings.System.putInt(requireContext().getContentResolver(), key,
                            (Boolean) value ? 1 : 0));
        }
        SliderPreference fade = findPreference(KEYS[2]);
        fade.setMin(200); fade.setMax(1200); fade.setSliderIncrement(20);
        fade.setShowSliderValue(true); fade.setUpdatesContinuously(false);
        fade.setSliderContentDescription(getString(R.string.arkui_navigation_bar_fade_title));
        fade.setLabelFormater(value -> getString(R.string.arkui_navigation_bar_fade_value, Math.round(value)));
        fade.setOnPreferenceChangeListener((preference, value) ->
                Settings.System.putInt(requireContext().getContentResolver(), KEYS[2], (Integer) value));
    }

    @Override public void onResume() {
        super.onResume();
        if (!available(requireContext())) { finish(); return; }
        for (String key : KEYS) requireContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(key), false, mObserver);
        refresh();
    }

    @Override public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    private void refresh() {
        var resolver = requireContext().getContentResolver();
        boolean gestures = SystemBarFollowSettings.isGestureNavigation(requireContext());
        for (int i = 0; i < 2; i++) {
            TwoStatePreference toggle = findPreference(KEYS[i]);
            toggle.setChecked(Settings.System.getInt(resolver, KEYS[i], 1) != 0);
            toggle.setEnabled(i == 0 || gestures);
        }
        findPreference(KEYS[1]).setSummary(gestures ? R.string.arkui_navigation_bar_follow_summary
                : R.string.arkui_navigation_bar_follow_requires_gestures);
        SliderPreference fade = findPreference(KEYS[2]);
        int duration = SystemBarFollowSettings.fadeDuration(requireContext(),
                android.os.UserHandle.myUserId());
        fade.setValue(duration); fade.setSummary(getString(R.string.arkui_navigation_bar_fade_value, duration));
        fade.setEnabled(gestures && Settings.System.getInt(resolver, KEYS[1], 1) != 0);
    }

    private static boolean available(Context context) {
        return !context.getSystemService(UserManager.class).isManagedProfile();
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_system_bar_motion) {
                @Override protected boolean isPageSearchEnabled(Context context) { return available(context); }
            };
}
