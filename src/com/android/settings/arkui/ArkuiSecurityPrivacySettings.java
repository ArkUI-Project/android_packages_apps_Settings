/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.arkui.applock.AppLockPreferenceController;
import com.android.settings.arkui.privacy.PrivacyPasswordPreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.safetycenter.SafetyCenterManagerWrapper;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.PreferenceCategoryController;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

/** Keeps security and privacy accessible together when Safety Center is disabled. */
@SearchIndexable
public class ArkuiSecurityPrivacySettings extends DashboardFragment {
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(PreferenceCategoryController.class).setChildren(List.of(
                use(PrivacyPasswordPreferenceController.class),
                use(AppLockPreferenceController.class)));
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SECURITY;
    }

    @Override
    protected String getLogTag() {
        return "ArkuiSecurityPrivacy";
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arkui_security_privacy_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_security_privacy_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return !SafetyCenterManagerWrapper.get().isEnabled(context);
                }
            };
}
