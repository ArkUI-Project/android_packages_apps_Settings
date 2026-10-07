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

import static com.android.settingslib.fuelgauge.BatteryStatus.BATTERY_LEVEL_UNKNOWN;

import androidx.annotation.NonNull;
import androidx.core.util.Preconditions;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The view model of {@code BatteryChartView} */
class BatteryChartViewModel {
    private static final String TAG = "BatteryChartViewModel";

    public static final int SELECTED_INDEX_ALL = -1;
    public static final int SELECTED_INDEX_INVALID = -2;

    // We need at least 2 levels to draw a trapezoid.
    private static final int MIN_LEVELS_DATA_SIZE = 2;

    enum AxisLabelPosition {
        BETWEEN_TRAPEZOIDS,
        CENTER_OF_TRAPEZOIDS,
    }

    interface LabelTextGenerator {
        /** Generates the label text. The text may be abbreviated to save space. */
        String generateText(List<Long> timestamps, int index);

        /** Generates the full text for slot information. */
        String generateFullText(List<Long> timestamps, int index);

        /** Generates the full text for accessibility. */
        String generateContentDescription(List<Long> timestamps, int index);

        /** Generates the battery level text of a slot for accessibility.*/
        String generateSlotBatteryLevelText(List<Integer> levels, int index);
    }

    private final List<Integer> mLevels;
    private final List<Long> mTimestamps;
    private final AxisLabelPosition mAxisLabelPosition;
    private final LabelTextGenerator mLabelTextGenerator;
    private final String[] mTexts;
    private final String[] mFullTexts;
    private final String[] mContentDescription;
    private final String[] mBatteryLevelTexts;
    private List<Integer> mConsumptionValues;

    private int mSelectedIndex = SELECTED_INDEX_ALL;
    private int mHighlightSlotIndex = SELECTED_INDEX_INVALID;

    BatteryChartViewModel(
            @NonNull List<Integer> levels,
            @NonNull List<Long> timestamps,
            @NonNull AxisLabelPosition axisLabelPosition,
            @NonNull LabelTextGenerator labelTextGenerator) {
        Preconditions.checkArgument(
                levels.size() == timestamps.size() && levels.size() >= MIN_LEVELS_DATA_SIZE,
                String.format(
                        Locale.ENGLISH,
                        "Invalid BatteryChartViewModel levels.size: %d, timestamps.size: %d.",
                        levels.size(),
                        timestamps.size()));
        mLevels = levels;
        mTimestamps = timestamps;
        mAxisLabelPosition = axisLabelPosition;
        mLabelTextGenerator = labelTextGenerator;
        mTexts = new String[size()];
        mFullTexts = new String[size()];
        mContentDescription = new String[size()];
        // Last one for SELECTED_INDEX_ALL
        mBatteryLevelTexts = new String[size() + 1];
    }

    public int size() {
        return mLevels.size();
    }

    public Integer getLevel(int index) {
        return mLevels.get(index);
    }

    void setConsumptionValues(List<Integer> values) {
        Preconditions.checkArgument(values.size() == size() - 1);
        mConsumptionValues = values;
        Arrays.fill(mBatteryLevelTexts, null);
    }

    boolean isConsumptionChart() {
        return mConsumptionValues != null;
    }

    int getConsumption(int index) {
        if (mConsumptionValues == null) {
            return BATTERY_LEVEL_UNKNOWN;
        }
        if (index != SELECTED_INDEX_ALL) {
            return mConsumptionValues.get(index);
        }
        int total = 0;
        boolean hasValue = false;
        for (int value : mConsumptionValues) {
            if (value != BATTERY_LEVEL_UNKNOWN) {
                total += value;
                hasValue = true;
            }
        }
        return hasValue ? total : BATTERY_LEVEL_UNKNOWN;
    }

    int getChartMaximum() {
        int maximum = 100;
        if (mConsumptionValues != null) {
            for (int value : mConsumptionValues) {
                maximum = Math.max(maximum, ((value + 99) / 100) * 100);
            }
        }
        return maximum;
    }

    long getTimestamp(int index) {
        return mTimestamps.get(index);
    }

    public String getText(int index) {
        if (mTexts[index] == null) {
            mTexts[index] = mLabelTextGenerator.generateText(mTimestamps, index);
        }
        return mTexts[index];
    }

    public String getFullText(int index) {
        if (mFullTexts[index] == null) {
            mFullTexts[index] = mLabelTextGenerator.generateFullText(mTimestamps, index);
        }
        return mFullTexts[index];
    }

    public String getContentDescription(int index) {
        if (mContentDescription[index] == null) {
            mContentDescription[index] =
                    mLabelTextGenerator.generateContentDescription(mTimestamps, index);
        }
        return mContentDescription[index];
    }

    public String getSlotBatteryLevelText(int index) {
        final int textIndex = index != SELECTED_INDEX_ALL ? index : size();
        if (mBatteryLevelTexts[textIndex] == null) {
            if (isConsumptionChart()) {
                final int consumption = getConsumption(index);
                mBatteryLevelTexts[textIndex] = consumption == BATTERY_LEVEL_UNKNOWN ? "—"
                        : NumberFormat.getPercentInstance().format(consumption / 100d);
            } else {
                mBatteryLevelTexts[textIndex] =
                        mLabelTextGenerator.generateSlotBatteryLevelText(mLevels, index);
            }
        }
        return mBatteryLevelTexts[textIndex];
    }

    public AxisLabelPosition axisLabelPosition() {
        return mAxisLabelPosition;
    }

    public int selectedIndex() {
        return mSelectedIndex;
    }

    public void setSelectedIndex(int index) {
        mSelectedIndex = index;
    }

    public int getHighlightSlotIndex() {
        return mHighlightSlotIndex;
    }

    public void setHighlightSlotIndex(int index) {
        mHighlightSlotIndex = index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLevels, mTimestamps, mSelectedIndex, mAxisLabelPosition,
                mConsumptionValues);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof BatteryChartViewModel)) {
            return false;
        }
        final BatteryChartViewModel batteryChartViewModel = (BatteryChartViewModel) other;
        return Objects.equals(mLevels, batteryChartViewModel.mLevels)
                && Objects.equals(mTimestamps, batteryChartViewModel.mTimestamps)
                && Objects.equals(mConsumptionValues, batteryChartViewModel.mConsumptionValues)
                && mAxisLabelPosition == batteryChartViewModel.mAxisLabelPosition
                && mSelectedIndex == batteryChartViewModel.mSelectedIndex;
    }

    @Override
    public String toString() {
        // Generate all the texts and full texts.
        for (int i = 0; i < size(); i++) {
            getText(i);
            getFullText(i);
        }

        return new StringBuilder()
                .append("levels: " + Objects.toString(mLevels))
                .append(", timestamps: " + Objects.toString(mTimestamps))
                .append(", texts: " + Arrays.toString(mTexts))
                .append(", fullTexts: " + Arrays.toString(mFullTexts))
                .append(", axisLabelPosition: " + mAxisLabelPosition)
                .append(", selectedIndex: " + mSelectedIndex)
                .toString();
    }
}
