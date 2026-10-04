/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import com.android.internal.arkui.SidebarContract;
import com.android.settings.core.BasePreferenceController;

/** Opens the standalone SystemApp's own settings under Special features. */
public final class SidebarPreferenceController extends BasePreferenceController {
    public SidebarPreferenceController(Context context, String key) { super(context, key); }
    public static boolean isAvailable(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        return users != null && !users.isManagedProfile()
                && context.getPackageManager().resolveActivity(
                        new Intent().setComponent(SidebarContract.SETTINGS), 0) != null;
    }
    @Override public int getAvailabilityStatus() {
        return isAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
