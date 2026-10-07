/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Uses the same per-user overlay transaction as wallpaper colors, preserving other theme choices. */
@SearchIndexable
public final class ArkuiFontsSettings extends SettingsPreferenceFragment {
    private static final String CATEGORY = "android.theme.customization.font";
    private static final String THEME = Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES;
    private final List<FontChoicePreference> mChoices = new ArrayList<>();
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private final BroadcastReceiver mOverlayReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.arkui_fonts_settings);
        final PreferenceCategory group = findPreference("arkui_font_choices");
        addChoice(group, "", "Google Sans Flex", "google-sans-flex");
        final OverlayManager manager = requireContext().getSystemService(OverlayManager.class);
        if (manager != null) for (OverlayInfo info : manager.getOverlayInfosForTarget(
                "android", UserHandle.of(requireContext().getUserId()))) {
            if (!CATEGORY.equals(info.category) || !info.isMutable) continue;
            try {
                final var pm = requireContext().getPackageManager();
                final var resources = pm.getResourcesForApplication(info.packageName);
                final int id = resources.getIdentifier("config_bodyFontFamily", "string", info.packageName);
                if (id == 0) continue;
                final String family = resources.getString(id);
                if (!Typeface.getSystemFontMap().containsKey(family)) continue;
                addChoice(group, info.packageName,
                        pm.getApplicationInfo(info.packageName, 0).loadLabel(pm), family);
            } catch (android.content.pm.PackageManager.NameNotFoundException
                    | android.content.res.Resources.NotFoundException ignored) {
                // An overlay removed while entering this page is not a selectable font.
            }
        }
    }

    private void addChoice(PreferenceCategory group, String packageName,
            CharSequence label, String family) {
        final FontChoicePreference row = new FontChoicePreference(requireContext(), packageName, family);
        row.setKey("font_" + (packageName.isEmpty() ? "default" : packageName));
        row.setTitle(label);
        row.setSummary(R.string.arkui_font_sample);
        row.setPersistent(false);
        row.setOnClickListener(preference -> select(packageName));
        mChoices.add(row);
        group.addPreference(row);
    }

    private void select(String packageName) {
        try {
            final String raw = Settings.Secure.getString(requireContext().getContentResolver(), THEME);
            final JSONObject theme = raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            if (packageName.isEmpty()) theme.remove(CATEGORY);
            else theme.put(CATEGORY, packageName);
            theme.put("_applied_timestamp", System.currentTimeMillis());
            if (!Settings.Secure.putStringForUser(requireContext().getContentResolver(), THEME,
                    theme.toString(), requireContext().getUserId())) {
                throw new IllegalStateException("Could not save font selection");
            }
            refresh();
        } catch (JSONException | IllegalStateException e) {
            Toast.makeText(requireContext(), R.string.arkui_personalization_save_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onResume() {
        super.onResume();
        requireContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(THEME), false, mObserver);
        final IntentFilter filter = new IntentFilter(Intent.ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");
        requireContext().registerReceiver(mOverlayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        refresh();
    }
    @Override public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        requireContext().unregisterReceiver(mOverlayReceiver);
        super.onPause();
    }
    private void refresh() {
        if (!isAdded()) return;
        String selected = "";
        try {
            final String raw = Settings.Secure.getString(requireContext().getContentResolver(), THEME);
            if (raw != null && !raw.isEmpty()) selected = new JSONObject(raw).optString(CATEGORY, "");
        } catch (JSONException ignored) {}
        for (FontChoicePreference row : mChoices) row.setChecked(row.packageName.equals(selected));
    }

    private static final class FontChoicePreference extends SelectorWithWidgetPreference {
        final String packageName;
        final Typeface sampleTypeface;
        FontChoicePreference(Context context, String packageName, String family) {
            super(context);
            this.packageName = packageName;
            sampleTypeface = Typeface.create(family, Typeface.NORMAL);
        }
        @Override public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            final TextView sample = (TextView) holder.findViewById(android.R.id.summary);
            if (sample != null) {
                sample.setTypeface(sampleTypeface);
                sample.setTextSize(18);
                sample.setMaxLines(3);
            }
        }
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_fonts_settings);
}
