/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.applications.manageapplications;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.widget.DrawableStateLayout;
import com.android.settingslib.widget.SettingsThemeHelper;
import com.android.settingslib.widget.theme.R;

/** Expressive presentation for the storage list without changing its filters or sorting. */
final class StorageAppListStyle {
    private static final int[] SINGLE = {android.R.attr.state_single, R.attr.state_has_icon_space};
    private static final int[] FIRST = {android.R.attr.state_first, R.attr.state_has_icon_space};
    private static final int[] MIDDLE = {android.R.attr.state_middle, R.attr.state_has_icon_space};
    private static final int[] LAST = {android.R.attr.state_last, R.attr.state_has_icon_space};

    private StorageAppListStyle() { }

    static boolean isEnabled(Context context, int listType) {
        return listType == ManageApplications.LIST_TYPE_STORAGE
                && SettingsThemeHelper.isExpressiveTheme(context);
    }

    static void applyListStyle(RecyclerView list) {
        final Resources resources = list.getResources();
        final int top = resources.getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_extrasmall4);
        final int bottom = resources.getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_small1);
        final int gap = resources.getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_extrasmall1);
        list.setPaddingRelative(list.getPaddingStart(), top, list.getPaddingEnd(), bottom);
        list.setClipToPadding(false);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                    @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                final int position = parent.getChildAdapterPosition(view);
                outRect.set(0, 0, 0, position != RecyclerView.NO_POSITION
                        && position < state.getItemCount() - 1 ? gap : 0);
            }
        });
    }

    static void bindItem(View view, int position, int count) {
        if (!(view instanceof DrawableStateLayout layout)) return;
        // Re-evaluate on every bind, including after searching, sorting or changing filters.
        layout.setExtraDrawableState(count == 1 ? SINGLE
                : position == 0 ? FIRST : position == count - 1 ? LAST : MIDDLE);
    }
}
