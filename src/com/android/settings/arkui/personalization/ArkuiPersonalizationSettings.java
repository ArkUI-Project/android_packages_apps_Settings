/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.app.settings.SettingsEnums;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ArkuiPersonalizationSettings extends DashboardFragment {
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override protected String getLogTag() { return "ArkuiPersonalization"; }
    @Override protected int getPreferenceScreenResId() { return R.xml.arkui_personalization_settings; }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_personalization_settings);
}
