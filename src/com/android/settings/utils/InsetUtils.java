/*
 * Copyright (C) 2024 The LineageOS Project
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
 * limitations under the License
 */

package com.android.settings.utils;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.settings.R;

public class InsetUtils {
    public static void applyWindowInsetsListener(final View rootView) {
        final int bottomSpacing = rootView.getResources().getDimensionPixelSize(
                R.dimen.settings_bottom_safe_spacing);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
            final int bottomInset = Math.max(insets.bottom, windowInsets.getInsets(
                    WindowInsetsCompat.Type.mandatorySystemGestures()).bottom);

            view.setPadding(insets.left, insets.top, insets.right, bottomInset + bottomSpacing);

            return WindowInsetsCompat.CONSUMED;
        });
    }
}
