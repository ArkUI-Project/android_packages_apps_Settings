/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.intelligence;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.arkui.IntelligenceSettings;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ArkuiIntelligenceSettings extends IntelligenceSettingsFragment {
    @Override protected int resource() { return R.xml.arkui_intelligence_settings; }
    @Override protected IntelligenceSettings.Option master() { return IntelligenceSettings.ENABLED; }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_intelligence_settings);
}
