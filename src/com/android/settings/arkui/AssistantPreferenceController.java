/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import com.android.settings.core.BasePreferenceController;

public final class AssistantPreferenceController extends BasePreferenceController {
    public AssistantPreferenceController(Context context, String key) { super(context, key); }
    @Override public int getAvailabilityStatus() {
        return !mContext.getSystemService(UserManager.class).isManagedProfile()
                && mContext.getPackageManager().resolveActivity(new Intent().setComponent(new ComponentName(
                        "org.arkui.assistant", "org.arkui.assistant.AssistantSettingsActivity")), 0) != null
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
