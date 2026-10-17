/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.R;

/** Version labels from the installed ROM, independent of the selected update channel. */
public final class ArkuiVersionInfo {
    private ArkuiVersionInfo() {}

    public static String getBuildName(Context context) {
        return SystemProperties.get("ro.lineage.version", context.getString(R.string.unknown));
    }

    public static String getLineageBaseVersion(Context context) {
        return SystemProperties.get("ro.lineage.build.version",
                context.getString(R.string.unknown));
    }

    public static String getVersionSummary(Context context) {
        final String unknown = context.getString(R.string.unknown);
        final String version = SystemProperties.get("ro.arkui.version", unknown);
        final String type = SystemProperties.get("org.arkui.releasetype");
        String label = unknown;
        if ("UNOFFICIAL".equals(type)) {
            label = "UNOFFICIAL";
        } else if ("OFFICIAL".equals(type)) {
            final String channel = SystemProperties.get("org.arkui.channel");
            final int number = SystemProperties.getInt("org.arkui.channel.number", 0);
            if ("stable".equals(channel)) {
                label = "Stable";
            } else if (number > 0 && "beta".equals(channel)) {
                label = "Beta" + number;
            } else if (number > 0 && "alpha".equals(channel)) {
                label = "Alpha" + number;
            }
        }
        return version + " · " + label;
    }
}
