/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.List;

import lineageos.providers.LineageSettings;

/** Native expressive preferences backed directly by the settings consumed by SystemUI. */
abstract class PersonalizationPreferenceFragment extends SettingsPreferenceFragment {
    enum Table { SECURE, GLOBAL, LINEAGE_SYSTEM, LINEAGE_SECURE }
    private record Binding(String key, Table table, int fallback) {}
    private final List<Binding> mBindings = new ArrayList<>();
    private boolean mResumed;
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };

    protected abstract int resource();
    protected abstract void configure();
    protected void afterRefresh() {}
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(resource());
        configure();
    }

    protected final void bind(String key, Table table, int fallback) {
        final Binding binding = new Binding(key, table, fallback);
        mBindings.add(binding);
        final Preference row = findPreference(key);
        row.setPersistent(false);
        if (row instanceof ListPreference list) {
            list.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
        row.setOnPreferenceChangeListener((preference, value) -> {
            final int number = value instanceof Boolean ? ((Boolean) value ? 1 : 0)
                    : Integer.parseInt(value.toString());
            return write(binding, number);
        });
    }

    protected final int read(String key, Table table, int fallback) {
        final ContentResolver resolver = requireContext().getContentResolver();
        final int user = requireContext().getUserId();
        return switch (table) {
            case SECURE -> Settings.Secure.getIntForUser(resolver, key, fallback, user);
            case GLOBAL -> Settings.Global.getInt(resolver, key, fallback);
            case LINEAGE_SYSTEM -> LineageSettings.System.getIntForUser(resolver, key, fallback, user);
            case LINEAGE_SECURE -> LineageSettings.Secure.getIntForUser(resolver, key, fallback, user);
        };
    }

    private boolean write(Binding binding, int value) {
        final ContentResolver resolver = requireContext().getContentResolver();
        final int user = requireContext().getUserId();
        return switch (binding.table()) {
            case SECURE -> Settings.Secure.putIntForUser(resolver, binding.key(), value, user);
            case GLOBAL -> requireContext().getSystemService(UserManager.class).isAdminUser()
                    && user == 0 && Settings.Global.putInt(resolver, binding.key(), value);
            case LINEAGE_SYSTEM -> LineageSettings.System.putIntForUser(
                    resolver, binding.key(), value, user);
            case LINEAGE_SECURE -> LineageSettings.Secure.putIntForUser(
                    resolver, binding.key(), value, user);
        };
    }

    @Override public void onResume() {
        super.onResume();
        if (requireContext().getSystemService(UserManager.class).isManagedProfile()) {
            finish();
            return;
        }
        mResumed = true;
        for (Binding binding : mBindings) {
            final Uri uri = switch (binding.table()) {
                case SECURE -> Settings.Secure.getUriFor(binding.key());
                case GLOBAL -> Settings.Global.getUriFor(binding.key());
                case LINEAGE_SYSTEM -> LineageSettings.System.getUriFor(binding.key());
                case LINEAGE_SECURE -> LineageSettings.Secure.getUriFor(binding.key());
            };
            requireContext().getContentResolver().registerContentObserver(uri, false, mObserver);
        }
        refresh();
    }

    @Override public void onPause() {
        mResumed = false;
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    protected final void refresh() {
        if (!mResumed || !isAdded()) return;
        for (Binding binding : mBindings) {
            final Preference row = findPreference(binding.key());
            final int value = read(binding.key(), binding.table(), binding.fallback());
            if (row instanceof TwoStatePreference toggle) toggle.setChecked(value != 0);
            else if (row instanceof ListPreference list) list.setValue(Integer.toString(value));
        }
        afterRefresh();
    }
}
