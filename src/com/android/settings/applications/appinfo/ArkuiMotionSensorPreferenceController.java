/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.applications.appinfo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.UserHandle;

import androidx.preference.Preference;

import com.android.internal.arkui.MotionSensorSettings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.arkui.ArkuiMotionSensorSettings;

public final class ArkuiMotionSensorPreferenceController extends AppInfoPreferenceControllerBase {
    public ArkuiMotionSensorPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override protected Class<? extends SettingsPreferenceFragment> getDetailFragmentClass() {
        return ArkuiMotionSensorSettings.class;
    }

    @Override public void updateState(Preference preference) {
        if (preference == null || mParent == null || mParent.getPackageInfo() == null) return;
        final ApplicationInfo app = mParent.getPackageInfo().applicationInfo;
        final boolean available = app != null
                && UserHandle.getAppId(app.uid) >= Process.FIRST_APPLICATION_UID;
        preference.setVisible(available);
        if (!available) return;
        final int mode = MotionSensorSettings.getMode(mContext.getContentResolver(),
                app.packageName, UserHandle.getUserId(app.uid));
        preference.setSummary(mode == MotionSensorSettings.DENY ? R.string.arkui_motion_sensor_deny
                : mode == MotionSensorSettings.BLOCK_ON_OPEN ? R.string.arkui_motion_sensor_open
                : R.string.arkui_motion_sensor_allow);
    }
}
