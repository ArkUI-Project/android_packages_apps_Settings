/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;
import android.util.AttributeSet;

import com.android.settingslib.widget.GroupSectionDividerMixin;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.NormalPaddingMixin;

/** A page introduction that stays outside the rounded preference groups. */
public final class ArkuiSecurityOverviewPreference extends LayoutPreference
        implements NormalPaddingMixin, GroupSectionDividerMixin {
    public ArkuiSecurityOverviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
        setPersistent(false);
    }
}
