/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.content.Context;
import android.os.UserManager;

import com.android.settings.core.BasePreferenceController;

public final class PrivacyPasswordPreferenceController extends BasePreferenceController {
    public PrivacyPasswordPreferenceController(Context context, String key) { super(context, key); }
    @Override public int getAvailabilityStatus() {
        return mContext.getSystemService(UserManager.class).isManagedProfile()
                ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }
}
