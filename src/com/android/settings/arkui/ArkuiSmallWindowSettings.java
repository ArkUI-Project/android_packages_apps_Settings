/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.arkui.SmallWindowSettings;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Per-user controls for the native ArkUI small-window experience. */
@SearchIndexable
public class ArkuiSmallWindowSettings extends DashboardFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String ENABLED = Settings.System.ARKUI_SMALL_WINDOW_ENABLED;
    private static final String[] SWITCH_KEYS = {
            Settings.System.ARKUI_SMALL_WINDOW_RECENTS,
            Settings.System.ARKUI_SMALL_WINDOW_NOTIFICATION_DRAG,
            Settings.System.POP_UP_NOTIFICATION_JUMP_PORTRAIT,
            Settings.System.POP_UP_NOTIFICATION_JUMP_LANDSCAPE,
            Settings.System.ARKUI_SMALL_WINDOW_EDGE_DOCK,
            Settings.System.ARKUI_SMALL_WINDOW_EDGE_DOCK_FOREGROUND,
            Settings.System.ARKUI_SMALL_WINDOW_HAPTICS,
    };
    private static final int[] SWITCH_DEFAULTS = {1, 0, 0, 0, 1, 1, 1};
    private static final String[] CHOICE_KEYS = {
            Settings.System.ARKUI_SMALL_WINDOW_SIZE,
            Settings.System.ARKUI_SMALL_WINDOW_PINNED_SIZE,
            Settings.System.POP_UP_SINGLE_TAP_ACTION,
            Settings.System.POP_UP_DOUBLE_TAP_ACTION,
    };
    private static final int[] CHOICE_DEFAULTS = {1, 0, 0, 1};
    private static final String[] APP_KEYS = {
            Settings.System.ARKUI_SMALL_WINDOW_BLACKLIST,
            Settings.System.POP_UP_NOTIFICATION_BLACKLIST,
    };
    private static final String[] GROUP_KEYS = {
            "small_window_launch", "small_window_appearance", "small_window_interaction",
    };

    private final Map<String, String> mAppLabels = new HashMap<>();
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            if (isAdded() && getPreferenceScreen() != null) {
                refresh();
            }
        }
    };

    private ContentResolver mResolver;
    private SettingsMainSwitchPreference mMainSwitch;
    private AlertDialog mResetDialog;
    private boolean mUpdating;
    private boolean mAppsLoaded;
    private int mLoadGeneration;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return "ArkuiSmallWindow";
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arkui_small_window_settings;
    }

    static boolean isAvailable(Context context) {
        return SmallWindowSettings.isSupported(context)
                && !context.getSystemService(UserManager.class).isManagedProfile();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mResolver = requireContext().getContentResolver();
        mMainSwitch = findPreference(ENABLED);
        mMainSwitch.addOnSwitchChangeListener((button, checked) -> {
            if (!mUpdating) {
                saveInt(ENABLED, checked ? 1 : 0);
                refresh();
            }
        });
        for (String key : SWITCH_KEYS) {
            findPreference(key).setOnPreferenceChangeListener(this);
        }
        for (String key : CHOICE_KEYS) {
            final ListPreference preference = findPreference(key);
            preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            preference.setOnPreferenceChangeListener(this);
        }
        for (String key : APP_KEYS) {
            findPreference(key).setOnPreferenceChangeListener(this);
        }
        findPreference("small_window_reset").setOnPreferenceClickListener(preference -> {
            showResetDialog();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAvailable(requireContext())) {
            finish();
            return;
        }
        for (String key : getSettingKeys()) {
            mResolver.registerContentObserver(Settings.System.getUriFor(key), false, mObserver);
        }
        refresh();
        loadApps();
    }

    @Override
    public void onPause() {
        mResolver.unregisterContentObserver(mObserver);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        ++mLoadGeneration;
        if (mResetDialog != null) {
            mResetDialog.dismiss();
            mResetDialog = null;
        }
        super.onDestroyView();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mUpdating) {
            return false;
        }
        if (preference instanceof MultiSelectListPreference) {
            @SuppressWarnings("unchecked")
            final Set<String> selected = (Set<String>) newValue;
            final boolean saved = Settings.System.putString(mResolver, preference.getKey(),
                    TextUtils.join(";", new TreeSet<>(selected)));
            if (!saved) {
                showSaveError();
            }
        } else if (preference instanceof SwitchPreferenceCompat) {
            saveInt(preference.getKey(), (Boolean) newValue ? 1 : 0);
        } else if (preference instanceof ListPreference) {
            final ListPreference list = (ListPreference) preference;
            if (list.findIndexOfValue((String) newValue) >= 0) {
                saveInt(preference.getKey(), Integer.parseInt((String) newValue));
            }
        }
        // System settings are authoritative; never persist a second copy in SharedPreferences.
        refresh();
        return false;
    }

    private void refresh() {
        mUpdating = true;
        try {
            final boolean enabled = Settings.System.getInt(mResolver, ENABLED, 1) != 0;
            mMainSwitch.setChecked(enabled);
            for (String key : GROUP_KEYS) {
                findPreference(key).setEnabled(enabled);
            }
            for (int i = 0; i < SWITCH_KEYS.length; i++) {
                final SwitchPreferenceCompat preference = findPreference(SWITCH_KEYS[i]);
                preference.setChecked(Settings.System.getInt(
                        mResolver, SWITCH_KEYS[i], SWITCH_DEFAULTS[i]) != 0);
            }
            for (int i = 0; i < CHOICE_KEYS.length; i++) {
                final ListPreference preference = findPreference(CHOICE_KEYS[i]);
                String value = Integer.toString(Settings.System.getInt(
                        mResolver, CHOICE_KEYS[i], CHOICE_DEFAULTS[i]));
                if (preference.findIndexOfValue(value) < 0) {
                    value = Integer.toString(CHOICE_DEFAULTS[i]);
                }
                preference.setValue(value);
            }
            for (String key : APP_KEYS) {
                updateAppPreference(key);
            }
            final boolean notifications = Settings.System.getInt(mResolver,
                    Settings.System.POP_UP_NOTIFICATION_JUMP_PORTRAIT, 0) != 0
                    || Settings.System.getInt(mResolver,
                            Settings.System.POP_UP_NOTIFICATION_JUMP_LANDSCAPE, 0) != 0;
            final boolean notificationDrag = Settings.System.getInt(mResolver,
                    Settings.System.ARKUI_SMALL_WINDOW_NOTIFICATION_DRAG, 0) != 0;
            findPreference(Settings.System.POP_UP_NOTIFICATION_BLACKLIST)
                    .setEnabled(enabled && (notifications || notificationDrag) && mAppsLoaded);
        } finally {
            mUpdating = false;
        }
    }

    private void loadApps() {
        final int generation = ++mLoadGeneration;
        final PackageManager pm = requireContext().getApplicationContext().getPackageManager();
        mAppsLoaded = false;
        refresh();
        ThreadUtils.postOnBackgroundThread(() -> {
            final Map<String, String> labels = new HashMap<>();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    labels.put(app.packageName, app.loadLabel(pm).toString());
                }
            }
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || getPreferenceScreen() == null
                        || generation != mLoadGeneration) {
                    return;
                }
                mAppLabels.clear();
                mAppLabels.putAll(labels);
                mAppsLoaded = true;
                refresh();
            });
        });
    }

    private Set<String> readApps(String key) {
        final Set<String> selected = new HashSet<>();
        final String value = Settings.System.getString(mResolver, key);
        if (!TextUtils.isEmpty(value)) {
            for (String name : value.split(";")) {
                if (!name.trim().isEmpty()) {
                    selected.add(name.trim());
                }
            }
        }
        return selected;
    }

    private void updateAppPreference(String key) {
        final MultiSelectListPreference preference = findPreference(key);
        final Set<String> selected = readApps(key);
        // Keep saved choices selectable even after an app is removed or loses its launcher icon.
        final Set<String> allPackages = new HashSet<>(mAppLabels.keySet());
        allPackages.addAll(selected);
        final List<String> packages = new ArrayList<>(allPackages);
        final Collator collator = Collator.getInstance();
        packages.sort((left, right) -> {
            final int result = collator.compare(mAppLabels.getOrDefault(left, left),
                    mAppLabels.getOrDefault(right, right));
            return result != 0 ? result : left.compareTo(right);
        });
        final CharSequence[] entries = new CharSequence[packages.size()];
        for (int i = 0; i < packages.size(); i++) {
            final String name = packages.get(i);
            final String label = mAppLabels.get(name);
            entries[i] = label == null || label.equals(name) ? name : label + "\n" + name;
        }
        preference.setEntries(entries);
        preference.setEntryValues(packages.toArray(new String[0]));
        preference.setValues(selected);
        preference.setEnabled(mAppsLoaded);
        preference.setSummary(!mAppsLoaded ? getString(R.string.arkui_small_window_apps_loading)
                : selected.isEmpty() ? getString(R.string.arkui_small_window_apps_none)
                : getResources().getQuantityString(R.plurals.arkui_small_window_apps_selected,
                        selected.size(), selected.size()));
    }

    private void saveInt(String key, int value) {
        if (!Settings.System.putInt(mResolver, key, value)) {
            showSaveError();
        }
    }

    private void showSaveError() {
        Toast.makeText(requireContext(), R.string.arkui_small_window_save_error,
                Toast.LENGTH_SHORT).show();
    }

    private void showResetDialog() {
        if (mResetDialog != null && mResetDialog.isShowing()) {
            return;
        }
        mResetDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.arkui_small_window_reset)
                .setMessage(R.string.arkui_small_window_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.arkui_small_window_reset, (dialog, which) -> {
                    boolean saved = true;
                    for (int i = 0; i < SWITCH_KEYS.length; i++) {
                        saved &= Settings.System.putInt(
                                mResolver, SWITCH_KEYS[i], SWITCH_DEFAULTS[i]);
                    }
                    for (int i = 0; i < CHOICE_KEYS.length; i++) {
                        saved &= Settings.System.putInt(
                                mResolver, CHOICE_KEYS[i], CHOICE_DEFAULTS[i]);
                    }
                    for (String key : APP_KEYS) {
                        saved &= Settings.System.putString(mResolver, key, "");
                    }
                    saved &= Settings.System.putInt(mResolver, ENABLED, 1);
                    if (!saved) {
                        showSaveError();
                    }
                    refresh();
                }).show();
    }

    private static List<String> getSettingKeys() {
        final List<String> keys = new ArrayList<>();
        java.util.Collections.addAll(keys, SWITCH_KEYS);
        java.util.Collections.addAll(keys, CHOICE_KEYS);
        java.util.Collections.addAll(keys, APP_KEYS);
        // Restore the master switch last so other defaults are already in place.
        keys.add(ENABLED);
        return keys;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_small_window_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return isAvailable(context);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    keys.add("small_window_reset");
                    keys.add("small_window_help");
                    keys.add("small_window_size_help");
                    return keys;
                }
            };
}
