/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.AppSwitchPreference;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AdSkipExcludedAppsFragment extends SettingsPreferenceFragment {
    private record App(String name, CharSequence label, Drawable icon) { }
    private int mGeneration;

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_ad_skip_excluded_apps);
    }

    @Override public void onResume() {
        super.onResume();
        Context context = requireContext();
        if (context.getSystemService(UserManager.class).isManagedProfile()) { finish(); return; }
        int generation = ++mGeneration;
        ThreadUtils.postOnBackgroundThread(() -> {
            PackageManager pm = context.getPackageManager();
            Set<String> names = new HashSet<>();
            Set<String> excluded = AdSkipSettings.excluded(context);
            List<App> apps = new ArrayList<>();
            for (var activity : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                ApplicationInfo info = activity.activityInfo.applicationInfo;
                if (!info.enabled || UserHandle.getAppId(info.uid) < Process.FIRST_APPLICATION_UID
                        || info.packageName.equals(context.getPackageName())
                        || !names.add(info.packageName)) continue;
                apps.add(new App(info.packageName, info.loadLabel(pm), info.loadIcon(pm)));
            }
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> collator.compare(a.label().toString(), b.label().toString()));
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || mGeneration != generation) return;
                getPreferenceScreen().removeAll();
                Preference intro = new Preference(context);
                intro.setTitle(R.string.arkui_ad_skip_excluded_intro);
                intro.setSelectable(false);
                getPreferenceScreen().addPreference(intro);
                for (App app : apps) {
                    AppSwitchPreference row = new AppSwitchPreference(context);
                    row.setKey(app.name());
                    row.setTitle(app.label());
                    row.setIcon(app.icon());
                    row.setPersistent(false);
                    row.setChecked(excluded.contains(app.name()));
                    row.setOnPreferenceChangeListener((preference, value) -> {
                        AdSkipSettings.setExcluded(context, app.name(), (Boolean) value);
                        return true;
                    });
                    getPreferenceScreen().addPreference(row);
                }
            });
        });
    }

    @Override public void onPause() { ++mGeneration; super.onPause(); }
}
