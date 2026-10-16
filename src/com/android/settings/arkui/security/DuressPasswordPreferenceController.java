/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.storage.StorageManager;

import com.android.settings.core.BasePreferenceController;

public final class DuressPasswordPreferenceController extends BasePreferenceController {
    public DuressPasswordPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return UserHandle.myUserId() == UserHandle.USER_SYSTEM
                && StorageManager.isFileEncrypted()
                && mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_SECURE_LOCK_SCREEN)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
