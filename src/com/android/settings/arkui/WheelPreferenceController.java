/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import com.android.settings.core.BasePreferenceController;

public final class WheelPreferenceController extends BasePreferenceController {
    public WheelPreferenceController(Context c, String key) { super(c, key); }
    @Override public int getAvailabilityStatus() {
        return !mContext.getSystemService(UserManager.class).isManagedProfile()
                && mContext.getPackageManager().resolveActivity(new Intent().setComponent(new ComponentName(
                        "org.arkui.wheel", "org.arkui.wheel.WheelSettingsActivity")), 0) != null
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
