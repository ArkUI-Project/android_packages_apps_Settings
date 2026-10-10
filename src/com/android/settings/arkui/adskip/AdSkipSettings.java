/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import com.android.settingslib.accessibility.AccessibilityUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Per-user configuration; the accessibility component itself is the master switch. */
final class AdSkipSettings {
    static final String EXCLUDED = "arkui_ad_skip_excluded_packages";
    static final String COUNT = "arkui_ad_skip_count";

    private AdSkipSettings() { }

    static ComponentName component(Context context) {
        return new ComponentName(context, AdSkipService.class);
    }

    static boolean enabled(Context context) {
        return AccessibilityUtils.getEnabledServicesFromSettings(context, context.getUserId())
                .contains(component(context));
    }

    static void setEnabled(Context context, boolean enabled) {
        AccessibilityUtils.setAccessibilityServiceState(context, component(context), enabled,
                context.getUserId());
    }

    static Set<String> excluded(Context context) {
        String value = Settings.Secure.getString(context.getContentResolver(), EXCLUDED);
        Set<String> result = new TreeSet<>();
        if (value != null && !value.isEmpty()) result.addAll(Arrays.asList(value.split(":")));
        return result;
    }

    static void setExcluded(Context context, String packageName, boolean excluded) {
        Set<String> packages = excluded(context);
        if (excluded) packages.add(packageName);
        else packages.remove(packageName);
        Settings.Secure.putString(context.getContentResolver(), EXCLUDED, String.join(":", packages));
    }
}
