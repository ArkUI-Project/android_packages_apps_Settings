/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.arkui.SmallWindowSettings;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;

import java.util.List;

/** System Settings home for Launcher options, retaining Launcher's existing preference storage. */
@SearchIndexable
public class ArkuiDesktopSettings extends DashboardFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "ArkuiDesktopSettings";
    private static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    private static final Uri PREFERENCES_URI =
            Uri.parse("content://" + LAUNCHER_PACKAGE + ".desktop_settings");
    private static final String SWIPE_UP = Settings.System.ARKUI_SMALL_WINDOW_SWIPE_UP;
    private static final String[] LAUNCHER_SWITCHES = {
            "pref_workspace_lock", "pref_add_icon_to_home", "pref_allowRotation",
            "pref_desktop_show_labels", "pref_enable_minus_one", "pref_drawer_open_keyboard",
            "pref_allapps_themed_icons", "pref_drawer_show_labels", "pref_sleep_gesture",
            "pref_landscape_app_animation",
            "pref_stacked_recents",
    };
    private static final String[] OPTIONAL_PREFERENCES = {
            "pref_allowRotation", "pref_enable_minus_one", "pref_icon_badging", "pref_suggestions",
    };

    private ContentResolver mResolver;
    private int mGeneration;
    private boolean mLoaded;
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            if (isAdded() && getPreferenceScreen() != null) {
                refreshSystemPreferences();
            }
        }
    };

    static boolean isAvailable(Context context) {
        final ProviderInfo provider = context.getPackageManager().resolveContentProvider(
                PREFERENCES_URI.getAuthority(), 0);
        return provider != null && LAUNCHER_PACKAGE.equals(provider.packageName)
                && provider.applicationInfo.isSystemApp()
                && !context.getSystemService(UserManager.class).isManagedProfile();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arkui_desktop_settings;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mResolver = requireContext().getContentResolver();
        findPreference("pref_standard_desktop").setOnPreferenceChangeListener(this);
        for (String key : LAUNCHER_SWITCHES) {
            findPreference(key).setOnPreferenceChangeListener(this);
        }
        findPreference(SWIPE_UP).setOnPreferenceChangeListener(this);
        findPreference("desktop_status").setOnPreferenceClickListener(preference -> {
            requestPreferences(null, false);
            return true;
        });
        setLauncherPreferencesEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAvailable(requireContext())) {
            finish();
            return;
        }
        mResolver.registerContentObserver(Settings.System.getUriFor(SWIPE_UP), false, mObserver);
        mResolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.ARKUI_SMALL_WINDOW_ENABLED), false, mObserver);
        mResolver.registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.NOTIFICATION_BADGING), false, mObserver);
        mResolver.registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS), false, mObserver);
        refreshSystemPreferences();
        requestPreferences(null, false);
    }

    @Override
    public void onPause() {
        ++mGeneration;
        mResolver.unregisterContentObserver(mObserver);
        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(newValue instanceof Boolean)) {
            return false;
        }
        final boolean checked = (Boolean) newValue;
        if (SWIPE_UP.equals(preference.getKey())) {
            final boolean saved = Settings.System.putInt(mResolver, SWIPE_UP, checked ? 1 : 0);
            if (!saved) {
                showSaveError();
            }
            return saved;
        }
        requestPreferences(preference.getKey(), checked);
        // Update the switch only after the Launcher has accepted and persisted the value.
        return false;
    }

    private void requestPreferences(String key, boolean value) {
        final int generation = ++mGeneration;
        final ContentResolver resolver = mResolver;
        setLauncherPreferencesEnabled(false);
        final Preference status = findPreference("desktop_status");
        status.setTitle(R.string.arkui_desktop_loading);
        status.setEnabled(false);
        status.setVisible(!mLoaded);
        ThreadUtils.postOnBackgroundThread(() -> {
            Bundle response = null;
            try {
                final Bundle extras = new Bundle();
                extras.putBoolean("value", value);
                response = resolver.call(PREFERENCES_URI,
                        key == null ? "get_preferences" : "set_preference", key, extras);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to access Launcher preferences", e);
            }
            final Bundle result = response;
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mGeneration || getPreferenceScreen() == null) {
                    return;
                }
                final Bundle values = result == null ? null : result.getBundle("values");
                final Bundle available = result == null ? null : result.getBundle("available");
                if (values == null || available == null) {
                    status.setTitle(R.string.arkui_desktop_unavailable);
                    status.setEnabled(true);
                    status.setVisible(true);
                    if (key != null) {
                        showSaveError();
                    }
                    return;
                }
                for (String switchKey : LAUNCHER_SWITCHES) {
                    final SwitchPreferenceCompat preference = findPreference(switchKey);
                    preference.setChecked(values.getBoolean(switchKey));
                }
                for (String optionalKey : OPTIONAL_PREFERENCES) {
                    findPreference(optionalKey).setVisible(available.getBoolean(optionalKey));
                }
                final boolean standard = values.getBoolean("pref_standard_desktop");
                final ArkuiDesktopModePreference mode = findPreference("pref_standard_desktop");
                mode.setStandard(standard);
                findPreference("desktop_drawer").setVisible(!standard);
                findPreference("pref_add_icon_to_home").setVisible(!standard);
                mLoaded = true;
                status.setVisible(false);
                setLauncherPreferencesEnabled(true);
                refreshSystemPreferences();
            });
        });
    }

    private void setLauncherPreferencesEnabled(boolean enabled) {
        findPreference("pref_standard_desktop").setEnabled(enabled);
        findPreference("desktop_layout").setEnabled(enabled);
        findPreference("desktop_drawer").setEnabled(enabled);
        findPreference("pref_sleep_gesture").setEnabled(enabled);
        findPreference("pref_landscape_app_animation").setEnabled(enabled);
        findPreference("pref_stacked_recents").setEnabled(enabled);
    }

    private void refreshSystemPreferences() {
        final SwitchPreferenceCompat swipe = findPreference(SWIPE_UP);
        swipe.setVisible(ArkuiSmallWindowSettings.isAvailable(requireContext()));
        final boolean smallWindowsEnabled = SmallWindowSettings.isEnabled(requireContext());
        swipe.setEnabled(smallWindowsEnabled);
        swipe.setChecked(Settings.System.getInt(mResolver, SWIPE_UP, 0) != 0);
        swipe.setSummary(smallWindowsEnabled ? R.string.arkui_small_window_swipe_up_summary
                : R.string.arkui_desktop_swipe_unavailable);

        final Preference dots = findPreference("pref_icon_badging");
        final boolean badging = Settings.Secure.getInt(mResolver,
                Settings.Secure.NOTIFICATION_BADGING, 1) != 0;
        final ComponentName listener = new ComponentName(LAUNCHER_PACKAGE,
                "com.android.launcher3.notification.NotificationListener");
        final String listeners = Settings.Secure.getString(mResolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        boolean hasAccess = false;
        if (!TextUtils.isEmpty(listeners)) {
            for (String component : listeners.split(":")) {
                hasAccess |= listener.equals(ComponentName.unflattenFromString(component));
            }
        }
        final boolean needsAccess = badging && !hasAccess;
        dots.setSummary(needsAccess ? R.string.arkui_desktop_notification_access
                : badging ? R.string.switch_on_text : R.string.switch_off_text);
        final String highlight = needsAccess ? listener.flattenToString() : "notification_badging";
        final Bundle arguments = new Bundle();
        arguments.putString(":settings:fragment_args_key", highlight);
        dots.setIntent(new Intent(needsAccess ? Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                : Settings.ACTION_NOTIFICATION_SETTINGS)
                .putExtra(":settings:fragment_args_key", highlight)
                .putExtra(":settings:show_fragment_args", arguments));
    }

    private void showSaveError() {
        Toast.makeText(requireContext(), R.string.arkui_desktop_save_error,
                Toast.LENGTH_SHORT).show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_desktop_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return isAvailable(context);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    keys.add("desktop_status");
                    if (!ArkuiSmallWindowSettings.isAvailable(context)) {
                        keys.add(SWIPE_UP);
                    }
                    if (isAvailable(context)) {
                        // Index only the same device-specific options that the page can display.
                        Bundle available = null;
                        try {
                            final Bundle result = context.getContentResolver().call(
                                    PREFERENCES_URI, "get_preferences", null, null);
                            available = result == null ? null : result.getBundle("available");
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Unable to index optional Launcher preferences", e);
                        }
                        for (String key : OPTIONAL_PREFERENCES) {
                            if (available == null || !available.getBoolean(key)) {
                                keys.add(key);
                            }
                        }
                    }
                    return keys;
                }
            };
}
