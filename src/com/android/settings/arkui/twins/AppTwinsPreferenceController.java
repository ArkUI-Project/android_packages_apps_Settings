/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.content.Context;
import com.android.settings.core.BasePreferenceController;

public final class AppTwinsPreferenceController extends BasePreferenceController {
    public AppTwinsPreferenceController(Context context, String key) { super(context, key); }
    @Override public int getAvailabilityStatus() {
        return AppTwinStore.available(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
