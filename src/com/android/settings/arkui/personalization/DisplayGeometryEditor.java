/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.SliderPreference;

/** Local draft editing: sliders update the preview; only Apply changes system resources. */
final class DisplayGeometryEditor {
    private final SettingsPreferenceFragment mHost;
    private final DisplayGeometrySettings mModel;
    private final boolean mShape;
    private final boolean mOwner;
    private final int[] mValues;
    private int[] mApplied;
    private final SliderPreference[] mSliders = new SliderPreference[DisplayGeometrySettings.COUNT];
    private final DisplayGeometryPreview mPreview;
    private final Preference mApply, mReset;
    private SwitchPreferenceCompat mCutout;
    private boolean mBusy;

    DisplayGeometryEditor(SettingsPreferenceFragment host, boolean shape, Bundle state) {
        mHost = host;
        mShape = shape;
        mOwner = host.requireContext().getUserId() == 0;
        mModel = new DisplayGeometrySettings(host.requireContext());
        mApplied = mModel.read();
        int[] saved = state == null || !mModel.uniqueId.equals(state.getString("geometry_display"))
                ? null : state.getIntArray("geometry_draft");
        mValues = saved != null && saved.length == DisplayGeometrySettings.COUNT
                ? saved.clone() : mApplied.clone();
        DisplayGeometrySettings.clamp(mValues);
        PreferenceCategory group = host.findPreference("arkui_geometry");
        LayoutPreference preview = new LayoutPreference(host.requireContext(), R.layout.arkui_geometry_preview);
        preview.setKey("geometry_preview");
        preview.setSelectable(false);
        mPreview = preview.findViewById(R.id.geometry_preview);
        group.addPreference(preview);
        if (shape) {
            slider(group, DisplayGeometrySettings.RADIUS, R.string.arkui_geometry_radius, -1, 100, false);
            mCutout = new SwitchPreferenceCompat(host.requireContext());
            mCutout.setKey("geometry_cutout");
            mCutout.setTitle(R.string.arkui_geometry_cutout);
            mCutout.setPersistent(false);
            mCutout.setOnPreferenceChangeListener((p, value) -> {
                mValues[DisplayGeometrySettings.CUTOUT] = (Boolean) value ? 1 : 0;
                refresh(); return true;
            });
            group.addPreference(mCutout);
            slider(group, DisplayGeometrySettings.X, R.string.arkui_geometry_cutout_x, 0, 100, true);
            slider(group, DisplayGeometrySettings.Y, R.string.arkui_geometry_cutout_y, 0, 100, false);
            slider(group, DisplayGeometrySettings.WIDTH, R.string.arkui_geometry_cutout_width, 4, 100, false);
            slider(group, DisplayGeometrySettings.CUTOUT_HEIGHT, R.string.arkui_geometry_cutout_height, 4, 100, false);
        } else {
            slider(group, DisplayGeometrySettings.LEFT, R.string.arkui_geometry_left, -1, 80, false);
            slider(group, DisplayGeometrySettings.RIGHT, R.string.arkui_geometry_right, -1, 80, false);
            slider(group, DisplayGeometrySettings.TOP, R.string.arkui_geometry_top, -1, 32, false);
            slider(group, DisplayGeometrySettings.HEIGHT, R.string.arkui_geometry_height, -1, 100, false);
        }
        mApply = new Preference(host.requireContext());
        mApply.setKey("geometry_apply");
        mApply.setTitle(R.string.arkui_geometry_apply);
        mApply.setOnPreferenceClickListener(p -> { apply(); return true; });
        group.addPreference(mApply);
        mReset = new Preference(host.requireContext());
        mReset.setKey("geometry_reset");
        mReset.setTitle(R.string.arkui_geometry_reset);
        mReset.setSummary(R.string.arkui_geometry_reset_summary);
        mReset.setOnPreferenceClickListener(p -> {
            int start = mShape ? DisplayGeometrySettings.RADIUS : DisplayGeometrySettings.LEFT;
            int end = mShape ? DisplayGeometrySettings.COUNT : DisplayGeometrySettings.RADIUS;
            for (int i = start; i < end; i++) mValues[i] = DisplayGeometrySettings.DEFAULTS[i];
            refresh(); return true;
        });
        group.addPreference(mReset);
        Preference note = new Preference(host.requireContext());
        note.setSelectable(false);
        note.setSummary(!mOwner ? R.string.arkui_geometry_owner_only
                : shape ? R.string.arkui_geometry_shape_note : R.string.arkui_geometry_status_note);
        group.addPreference(note);
        refresh();
    }

