/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Icon controls with immediate local previews and ordered, atomic Launcher saves. */
@SearchIndexable
public class ArkuiDesktopIconSettings extends DashboardFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "ArkuiDesktopIcons";
    private static final Uri URI =
            Uri.parse("content://com.android.launcher3.desktop_settings");
    private static final String SIZE = "pref_desktop_icon_size";
    private static final String SHAPE = "icon_shape_model";
    private static final String LABELS = "pref_desktop_show_labels";
    // Shared across fragment recreations so an old save cannot overtake a new read or save.
    private static final Executor IO = Executors.newSingleThreadExecutor();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSave = this::savePending;
    private ArkuiMotionPreference mSize;
    private ListPreference mShape;
    private SwitchPreferenceCompat mLabels;
    private ArkuiIconPreviewPreference mPreview;
    private ContentResolver mResolver;
    private String[] mShapePaths;
    private String mShapeValue;
    private String mDefaultShape;
    private int mSizeValue;
    private int mDefaultSize;
    private int mGeneration;
    private float mBaseSizeDp;
    private boolean mShowLabels;
    private boolean mLoaded;
    private boolean mPendingSave;
    private boolean mTracking;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arkui_desktop_icon_settings;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mResolver = requireContext().getContentResolver();
        mSize = findPreference(SIZE);
        mShape = findPreference(SHAPE);
        mLabels = findPreference(LABELS);
        mPreview = findPreference("icon_preview");
        mSize.setUnit("%");
        mSize.setUpdatesContinuously(true);
        mSize.setExtraTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                mTracking = true;
                mHandler.removeCallbacks(mSave);
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                mTracking = false;
                savePending();
            }
        });
        mShape.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mSize.setOnPreferenceChangeListener(this);
        mShape.setOnPreferenceChangeListener(this);
        mLabels.setOnPreferenceChangeListener(this);
        findPreference("icon_status").setOnPreferenceClickListener(preference -> {
            loadPreferences();
            return true;
        });
        findPreference("icon_reset").setOnPreferenceClickListener(preference -> {
            mSizeValue = mDefaultSize;
            mShapeValue = mDefaultShape;
            mShowLabels = true;
            mSize.setValue(mSizeValue);
            mShape.setValue(mShapeValue);
            mLabels.setChecked(true);
            changed();
            savePending();
            return true;
        });
        setControlsEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!ArkuiDesktopSettings.isAvailable(requireContext())) {
            finish();
            return;
        }
        loadPreferences();
    }

    @Override
    public void onPause() {
        mTracking = false;
        // Leaving while dragging still persists the last value shown in the preview.
        savePending();
        ++mGeneration;
        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (!mLoaded) return false;
        if (SIZE.equals(preference.getKey()) && value instanceof Integer size) {
            mSizeValue = size;
        } else if (SHAPE.equals(preference.getKey()) && value instanceof String shape
                && mShape.findIndexOfValue(shape) >= 0) {
            mShapeValue = shape;
        } else if (LABELS.equals(preference.getKey()) && value instanceof Boolean labels) {
            mShowLabels = labels;
        } else {
            return false;
        }
        changed();
        return true;
    }

    private void changed() {
        ++mGeneration;
        mPendingSave = true;
        updatePreview();
        mHandler.removeCallbacks(mSave);
        // Dragging only redraws the preview. Keyboard and accessibility changes are coalesced.
        if (!mTracking) mHandler.postDelayed(mSave, 200);
    }

    private void updatePreview() {
        int index = mShape.findIndexOfValue(mShapeValue);
        mPreview.setConfiguration(mSizeValue, mBaseSizeDp,
                mShapePaths[Math.max(0, index)], mShowLabels);
        mSize.setSliderStateDescription(mSizeValue + "%");
    }

    private void setControlsEnabled(boolean enabled) {
        findPreference("icon_controls").setEnabled(enabled);
        findPreference("icon_reset").setEnabled(enabled);
    }

    private void loadPreferences() {
        final int generation = ++mGeneration;
        final Context context = requireContext().getApplicationContext();
        final Preference status = findPreference("icon_status");
        status.setTitle(R.string.arkui_desktop_loading);
        status.setEnabled(false);
        status.setVisible(!mLoaded);
        IO.execute(() -> {
            Bundle response = null;
            try {
                response = mResolver.call(URI, "get_preferences", null, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to read icon settings", e);
            }
            final Bundle result = response;
            final List<ArkuiIconPreviewPreference.AppIcon> icons =
                    ArkuiIconPreviewPreference.loadIcons(context);
            mHandler.post(() -> {
                if (!isAdded() || generation != mGeneration) return;
                if (!bindPreferences(result)) {
                    mLoaded = false;
                    setControlsEnabled(false);
                    status.setTitle(R.string.arkui_desktop_unavailable);
                    status.setEnabled(true);
                    status.setVisible(true);
                    return;
                }
                mPreview.setIcons(icons);
                updatePreview();
                mLoaded = true;
                status.setVisible(false);
                setControlsEnabled(true);
            });
        });
    }

    private boolean bindPreferences(Bundle result) {
        if (result == null) return false;
        Bundle values = result.getBundle("values");
        Bundle shapes = result.getBundle("icon_shapes");
        Bundle size = result.getBundle("icon_size");
        if (values == null || shapes == null || size == null) return false;
        String[] keys = shapes.getStringArray("values");
        String[] labels = shapes.getStringArray("labels");
        String[] paths = shapes.getStringArray("paths");
        if (keys == null || labels == null || paths == null || keys.length == 0
                || keys.length != labels.length || keys.length != paths.length) return false;
        mShapePaths = paths;
        mShape.setEntries(labels);
        mShape.setEntryValues(keys);
        mDefaultShape = shapes.getString("default", keys[0]);
        if (mShape.findIndexOfValue(mDefaultShape) < 0) mDefaultShape = keys[0];
        mShapeValue = values.getString(SHAPE, mDefaultShape);
        if (mShape.findIndexOfValue(mShapeValue) < 0) mShapeValue = mDefaultShape;
        mShape.setValue(mShapeValue);
        mShape.setVisible(keys.length > 1);
        mDefaultSize = size.getInt("default", 115);
        mSizeValue = values.getInt(SIZE, mDefaultSize);
        int min = size.getInt("min", 80);
        int max = size.getInt("max", 120);
        int step = Math.max(1, size.getInt("step", 5));
        mSize.setMax(max);
        mSize.setMin(min);
        mSize.setSliderIncrement((mSizeValue - min) % step == 0 ? step : 1);
        mSize.setValue(mSizeValue);
        mBaseSizeDp = size.getFloat("base_size_dp", 56f);
        mShowLabels = values.getBoolean(LABELS, true);
        mLabels.setChecked(mShowLabels);
        return true;
    }

    private void savePending() {
        mHandler.removeCallbacks(mSave);
        if (!mPendingSave || !mLoaded) return;
        mPendingSave = false;
        final int generation = mGeneration;
        final Bundle values = new Bundle();
        values.putInt(SIZE, mSizeValue);
        values.putString(SHAPE, mShapeValue);
        values.putBoolean(LABELS, mShowLabels);
        IO.execute(() -> {
            Bundle response = null;
            try {
                response = mResolver.call(URI, "set_icon_preferences", null, values);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to save icon settings", e);
            }
            if (response != null) return;
            mHandler.post(() -> {
                if (!isAdded() || generation != mGeneration) return;
                Toast.makeText(requireContext(), R.string.arkui_desktop_save_error,
                        Toast.LENGTH_SHORT).show();
                loadPreferences();
            });
        });
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_desktop_icon_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return ArkuiDesktopSettings.isAvailable(context);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    keys.add("icon_status");
                    keys.add("icon_preview");
                    keys.add("icon_reset");
                    return keys;
                }
            };
}
