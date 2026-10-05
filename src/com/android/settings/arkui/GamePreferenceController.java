/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import com.android.settings.core.BasePreferenceController;
public final class GamePreferenceController extends BasePreferenceController {
    public GamePreferenceController(Context c,String key) { super(c,key); }
    @Override public int getAvailabilityStatus() {
        return !mContext.getSystemService(UserManager.class).isManagedProfile()
                && mContext.getPackageManager().resolveActivity(new Intent("org.arkui.game.SETTINGS")
                        .setPackage("org.arkui.game"),0)!=null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
