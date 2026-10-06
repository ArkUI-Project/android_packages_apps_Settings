/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

/** Connected expressive buttons for switching between standard and drawer home screens. */
public class ArkuiDesktopModePreference extends Preference {
    private boolean mStandard;
    private boolean mBinding;

    public ArkuiDesktopModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.arkui_desktop_mode_preference);
        setPersistent(false);
        setSelectable(false);
        setStandard(false);
    }

    public void setStandard(boolean standard) {
        mStandard = standard;
        setSummary(standard ? R.string.arkui_desktop_standard_summary
                : R.string.arkui_desktop_drawer_mode_summary);
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) holder.findViewById(
                R.id.desktop_mode_group);
        group.clearOnButtonCheckedListeners();
        group.check(mStandard ? R.id.desktop_mode_standard : R.id.desktop_mode_drawer);
        ((MaterialButton) holder.findViewById(R.id.desktop_mode_standard)).setEnabled(isEnabled());
        ((MaterialButton) holder.findViewById(R.id.desktop_mode_drawer)).setEnabled(isEnabled());
        group.addOnButtonCheckedListener((buttons, id, checked) -> {
            boolean standard = id == R.id.desktop_mode_standard;
            if (!checked || mBinding || standard == mStandard) return;
            if (callChangeListener(standard)) {
                setStandard(standard);
            } else {
                // System Settings waits for Launcher's provider to persist the selection.
                mBinding = true;
                buttons.check(mStandard ? R.id.desktop_mode_standard : R.id.desktop_mode_drawer);
                mBinding = false;
            }
        });
    }
}
