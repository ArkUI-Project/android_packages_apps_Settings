/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;

import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.arkui.DepthWallpaperContract.Option;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.SliderPreference;

/** Spacious expressive controls with visual/behavior tuning on a separate page. */
@SearchIndexable
public final class ArkuiDepthWallpaperSettings extends SettingsPreferenceFragment {
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = this::refresh;
    private final ContentObserver mObserver = new ContentObserver(mMain) {
        @Override public void onChange(boolean selfChange) {
            mMain.removeCallbacks(mRefresh);
            mMain.postDelayed(mRefresh, 80);
        }
    };
    private boolean mUpdating;
    private boolean mResumed;
    private int mPreviewGeneration;

    @Override public int getMetricsCategory() { return SettingsEnums.WALLPAPER_TYPE; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final boolean effects = getArguments() != null && getArguments().getBoolean("effects");
        addPreferencesFromResource(effects ? R.xml.arkui_depth_wallpaper_effects
                : R.xml.arkui_depth_wallpaper);
        for (Option option : OPTIONS) {
            final Preference row = findPreference(option.key());
            if (row == null) continue;
            row.setPersistent(false);
            if (row instanceof SettingsMainSwitchPreference main) {
                main.addOnSwitchChangeListener((button, checked) -> {
                    if (!mUpdating) save(option, checked ? 1 : 0);
                });
            } else {
                row.setOnPreferenceChangeListener((preference, value) -> {
                    if (mUpdating) return true;
                    return save(option, value instanceof Boolean ? ((Boolean) value ? 1 : 0)
                            : value instanceof String ? Integer.parseInt((String) value)
                            : (Integer) value);
                });
            }
            if (row instanceof SliderPreference slider) {
                slider.setMin(option.min());
                slider.setMax(option.max());
                slider.setSliderIncrement(option == DURATION ? 50 : 1);
                slider.setShowSliderValue(true);
                slider.setUpdatesContinuously(false);
                slider.setSliderContentDescription(row.getTitle());
                slider.setLabelFormater(value -> format(option, Math.round(value)));
            }
        }
        final Preference edit = findPreference("depth_edit");
        if (edit != null) edit.setOnPreferenceClickListener(p -> { openEditor(); return true; });
        final Preference preview = findPreference("depth_preview");
        if (preview != null) preview.setOnPreferenceClickListener(p -> { openEditor(); return true; });
        final Preference generate = findPreference("depth_generate");
        if (generate != null) generate.setOnPreferenceClickListener(p -> {
            DepthWallpaperProcessor.request(requireContext(), true);
            refresh();
            return true;
        });
        final Preference tuning = findPreference("depth_effects");
        if (tuning != null) tuning.setOnPreferenceClickListener(p -> {
            final Bundle args = new Bundle();
            args.putBoolean("effects", true);
            new SubSettingLauncher(requireContext()).setDestination(getClass().getName())
                    .setArguments(args).setTitleRes(R.string.arkui_depth_effects)
                    .setSourceMetricsCategory(getMetricsCategory()).launch();
            return true;
        });
        final Preference reset = findPreference("depth_reset");
        if (reset != null) reset.setOnPreferenceClickListener(p -> {
            DepthWallpaperProcessor.cancel();
            for (Option option : OPTIONS) option.write(requireContext().getContentResolver(),
                    requireContext().getUserId(), option.defaultValue());
            refresh();
            return true;
        });
    }
    private void openEditor() {
        startActivity(new Intent(requireContext(), DepthWallpaperEditorActivity.class));
    }
    private boolean save(Option option, int value) {
        final Context context = requireContext();
        if (!option.write(context.getContentResolver(), context.getUserId(), value)) return false;
        if (option == ENABLED && value == 0) DepthWallpaperProcessor.cancel();
        else if (option == ENABLED || option == AUTO_REFRESH || option == SUBJECT
                || option == CONFIDENCE) {
            DepthWallpaperProcessor.request(context, option == SUBJECT || option == CONFIDENCE);
        }
        mMain.removeCallbacks(mRefresh);
        mMain.post(mRefresh);
        return true;
    }
    @Override public void onResume() {
        super.onResume();
        if (!available(requireContext())) { finish(); return; }
        mResumed = true;
        final var resolver = requireContext().getContentResolver();
        for (Option option : OPTIONS) resolver.registerContentObserver(
                android.provider.Settings.Secure.getUriFor(option.key()), false, mObserver);
        resolver.registerContentObserver(URI, true, mObserver);
        refresh();
    }
    @Override public void onPause() {
        mResumed = false;
        ++mPreviewGeneration;
        mMain.removeCallbacks(mRefresh);
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }
    private void refresh() {
        if (!isAdded() || !mResumed) return;
        final Context context = requireContext();
        final Bundle status = DepthWallpaperStore.status(context);
        mUpdating = true;
        for (Option option : OPTIONS) {
            final Preference row = findPreference(option.key());
            if (row == null) continue;
            final int value = option.read(context.getContentResolver(), context.getUserId());
            if (row instanceof TwoStatePreference toggle) toggle.setChecked(value != 0);
            else if (row instanceof androidx.preference.ListPreference list) {
                list.setValue(Integer.toString(value));
                list.setSummary(list.getEntry());
            } else if (row instanceof SliderPreference slider) {
                slider.setValue(value);
                slider.setSummary(format(option, value));
            }
            if (option == DURATION) row.setEnabled(ANIMATE.read(context.getContentResolver(),
                    context.getUserId()) != 0);
            if (option == AOD_OPACITY) row.setEnabled(AOD.read(context.getContentResolver(),
                    context.getUserId()) != 0);
        }
        mUpdating = false;
        final Preference generate = findPreference("depth_generate");
        if (generate != null) {
            generate.setEnabled(!"processing".equals(status.getString("state"))
                    && !"unsupported".equals(status.getString("state")));
            generate.setSummary(statusText(status));
        }
        final DepthWallpaperPreviewPreference preview = findPreference("depth_preview");
        if (preview != null) {
            final int generation = ++mPreviewGeneration;
            ThreadUtils.postOnBackgroundThread(() -> {
                android.graphics.Bitmap background = null;
                try { background = DepthWallpaperProcessor.readWallpaper(context); }
                catch (Exception ignored) { }
                final var photo = background;
                final var mask = DepthWallpaperStore.readMask(context);
                mMain.post(() -> {
                    if (mResumed && generation == mPreviewGeneration) {
                        preview.update(photo, mask, statusText(status));
                    }
                });
            });
        }
    }
    private String format(Option option, int value) {
        return getString(option == FEATHER ? R.string.arkui_depth_value_dp
                : option == DURATION ? R.string.arkui_depth_value_ms
                : R.string.arkui_depth_value_percent, value);
    }
    private CharSequence statusText(Bundle status) {
        return getString(switch (status.getString("state", "empty")) {
            case "ready" -> R.string.arkui_depth_ready;
            case "processing" -> R.string.arkui_depth_processing;
            case "no_subject" -> R.string.arkui_depth_no_subject;
            case "unsupported" -> R.string.arkui_depth_live;
            case "error" -> R.string.arkui_depth_error;
            default -> R.string.arkui_depth_empty;
        });
    }
    private static boolean available(Context context) {
        final UserManager users = context.getSystemService(UserManager.class);
        return !users.isManagedProfile() && !users.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_depth_wallpaper) {
                @Override protected boolean isPageSearchEnabled(Context context) { return available(context); }
            };
}
