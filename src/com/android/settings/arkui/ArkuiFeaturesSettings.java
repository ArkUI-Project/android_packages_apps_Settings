/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/** Settings for ArkUI features, including native small windows. */
@SearchIndexable
public class ArkuiFeaturesSettings extends DashboardFragment {
    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return "ArkuiFeaturesSettings";
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arkui_features_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_features_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return ArkuiSmallWindowSettings.isAvailable(context);
                }
            };
}
