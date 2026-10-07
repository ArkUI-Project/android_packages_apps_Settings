/*
 * Copyright (C) 2022 The Android Open Source Project
 * Copyright (C) 2026 The ArkUI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fuelgauge.batteryusage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.fuelgauge.BatteryUtils;

/** Custom preference for displaying the battery level as chart graph. */
public class BatteryHistoryPreference extends Preference {
    private static final String TAG = "BatteryHistoryPreference";

    private BatteryChartView mDailyChartView;
    private BatteryChartView mHourlyChartView;
    private BatteryChartPreferenceController mChartPreferenceController;
    private BatteryDiffData mUsageData;
    private String mSlotInformation;
    private boolean mShowTimeRange;
    private TextView mScreenOnValue;
    private TextView mScreenOffValue;
    private TextView mTimeRange;

    public BatteryHistoryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.arkui_battery_usage_card);
        setSelectable(false);
    }

    void setChartPreferenceController(BatteryChartPreferenceController controller) {
        mChartPreferenceController = controller;
        if (mDailyChartView != null && mHourlyChartView != null) {
            mChartPreferenceController.setBatteryChartView(mDailyChartView, mHourlyChartView);
        }
    }

    void setUsageData(BatteryDiffData data, String slotInformation, boolean showTimeRange) {
        mUsageData = data;
        mSlotInformation = slotInformation;
        mShowTimeRange = showTimeRange;
        updateUsageViews();
    }

    private void updateUsageViews() {
        if (mScreenOnValue == null) {
            return;
        }
        mTimeRange.setText(mSlotInformation == null
                ? getContext().getString(R.string.arkui_battery_all_days) : mSlotInformation);
        mTimeRange.setVisibility(mShowTimeRange ? View.VISIBLE : View.GONE);
        if (mUsageData == null || mUsageData.getEndTimestamp() <= mUsageData.getStartTimestamp()) {
            mScreenOnValue.setText("—");
            mScreenOffValue.setText("—");
            return;
        }
        final long duration = Math.max(0,
                mUsageData.getEndTimestamp() - mUsageData.getStartTimestamp());
        final long screenOn = Math.max(0, Math.min(duration, mUsageData.getScreenOnTime()));
        mScreenOnValue.setText(BatteryUtils.formatElapsedTimeWithoutComma(
                getContext(), screenOn, /* withSeconds= */ false, /* collapseTimeUnit= */ false));
        mScreenOffValue.setText(BatteryUtils.formatElapsedTimeWithoutComma(
                getContext(), duration - screenOn,
                /* withSeconds= */ false, /* collapseTimeUnit= */ false));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        final long startTime = System.currentTimeMillis();
        final TextView companionTextView = (TextView) view.findViewById(R.id.companion_text);
        mDailyChartView = (BatteryChartView) view.findViewById(R.id.daily_battery_chart);
        mDailyChartView.setCompanionTextView(companionTextView);
        mHourlyChartView = (BatteryChartView) view.findViewById(R.id.hourly_battery_chart);
        mHourlyChartView.setCompanionTextView(companionTextView);
        mScreenOnValue = (TextView) view.findViewById(R.id.battery_screen_on_value);
        mScreenOffValue = (TextView) view.findViewById(R.id.battery_screen_off_value);
        mTimeRange = (TextView) view.findViewById(R.id.battery_usage_time_range);
        updateUsageViews();
        if (mChartPreferenceController != null) {
            mChartPreferenceController.setBatteryChartView(mDailyChartView, mHourlyChartView);
        }
        BatteryUtils.logRuntime(TAG, "onBindViewHolder", startTime);
    }
}
