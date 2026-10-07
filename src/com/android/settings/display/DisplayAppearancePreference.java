/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.Utils;

import com.google.android.material.card.MaterialCardView;

/** Direct theme selection, leaving automatic schedules to the existing dark theme settings. */
public class DisplayAppearancePreference extends Preference {
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifyChanged();
        }
    };

    public DisplayAppearancePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.arkui_display_appearance);
        setPersistent(false);
        setSelectable(false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        getContext().registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDetached() {
        getContext().unregisterReceiver(mReceiver);
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        final boolean dark = (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bindCard(holder, R.id.display_light_card, R.id.display_light_preview, false, !dark);
        bindCard(holder, R.id.display_dark_card, R.id.display_dark_preview, true, dark);
    }

    private void bindCard(PreferenceViewHolder holder, int cardId, int previewId,
            boolean dark, boolean selected) {
        final MaterialCardView card = (MaterialCardView) holder.findViewById(cardId);
        ((DisplayModePreview) holder.findViewById(previewId)).setDark(dark);
        card.setChecked(selected);
        card.setStrokeColor(Utils.getColorAccentDefaultColor(getContext()));
        card.setStrokeWidth(Math.round(getContext().getResources().getDisplayMetrics().density
                * (selected ? 3 : 0)));
        card.setEnabled(isEnabled()
                && !getContext().getSystemService(PowerManager.class).isPowerSaveMode());
        card.setOnClickListener(view -> {
            if (!selected) {
                getContext().getSystemService(UiModeManager.class).setNightModeActivated(dark);
            }
        });
        card.setSelected(selected);
    }
}
