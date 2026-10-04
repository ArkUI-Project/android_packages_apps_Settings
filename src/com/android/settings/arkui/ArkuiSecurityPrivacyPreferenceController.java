/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.safetycenter.SafetyCenterManagerWrapper;
import com.android.settings.safetycenter.TopLevelSafetyCenterEntryPreferenceController;

/** Uses one homepage entry with or without the device's Safety Center. */
public class ArkuiSecurityPrivacyPreferenceController
        extends TopLevelSafetyCenterEntryPreferenceController {
    public ArkuiSecurityPrivacyPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        // When disabled, let the preference's fragment open the security/privacy fallback page.
        return SafetyCenterManagerWrapper.get().isEnabled(mContext)
                && super.handlePreferenceTreeClick(preference);
    }
}
