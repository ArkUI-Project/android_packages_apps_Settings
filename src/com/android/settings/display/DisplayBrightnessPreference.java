/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinearFloat;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGammaFloat;

import android.content.Context;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Display;

import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.RestrictedSliderPreference;

import java.text.NumberFormat;

/** Inline expressive brightness control with the same gamma conversion as SystemUI. */
public class DisplayBrightnessPreference extends RestrictedSliderPreference {
    private final DisplayManager mDisplayManager;
    private final Display mDisplay;
    private boolean mTracking;
    private final DisplayManager.DisplayListener mListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) {}
        @Override public void onDisplayRemoved(int id) {}
        @Override public void onDisplayChanged(int id) {
            if (id == mDisplay.getDisplayId() && !mTracking) updateValue();
        }
    };

    public DisplayBrightnessPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDisplay = mDisplayManager.getDisplay(context.getDisplayId());
        setPersistent(false);
        setMin(GAMMA_SPACE_MIN);
        setMax(GAMMA_SPACE_MAX);
        setUpdatesContinuously(true);
        setTickVisible(false);
        setShowSliderValue(false);
        setLabelFormater(value -> NumberFormat.getPercentInstance().format(value / GAMMA_SPACE_MAX));
        setOnPreferenceChangeListener((preference, value) -> {
            final BrightnessInfo info = mDisplay.getBrightnessInfo();
            if (info == null || !isEnabled()) return false;
            final float brightness = convertGammaToLinearFloat((Integer) value,
                    info.brightnessMinimum, info.brightnessMaximum);
            if (mTracking) {
                mDisplayManager.setTemporaryBrightness(mDisplay.getDisplayId(), brightness);
            } else {
                mDisplayManager.setBrightness(mDisplay.getDisplayId(), brightness);
            }
            return true;
        });
        setExtraTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(
                    com.google.android.material.slider.Slider slider) {
                mTracking = true;
            }
            @Override public void onStopTrackingTouch(
                    com.google.android.material.slider.Slider slider) {
                mTracking = false;
                final BrightnessInfo info = mDisplay.getBrightnessInfo();
                if (info != null && isEnabled()) {
                    mDisplayManager.setBrightness(mDisplay.getDisplayId(),
                            convertGammaToLinearFloat(getValue(),
                                    info.brightnessMinimum, info.brightnessMaximum));
                }
                updateValue();
            }
        });
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mDisplayManager.registerDisplayListener(mListener, new Handler(Looper.getMainLooper()),
                DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS);
        updateValue();
    }

    @Override
    public void onDetached() {
        mDisplayManager.unregisterDisplayListener(mListener);
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (!mTracking) updateValue();
        super.onBindViewHolder(holder);
    }

    private void updateValue() {
        final BrightnessInfo info = mDisplay.getBrightnessInfo();
        if (info != null && Float.isFinite(info.brightness)) {
            setValue(convertLinearToGammaFloat(info.brightness,
                    info.brightnessMinimum, info.brightnessMaximum));
        }
    }
}
