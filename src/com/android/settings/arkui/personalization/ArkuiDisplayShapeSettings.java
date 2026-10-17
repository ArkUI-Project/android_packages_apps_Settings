/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.app.settings.SettingsEnums;
import android.os.Bundle;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ArkuiDisplayShapeSettings extends SettingsPreferenceFragment {
    private DisplayGeometryEditor mEditor;
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_display_shape_settings);
        mEditor = new DisplayGeometryEditor(this, true, state);
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mEditor != null) mEditor.save(state);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_display_shape_settings);
}
