/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.arkui.MotionSensorSettings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.applications.appinfo.AppInfoDashboardFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Expressive per-app motion access, also reachable from the app information page. */
@SearchIndexable
public final class ArkuiMotionSensorSettings extends SettingsPreferenceFragment {
    private static final int[] TITLES = {R.string.arkui_motion_sensor_allow,
            R.string.arkui_motion_sensor_open, R.string.arkui_motion_sensor_deny};
    private static final int[] DESCRIPTIONS = {R.string.arkui_motion_sensor_allow_description,
            R.string.arkui_motion_sensor_open_description,
            R.string.arkui_motion_sensor_deny_description};
    private record App(ApplicationInfo info, CharSequence label) { }
    private final List<SelectorWithWidgetPreference> mChoices = new ArrayList<>();
    private ApplicationInfo mApp;
    private int mGeneration;
    private boolean mSavingAll;

    @Override public int getMetricsCategory() { return SettingsEnums.TOP_LEVEL_PRIVACY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_motion_sensor_settings);
        final Bundle args = getArguments();
        final String name = args == null ? null
                : args.getString(AppInfoDashboardFragment.ARG_PACKAGE_NAME);
        if (name != null) {
            final int uid = args.getInt(AppInfoDashboardFragment.ARG_PACKAGE_UID,
                    Process.myUid());
            try {
                mApp = requireContext().getPackageManager().getApplicationInfoAsUser(name, 0,
                        UserHandle.getUserId(uid));
            } catch (PackageManager.NameNotFoundException ignored) { finish(); return; }
            if (UserHandle.getAppId(mApp.uid) < Process.FIRST_APPLICATION_UID) {
                finish();
                return;
            }
            showChoices();
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (mApp == null) loadApps();
        else refreshChoices();
    }

    @Override public void onPause() { ++mGeneration; super.onPause(); }