    private void slider(PreferenceCategory group, int index, int title, int min, int max, boolean percent) {
        SliderPreference slider = new SliderPreference(mHost.requireContext());
        slider.setKey("geometry_" + index);
        slider.setTitle(title);
        slider.setPersistent(false);
        // Reserve zero for Automatic, without a dead 0..23 dp range on the height slider.
        slider.setMin(index == DisplayGeometrySettings.HEIGHT ? 0 : min);
        slider.setMax(index == DisplayGeometrySettings.HEIGHT ? 77 : max);
        slider.setSliderIncrement(1);
        slider.setShowSliderValue(true);
        slider.setUpdatesContinuously(true);
        slider.setIconSpaceReserved(false);
        slider.setLabelFormater(value -> format(fromSlider(index, Math.round(value)), percent));
        slider.setSliderContentDescription(mHost.getString(title));
        slider.setOnPreferenceChangeListener((p, value) -> {
            int requested = fromSlider(index, (Integer) value);
            mValues[index] = requested;
            DisplayGeometrySettings.clamp(mValues);
            refresh(); return requested == mValues[index];
        });
        mSliders[index] = slider;
        group.addPreference(slider);
    }

    private String format(int value, boolean percent) {
        return value < 0 ? mHost.getString(R.string.arkui_geometry_default)
                : mHost.getString(percent ? R.string.arkui_geometry_percent : R.string.arkui_geometry_dp, value);
    }

    private static int fromSlider(int index, int value) {
        return index == DisplayGeometrySettings.HEIGHT ? (value == 0 ? -1 : value + 23) : value;
    }

    private boolean hasChanges() {
        int start = mShape ? DisplayGeometrySettings.RADIUS : DisplayGeometrySettings.LEFT;
        int end = mShape ? DisplayGeometrySettings.COUNT : DisplayGeometrySettings.RADIUS;
        for (int i = start; i < end; i++) if (mValues[i] != mApplied[i]) return true;
        return false;
    }

    private void refresh() {
        for (int i = 0; i < mSliders.length; i++) {
            if (mSliders[i] == null) continue;
            mSliders[i].setValue(i == DisplayGeometrySettings.HEIGHT
                    ? (mValues[i] < 0 ? 0 : mValues[i] - 23) : mValues[i]);
            mSliders[i].setSummary(format(mValues[i], i == DisplayGeometrySettings.X));
            mSliders[i].setEnabled(mOwner && !mBusy
                    && (i < DisplayGeometrySettings.X || mValues[DisplayGeometrySettings.CUTOUT] == 1));
        }
        if (mCutout != null) {
            mCutout.setChecked(mValues[DisplayGeometrySettings.CUTOUT] == 1);
            mCutout.setEnabled(mOwner && !mBusy);
        }
        mApply.setEnabled(mOwner && !mBusy && hasChanges());
        mReset.setEnabled(mOwner && !mBusy);
        mPreview.update(mModel, mValues);
    }

    private void apply() {
        if (!mOwner || mBusy) return;
        mBusy = true;
        int[] draft = mValues.clone();
        refresh();
        android.content.Context context = mHost.requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean success;
            try { mModel.apply(draft, mShape); success = true; }
            catch (RuntimeException e) {
                android.util.Log.w("ArkuiGeometry", "Cannot apply display calibration", e);
                success = false;
            }
            boolean applied = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                mBusy = false;
                if (applied) mApplied = draft;
                Toast.makeText(context, applied ? R.string.arkui_geometry_applied
                        : R.string.arkui_personalization_save_error, Toast.LENGTH_SHORT).show();
                if (mHost.isAdded()) refresh();
            });
        });
    }

    void save(Bundle state) {
        state.putString("geometry_display", mModel.uniqueId);
        state.putIntArray("geometry_draft", mValues);
    }
}
