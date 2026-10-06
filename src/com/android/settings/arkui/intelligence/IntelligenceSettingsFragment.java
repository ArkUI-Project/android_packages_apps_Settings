/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.intelligence;

import static com.android.settingslib.arkui.IntelligenceSettings.*;

import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.widget.SliderPreference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Shared native MD3E preferences without a second settings store or background app. */
abstract class IntelligenceSettingsFragment extends SettingsPreferenceFragment {
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ContentObserver mObserver = new ContentObserver(mMain) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private boolean mUpdating;
    private boolean mResumed;
    protected abstract int resource();
    protected abstract Option master();

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(resource());
        for (Option option : OPTIONS) {
            final Preference row = findPreference(option.key());
            if (row == null) continue;
            row.setPersistent(false);
            if (row instanceof SettingsMainSwitchPreference main) {
                main.addOnSwitchChangeListener((button, checked) -> {
                    if (!mUpdating) save(option, checked ? 1 : 0);
                });
            } else row.setOnPreferenceChangeListener((preference, value) -> {
                if (!mUpdating) save(option, value instanceof Boolean ? ((Boolean) value ? 1 : 0) : (Integer) value);
                return true;
            });
            if (row instanceof SliderPreference slider) {
                slider.setMin(option.min());
                slider.setMax(option.max());
                slider.setSliderIncrement(option == LYRICS_OFFSET ? 100 : 1);
                slider.setShowSliderValue(true);
                slider.setUpdatesContinuously(false);
                slider.setSliderContentDescription(row.getTitle());
                slider.setLabelFormater(value -> format(option, Math.round(value)));
            }
        }
        final ListPreference music = findPreference(MUSIC_APP);
        if (music != null) {
            populateMusic(music);
            music.setPersistent(false);
            music.setOnPreferenceChangeListener((row, value) -> Settings.Secure.putStringForUser(
                    requireContext().getContentResolver(), MUSIC_APP, (String) value, requireContext().getUserId()));
        }
    }
    private void save(Option option, int value) {
        option.write(requireContext().getContentResolver(), requireContext().getUserId(), value);
        refresh();
    }
    @Override public void onResume() {
        super.onResume();
        mResumed = true;
        for (Option option : OPTIONS) if (findPreference(option.key()) != null) {
            requireContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(option.key()), false, mObserver);
        }
        requireContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(MUSIC_APP), false, mObserver);
        refresh();
    }
    @Override public void onPause() {
        mResumed = false;
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }
    private void refresh() {
        if (!mResumed || !isAdded()) return;
        final var resolver = requireContext().getContentResolver();
        final int user = requireContext().getUserId();
        final boolean enabled = master().enabled(resolver, user);
        mUpdating = true;
        for (Option option : OPTIONS) {
            final Preference row = findPreference(option.key());
            if (row == null) continue;
            final int value = option.read(resolver, user);
            if (row instanceof TwoStatePreference toggle) toggle.setChecked(value != 0);
            else if (row instanceof SliderPreference slider) { slider.setValue(value); slider.setSummary(format(option, value)); }
            row.setEnabled(option == master() || enabled);
        }
        final ListPreference music = findPreference(MUSIC_APP);
        if (music != null) {
            String selected = Settings.Secure.getStringForUser(resolver, MUSIC_APP, user);
            if (selected == null) selected = "org.lineageos.twelve";
            music.setValue(selected);
            music.setEnabled(enabled && HEADPHONES.enabled(resolver, user));
            music.setSummary(music.getEntry() != null ? music.getEntry() : getString(R.string.arkui_intelligence_music_none));
        }
        mUpdating = false;
    }
    private void populateMusic(ListPreference preference) {
        final var pm = requireContext().getPackageManager();
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        apps.sort(new ResolveInfo.DisplayNameComparator(pm));
        final LinkedHashMap<String, CharSequence> choices = new LinkedHashMap<>();
        for (ResolveInfo app : apps) choices.putIfAbsent(app.activityInfo.packageName, app.loadLabel(pm));
        preference.setEntries(choices.values().toArray(new CharSequence[0]));
        preference.setEntryValues(choices.keySet().toArray(new CharSequence[0]));
    }
    private String format(Option option, int value) {
        return getString(option == LYRICS_OFFSET ? R.string.arkui_intelligence_ms
                : option == LYRICS_WIDTH ? R.string.arkui_intelligence_dp
                : option == LYRICS_TEXT_SIZE ? R.string.arkui_intelligence_sp
                : R.string.arkui_intelligence_seconds, value);
    }
}
