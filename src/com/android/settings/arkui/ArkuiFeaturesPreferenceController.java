/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class ArkuiFeaturesPreferenceController extends BasePreferenceController {
    public ArkuiFeaturesPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return (ArkuiSmallWindowSettings.isAvailable(mContext)
                || SidebarPreferenceController.isAvailable(mContext))
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(R.string.arkui_features_summary);
    }
}
