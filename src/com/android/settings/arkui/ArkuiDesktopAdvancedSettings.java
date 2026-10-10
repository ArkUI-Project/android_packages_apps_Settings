/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native controls for the schema supplied by Launcher, including its defaults and bounds. */
@SearchIndexable
public class ArkuiDesktopAdvancedSettings extends DashboardFragment {
    private static final String TAG = "ArkuiDesktopAdvanced";
    private static final Uri URI = Uri.parse("content://com.android.launcher3.desktop_settings");
    private final Map<String, CharSequence> mDescriptions = new LinkedHashMap<>();
    private int mGeneration;
    private boolean mLoaded;
    private boolean mIosStyle;

    @Override
    public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override
    protected String getLogTag() { return TAG; }

    @Override
    protected int getPreferenceScreenResId() { return R.xml.arkui_desktop_advanced_settings; }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        final ListPreference style = findPreference("pref_motion_style");
        style.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        style.setOnPreferenceChangeListener((preference, value) -> {
            if ("0".equals(value) || "1".equals(value)) {
                request("set_preference", preference.getKey(), Integer.parseInt((String) value));
            }
            return false;
        });
        bindSliders(getPreferenceScreen());
        findPreference("motion_status").setOnPreferenceClickListener(preference -> {
            request("get_preferences", null, 0);
            return true;
        });
        findPreference("motion_reset").setOnPreferenceClickListener(preference -> {
            request("reset_animation_preferences", null, 0);
            return true;
        });
        setControlsEnabled(false);
        setControlsVisible(false);
        findPreference("motion_style_hint").setVisible(false);
    }

    private void bindSliders(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceGroup child) {
                bindSliders(child);
            } else if (preference instanceof ArkuiMotionPreference slider) {
                mDescriptions.put(slider.getKey(), slider.getSummary());
                slider.setOnPreferenceChangeListener((item, value) -> {
                    if (value instanceof Integer number) {
                        request("set_preference", item.getKey(), number);
                    }
                    // The response confirms persistence before updating the displayed value.
                    return false;
                });
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!ArkuiDesktopSettings.isAvailable(requireContext())) {
            finish();
            return;
        }
        request("get_preferences", null, 0);
    }

    @Override
    public void onPause() {
        ++mGeneration;
        super.onPause();
    }

    private void setControlsEnabled(boolean enabled) {
        for (String key : mDescriptions.keySet()) {
            findPreference(key).setEnabled(enabled && !mIosStyle);
        }
        findPreference("pref_motion_style").setEnabled(enabled);
        findPreference("motion_reset").setEnabled(enabled && !mIosStyle);
    }

    private void setControlsVisible(boolean visible) {
        PreferenceGroup screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof PreferenceGroup) preference.setVisible(visible);
        }
    }

    private void request(String method, String key, int value) {
        final int generation = ++mGeneration;
        final ContentResolver resolver = requireContext().getContentResolver();
        setControlsEnabled(false);
        Preference status = findPreference("motion_status");
        status.setTitle(R.string.arkui_desktop_loading);
        status.setEnabled(false);
        status.setVisible(!mLoaded);
        ThreadUtils.postOnBackgroundThread(() -> {
            Bundle response = null;
            try {
                Bundle extras = new Bundle();
                extras.putInt("value", value);
                response = resolver.call(URI, method, key, extras);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to access animation preferences", e);
            }
            final Bundle motion = response == null ? null : response.getBundle("motion");
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mGeneration || getPreferenceScreen() == null) {
                    return;
                }
                if (motion == null || motion.isEmpty()) {
                    status.setTitle(R.string.arkui_desktop_unavailable);
                    status.setEnabled(true);
                    status.setVisible(true);
                    if (!"get_preferences".equals(method)) {
                        Toast.makeText(getContext(), R.string.arkui_desktop_save_error,
                                Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                final Bundle styleSpec = motion.getBundle("pref_motion_style");
                final ListPreference style = findPreference("pref_motion_style");
                style.setVisible(styleSpec != null);
                mIosStyle = styleSpec != null && styleSpec.getInt("value") == 1;
                if (styleSpec != null) style.setValue(Integer.toString(styleSpec.getInt("value")));
                findPreference("motion_style_hint").setVisible(mIosStyle);
                for (Map.Entry<String, CharSequence> entry : mDescriptions.entrySet()) {
                    ArkuiMotionPreference slider = findPreference(entry.getKey());
                    Bundle spec = motion.getBundle(entry.getKey());
                    slider.setVisible(spec != null);
                    if (spec == null) continue;
                    int min = spec.getInt("min");
                    int max = spec.getInt("max");
                    int current = spec.getInt("value");
                    int step = Math.max(1, spec.getInt("step"));
                    String unit = spec.getString("unit", "");
                    slider.setMax(max);
                    slider.setMin(min);
                    // Older sliders allowed arbitrary integers. Keep saved values valid for
                    // Material Slider's discrete grid without rounding the user's settings.
                    slider.setSliderIncrement((current - min) % step == 0
                            && (max - min) % step == 0 ? step : 1);
                    slider.setUnit(unit);
                    slider.setValue(current);
                    slider.setSliderStateDescription(current + unit);
                    slider.setSummary(getString(R.string.arkui_motion_value,
                            current, unit, spec.getInt("default"), entry.getValue()));
                }
                mLoaded = true;
                setControlsVisible(!mIosStyle);
                findPreference("motion_reset").setVisible(!mIosStyle);
                status.setVisible(false);
                setControlsEnabled(true);
                if ("reset_animation_preferences".equals(method)) {
                    Toast.makeText(getContext(), R.string.arkui_motion_saved,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_desktop_advanced_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return ArkuiDesktopSettings.isAvailable(context);
                }

                @Override
                public java.util.List<String> getNonIndexableKeys(Context context) {
                    java.util.List<String> keys = super.getNonIndexableKeys(context);
                    keys.add("motion_status");
                    keys.add("motion_reset");
                    return keys;
                }
            };
}
