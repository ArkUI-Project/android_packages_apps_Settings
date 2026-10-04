/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;

public class DesktopPreferenceController extends BasePreferenceController {
    public DesktopPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return ArkuiDesktopSettings.isAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
