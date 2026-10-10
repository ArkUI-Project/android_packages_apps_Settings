/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.text.format.DateFormat;
import androidx.preference.ListPreference;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import lineageos.providers.LineageSettings;

@SearchIndexable
public final class ArkuiStatusBarSettings extends PersonalizationPreferenceFragment {
    @Override protected int resource() { return R.xml.arkui_status_bar_settings; }
    @Override protected void configure() {
        bind("arkui_status_bar_app_icons", Table.SECURE, 0);
        bind("status_bar_clock", Table.LINEAGE_SYSTEM, 2);
        bind("clock_seconds", Table.SECURE, 0);
        bind("arkui_clock_date_style", Table.SECURE, 0);
        bind("status_bar_clock_auto_hide", Table.LINEAGE_SYSTEM, 0);
        bind("status_bar_am_pm", Table.LINEAGE_SYSTEM, 2);
        bind("status_bar_battery_style", Table.LINEAGE_SYSTEM, 0);
        bind("status_bar_show_battery_percent", Table.LINEAGE_SYSTEM, 0);
        bind("network_traffic_mode", Table.LINEAGE_SECURE, 0);
        bind("network_traffic_position", Table.LINEAGE_SECURE, 1);
        bind("network_traffic_autohide", Table.LINEAGE_SECURE, 0);
        bind("network_traffic_hide_arrows", Table.LINEAGE_SECURE, 0);
        bind("network_traffic_units", Table.LINEAGE_SECURE, 2);
    }
    @Override protected void afterRefresh() {
        findPreference("status_bar_show_battery_percent").setEnabled(
                read("status_bar_battery_style", Table.LINEAGE_SYSTEM, 0) != 2);
        findPreference("status_bar_am_pm").setEnabled(!DateFormat.is24HourFormat(requireContext()));
        final boolean traffic = read("network_traffic_mode", Table.LINEAGE_SECURE, 0) != 0;
        for (String key : new String[] {"network_traffic_position", "network_traffic_autohide",
                "network_traffic_hide_arrows", "network_traffic_units"}) {
            findPreference(key).setEnabled(traffic);
        }
        boolean cutoutCentered = false;
        final var metrics = requireActivity().getWindowManager().getCurrentWindowMetrics();
        final var cutout = metrics.getWindowInsets().getDisplayCutout();
        if (cutout != null) {
            final var bounds = cutout.getBoundingRectTop();
            final int center = metrics.getBounds().width() / 2;
            cutoutCentered = !bounds.isEmpty() && bounds.left <= center && bounds.right >= center;
        }
        final ListPreference clock = findPreference("status_bar_clock");
        int clockPosition = read("status_bar_clock", Table.LINEAGE_SYSTEM, 2);
        if (cutoutCentered && clockPosition == 1) {
            LineageSettings.System.putIntForUser(requireContext().getContentResolver(),
                    "status_bar_clock", 2, requireContext().getUserId());
            clockPosition = 2;
            clock.setValue("2");
        }
        final ListPreference position = findPreference("network_traffic_position");
        int trafficPosition = read("network_traffic_position", Table.LINEAGE_SECURE, 1);
        final boolean clockCentered = cutoutCentered || clockPosition == 1;
        if (clockCentered && trafficPosition == 1) {
            // Keep a dormant centered traffic setting from overlapping a newly centered clock.
            LineageSettings.Secure.putIntForUser(requireContext().getContentResolver(),
                    "network_traffic_position", 2, requireContext().getUserId());
            trafficPosition = 2;
            position.setValue("2");
        }
        final boolean centerOccupied = cutoutCentered || traffic && trafficPosition == 1;
        clock.setEntries(centerOccupied ? R.array.arkui_clock_side_entries : R.array.arkui_clock_entries);
        clock.setEntryValues(centerOccupied ? R.array.arkui_clock_side_values : R.array.arkui_clock_values);
        position.setEntries(clockCentered ? R.array.arkui_traffic_side_entries : R.array.arkui_traffic_position_entries);
        position.setEntryValues(clockCentered ? R.array.arkui_traffic_side_values : R.array.arkui_traffic_position_values);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_status_bar_settings);
}
