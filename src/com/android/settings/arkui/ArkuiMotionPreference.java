/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.SliderPreference;
import com.android.settingslib.widget.preference.slider.R;

/** Expressive Settings slider with values and range labels in the parameter's units. */
public class ArkuiMotionPreference extends SliderPreference {
    private String mUnit = "";

    public ArkuiMotionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setIconSpaceReserved(false);
        setShowSliderValue(true);
        setTickVisible(false);
        setUpdatesContinuously(false);
        setLabelFormater(value -> formatValue(Math.round(value)));
    }

    public void setUnit(String unit) {
        if (!TextUtils.equals(mUnit, unit)) {
            mUnit = unit;
            notifyChanged();
        }
    }

    private String formatValue(int value) {
        return value + mUnit;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView start = (TextView) holder.findViewById(android.R.id.text1);
        TextView end = (TextView) holder.findViewById(android.R.id.text2);
        View labels = holder.findViewById(R.id.label_frame);
        if (start != null && end != null && labels != null) {
            start.setText(formatValue(getMin()));
            end.setText(formatValue(getMax()));
            labels.setVisibility(View.VISIBLE);
        }
    }
}
