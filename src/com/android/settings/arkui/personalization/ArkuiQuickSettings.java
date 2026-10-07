/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ArkuiQuickSettings extends PersonalizationPreferenceFragment {
    @Override protected int resource() { return R.xml.arkui_quick_settings; }
    @Override protected void configure() {
        bind("qs_quick_pulldown", Table.LINEAGE_SYSTEM, 0);
        bind("qs_show_brightness_slider", Table.LINEAGE_SECURE, 1);
        bind("qs_brightness_slider_position", Table.LINEAGE_SECURE, 0);
        bind("qs_show_auto_brightness", Table.LINEAGE_SECURE, 1);
    }
    @Override protected void afterRefresh() {
        final boolean brightness = read("qs_show_brightness_slider", Table.LINEAGE_SECURE, 1) != 0;
        findPreference("qs_brightness_slider_position").setEnabled(brightness);
        findPreference("qs_show_auto_brightness").setEnabled(brightness && getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available));
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_quick_settings);
}