    private void loadApps() {
        final int generation = ++mGeneration;
        final Context context = requireContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            final PackageManager pm = context.getPackageManager();
            final Map<String, Integer> modes = MotionSensorSettings.read(
                    context.getContentResolver(), context.getUserId());
            final List<App> apps = new ArrayList<>();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if (UserHandle.getAppId(info.uid) < Process.FIRST_APPLICATION_UID
                        || !info.enabled) continue;
                if (info.isSystemApp() && pm.getLaunchIntentForPackage(info.packageName) == null
                        && !modes.containsKey(info.packageName)) continue;
                apps.add(new App(info, info.loadLabel(pm)));
            }
            final Collator collator = Collator.getInstance();
            apps.sort((a, b) -> collator.compare(a.label().toString(), b.label().toString()));
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mGeneration) return;
                getPreferenceScreen().removeAll();
                addExplanation();
                final ButtonPreference blockAll = new ButtonPreference(context);
                blockAll.setKey("motion_block_all");
                blockAll.setTitle(R.string.arkui_motion_sensor_block_all);
                blockAll.setIcon(R.drawable.ic_arkui_motion_sensor);
                blockAll.setButtonStyle(ButtonPreference.TYPE_FILLED, ButtonPreference.SIZE_NORMAL);
                blockAll.setEnabled(!mSavingAll);
                blockAll.setOnClickListener(view -> blockAllApps(blockAll));
                getPreferenceScreen().addPreference(blockAll);
                final PreferenceCategory group = new PreferenceCategory(context);
                group.setTitle(R.string.arkui_motion_sensor_apps);
                getPreferenceScreen().addPreference(group);
                for (App app : apps) {
                    final Preference row = new Preference(context);
                    row.setKey(app.info().packageName);
                    row.setTitle(app.label());
                    row.setIcon(app.info().loadIcon(pm));
                    row.setSummary(TITLES[modes.getOrDefault(app.info().packageName,
                            MotionSensorSettings.ALLOW)]);
                    row.setOnPreferenceClickListener(preference -> {
                        final Bundle args = new Bundle();
                        args.putString(AppInfoDashboardFragment.ARG_PACKAGE_NAME,
                                app.info().packageName);
                        args.putInt(AppInfoDashboardFragment.ARG_PACKAGE_UID, app.info().uid);
                        new SubSettingLauncher(context).setDestination(getClass().getName())
                                .setArguments(args).setTitleRes(R.string.arkui_motion_sensor_title)
                                .setSourceMetricsCategory(getMetricsCategory()).launch();
                        return true;
                    });
                    group.addPreference(row);
                }
                if (apps.isEmpty()) {
                    final Preference empty = new Preference(context);
                    empty.setTitle(R.string.arkui_motion_sensor_empty);
                    empty.setSelectable(false);
                    group.addPreference(empty);
                }
            });
        });
    }

    private void blockAllApps(ButtonPreference button) {
        if (mSavingAll) return;
        final Context context = requireContext();
        mSavingAll = true;
        button.setEnabled(false);
        ThreadUtils.postOnBackgroundThread(() -> {
            final List<String> packages = new ArrayList<>();
            // Include disabled and non-launchable apps so shared UIDs get a consistent mode.
            for (ApplicationInfo info : context.getPackageManager().getInstalledApplications(0)) {
                if (UserHandle.getAppId(info.uid) >= Process.FIRST_APPLICATION_UID) {
                    packages.add(info.packageName);
                }
            }
            final boolean saved = MotionSensorSettings.setMode(context.getContentResolver(),
                    packages, context.getUserId(), MotionSensorSettings.BLOCK_ON_OPEN);
            ThreadUtils.postOnMainThread(() -> {
                mSavingAll = false;
                if (!isAdded()) return;
                Toast.makeText(context, saved ? R.string.arkui_motion_sensor_block_all_done
                        : R.string.arkui_motion_sensor_save_error, Toast.LENGTH_SHORT).show();
                if (isResumed()) loadApps();
            });
        });
    }

    private void addExplanation() {
        final Preference explanation = new Preference(requireContext());
        explanation.setTitle(R.string.arkui_motion_sensor_description);
        explanation.setIcon(R.drawable.ic_arkui_motion_sensor);
        explanation.setSelectable(false);
        getPreferenceScreen().addPreference(explanation);
    }

    private void showChoices() {
        final Context context = requireContext();
        getPreferenceScreen().removeAll();
        final Preference header = new Preference(context);
        header.setTitle(mApp.loadLabel(context.getPackageManager()));
        header.setIcon(mApp.loadIcon(context.getPackageManager()));
        header.setSelectable(false);
        getPreferenceScreen().addPreference(header);
        final PreferenceCategory group = new PreferenceCategory(context);
        group.setTitle(R.string.arkui_motion_sensor_access);
        getPreferenceScreen().addPreference(group);
        for (int mode = 0; mode < TITLES.length; mode++) {
            final int choice = mode;
            final SelectorWithWidgetPreference row = new SelectorWithWidgetPreference(context);
            row.setKey("motion_mode_" + mode);
            row.setTitle(TITLES[mode]);
            row.setSummary(DESCRIPTIONS[mode]);
            row.setPersistent(false);
            row.setOnClickListener(preference -> saveMode(choice));
            mChoices.add(row);
            group.addPreference(row);
        }
        final FooterPreference footer = new FooterPreference(context);
        footer.setKey("motion_sensor_note");
        footer.setTitle(R.string.arkui_motion_sensor_note);
        getPreferenceScreen().addPreference(footer);
        final String[] shared = context.getPackageManager().getPackagesForUid(mApp.uid);
        if (shared != null && shared.length > 1) {
            final FooterPreference sharedFooter = new FooterPreference(context);
            sharedFooter.setKey("motion_sensor_shared_note");
            sharedFooter.setTitle(R.string.arkui_motion_sensor_shared_uid);
            getPreferenceScreen().addPreference(sharedFooter);
        }
        refreshChoices();
    }

    private void saveMode(int mode) {
        final Context context = requireContext();
        final String[] shared = context.getPackageManager().getPackagesForUid(mApp.uid);
        if (shared == null) return;
        if (!MotionSensorSettings.setMode(context.getContentResolver(), List.of(shared),
                UserHandle.getUserId(mApp.uid), mode)) {
            Toast.makeText(context, R.string.arkui_motion_sensor_save_error,
                    Toast.LENGTH_SHORT).show();
        }
        refreshChoices();
    }

    private void refreshChoices() {
        final int mode = MotionSensorSettings.getMode(requireContext().getContentResolver(),
                mApp.packageName, UserHandle.getUserId(mApp.uid));
        for (int i = 0; i < mChoices.size(); i++) mChoices.get(i).setChecked(i == mode);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_motion_sensor_settings);
}
