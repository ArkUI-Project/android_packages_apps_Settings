/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;

/** Exposes small-window settings only on supported personal-user mobile builds. */
public class SmallWindowPreferenceController extends BasePreferenceController {
    public SmallWindowPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return ArkuiSmallWindowSettings.isAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
