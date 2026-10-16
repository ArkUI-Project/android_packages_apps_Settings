/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.ArkuiVersionInfo;

public class ArkuiVersionPreferenceController extends BasePreferenceController {
    public ArkuiVersionPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return ArkuiVersionInfo.getBuildName(mContext);
    }
}
