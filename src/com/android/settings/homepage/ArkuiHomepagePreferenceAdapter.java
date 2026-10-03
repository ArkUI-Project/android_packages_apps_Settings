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

package com.android.settings.homepage;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceGroup;

import com.android.settings.R;
import com.android.settings.core.RoundCornerPreferenceAdapter;

/** Homepage-only row colors, preserving the upstream grouping and selection mapping. */
public class ArkuiHomepagePreferenceAdapter extends RoundCornerPreferenceAdapter {
    public ArkuiHomepagePreferenceAdapter(@NonNull PreferenceGroup group) {
        super(group);
    }

    @Override
    protected int getRoundCornerDrawableRes(int position, boolean isSelected) {
        final int background = super.getRoundCornerDrawableRes(position, isSelected);
        if (isSelected) {
            return background;
        }
        if (background == com.android.settingslib.widget.theme.R.drawable
                .settingslib_round_background_top) {
            return R.drawable.arkui_home_row_top;
        }
        if (background == com.android.settingslib.widget.theme.R.drawable
                .settingslib_round_background_bottom) {
            return R.drawable.arkui_home_row_bottom;
        }
        if (background == com.android.settingslib.widget.theme.R.drawable
                .settingslib_round_background_center) {
            return R.drawable.arkui_home_row_center;
        }
        if (background == com.android.settingslib.widget.theme.R.drawable
                .settingslib_round_background) {
            return R.drawable.arkui_home_row_single;
        }
        return background;
    }
}
