/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.app.settings.SettingsEnums;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;

@SearchIndexable
public final class ArkuiStatusBarIconsSettings extends SettingsPreferenceFragment {
    private static final String KEY = "icon_blacklist";
    private static final String[] SLOTS = {"clock", "wifi", "mobile", "bluetooth", "alarm_clock",
            "volume", "zen", "airplane", "location", "rotate", "headset"};
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_status_bar_icons);
        for (String slot : SLOTS) {
            final TwoStatePreference row = findPreference("icon_" + slot);
            row.setPersistent(false);
            row.setOnPreferenceChangeListener((preference, value) -> {
                final ArraySet<String> hidden = readHidden();
                if ((Boolean) value) hidden.remove(slot);
                else hidden.add(slot);
                return Settings.Secure.putStringForUser(requireContext().getContentResolver(), KEY,
                        TextUtils.join(",", hidden), requireContext().getUserId());
            });
        }
    }
    private ArraySet<String> readHidden() {
        final String raw = Settings.Secure.getString(requireContext().getContentResolver(), KEY);
        final ArraySet<String> hidden = new ArraySet<>();
        if (raw != null) hidden.addAll(Arrays.asList(raw.split(",")));
        else try {
            final var resources = requireContext().getPackageManager().getResourcesForApplication("com.android.systemui");
            final int id = resources.getIdentifier("config_statusBarIconsToExclude", "array", "com.android.systemui");
            if (id != 0) hidden.addAll(Arrays.asList(resources.getStringArray(id)));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        hidden.remove("");
        return hidden;
    }
    @Override public void onResume() {
        super.onResume();
        requireContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, mObserver);
        refresh();
    }
    @Override public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }
    private void refresh() {
        if (!isAdded()) return;
        final ArraySet<String> hidden = readHidden();
        for (String slot : SLOTS) ((TwoStatePreference) findPreference("icon_" + slot)).setChecked(!hidden.contains(slot));
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_status_bar_icons);
}
