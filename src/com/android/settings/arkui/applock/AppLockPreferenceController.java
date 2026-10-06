/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.applock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;

import com.android.settings.core.BasePreferenceController;

/** App lock uses this user's own secure credential and trusted system authentication UI. */
public final class AppLockPreferenceController extends BasePreferenceController {
    public AppLockPreferenceController(Context context, String key) { super(context, key); }

    @Override public int getAvailabilityStatus() {
        return isAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    static boolean isAvailable(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
                && !context.getSystemService(UserManager.class).isManagedProfile();
    }
}
