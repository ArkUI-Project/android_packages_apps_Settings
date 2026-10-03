/*
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

package com.android.settings.deviceinfo.aboutphone;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.NormalPaddingMixin;

/** System overview that delegates navigation to the existing device-info preferences. */
public class ArkuiAboutOverviewPreference extends LayoutPreference implements NormalPaddingMixin {
    private final LinearLayout mCards;

    public ArkuiAboutOverviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
        final String baseVersion = SystemProperties.get("ro.lineage.build.version",
                context.getString(R.string.unknown));
        ((TextView) findViewById(R.id.arkui_about_base_version)).setText(context.getString(
                R.string.arkui_about_lineage_base, baseVersion));
        ((TextView) findViewById(R.id.arkui_about_android_version)).setText(Build.VERSION.RELEASE);
        ((TextView) findViewById(R.id.arkui_about_model)).setText(Build.MODEL);

        mCards = findViewById(R.id.arkui_about_cards);
        // Use the actual pane width, including in split-screen and activity embedding.
        mCards.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateCardLayout(right - left));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        bindCard(R.id.arkui_about_android_card, "firmware_version");
        bindCard(R.id.arkui_about_model_card, "device_model");
    }

    private void bindCard(int viewId, String key) {
        final View card = findViewById(viewId);
        final Preference preference = getPreferenceManager().findPreference(key);
        // Respect device overlays and the availability supplied by either controller system.
        card.setVisibility(preference != null && preference.isVisible() ? View.VISIBLE : View.GONE);
        card.setEnabled(preference != null && preference.isEnabled());
        card.setOnClickListener(view -> {
            final Preference destination = getPreferenceManager().findPreference(key);
            if (destination != null && destination.isVisible()) {
                destination.performClick();
            }
        });
    }

    private void updateCardLayout(int width) {
        if (width <= 0) {
            return;
        }
        final float density = getContext().getResources().getDisplayMetrics().density;
        final boolean stacked = width / density < 340
                || getContext().getResources().getConfiguration().fontScale >= 1.3f;
        final int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        mCards.setOrientation(orientation);
        final int gap = Math.round(12 * density);
        final boolean bothVisible = mCards.getChildAt(0).getVisibility() == View.VISIBLE
                && mCards.getChildAt(1).getVisibility() == View.VISIBLE;
        for (int i = 0; i < mCards.getChildCount(); i++) {
            final View card = mCards.getChildAt(i);
            final LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) card.getLayoutParams();
            final int cardWidth = stacked ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
            final int cardHeight = stacked ? ViewGroup.LayoutParams.WRAP_CONTENT
                    : ViewGroup.LayoutParams.MATCH_PARENT;
            final float weight = stacked ? 0 : 1;
            final int start = !stacked && bothVisible && i == 1 ? gap / 2 : 0;
            final int end = !stacked && bothVisible && i == 0 ? gap / 2 : 0;
            final int top = stacked && bothVisible && i == 1 ? gap : 0;
            if (params.width != cardWidth || params.height != cardHeight || params.weight != weight
                    || params.getMarginStart() != start || params.getMarginEnd() != end
                    || params.topMargin != top) {
                params.width = cardWidth;
                params.height = cardHeight;
                params.weight = weight;
                params.setMarginStart(start);
                params.setMarginEnd(end);
                params.topMargin = top;
                card.setLayoutParams(params);
            }
        }
    }
}
