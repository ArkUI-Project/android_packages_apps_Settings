/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.hardware.Sensor;
import android.hardware.SensorManager;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class MistouchProtectionSettings extends ArkuiFeatureToggleFragment {
    @Override protected int resource() { return R.xml.arkui_mistouch_protection; }
    @Override protected String setting() { return "arkui_mistouch_protection"; }
    @Override protected boolean supported() {
        SensorManager sensors = requireContext().getSystemService(SensorManager.class);
        return sensors != null && (sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
                || sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_mistouch_protection);
}
