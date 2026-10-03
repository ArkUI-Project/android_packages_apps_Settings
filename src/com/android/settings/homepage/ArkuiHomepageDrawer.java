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

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.android.settings.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/** Keeps the overview fixed behind a draggable, independently scrolling settings drawer. */
final class ArkuiHomepageDrawer {
    private final Rect mOverviewClip = new Rect();
    private final View mContainer;
    private final View mAppBar;
    private final View mOverview;
    private final View mDrawer;
    private final View mScrollContent;
    private final NestedScrollView mScrollView;
    private final BottomSheetBehavior<View> mBehavior;
    private final OnBackPressedCallback mBackCallback;
    private boolean mGeometryUpdatePending;

    ArkuiHomepageDrawer(SettingsHomepageActivity activity) {
        mContainer = activity.findViewById(R.id.settings_homepage_container);
        mAppBar = activity.findViewById(R.id.app_bar);
        mOverview = activity.findViewById(R.id.arkui_home_overview);
        mDrawer = activity.findViewById(R.id.arkui_home_drawer);
        mScrollContent = activity.findViewById(R.id.homepage_container);
        mScrollView = activity.findViewById(R.id.main_content_scrollable_container);
        mBehavior = BottomSheetBehavior.from(mDrawer);
        mBehavior.setShouldRemoveExpandedCorners(false);

        ViewCompat.setAccessibilityPaneTitle(mDrawer, activity.getString(R.string.settings_label));
        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                mScrollView.scrollTo(0, 0);
                mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, mBackCallback);
        mBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateState(newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateOverviewClip();
            }
        });

        final View.OnLayoutChangeListener listener =
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        requestGeometryUpdate();
        mContainer.addOnLayoutChangeListener(listener);
        mAppBar.addOnLayoutChangeListener(listener);
        mOverview.addOnLayoutChangeListener(listener);
        mScrollContent.addOnLayoutChangeListener(listener);
        // Run after layout so the clip also follows restored states and geometry changes.
        mContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::updateOverviewClip);
        requestGeometryUpdate();
    }

    private void updateState(int state) {
        mBackCallback.setEnabled(state != BottomSheetBehavior.STATE_COLLAPSED);
        mOverview.setImportantForAccessibility(state == BottomSheetBehavior.STATE_EXPANDED
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void updateOverviewClip() {
        // Keep covered cards out of the transparent handle area as the drawer moves over them.
        final int visibleHeight = Math.max(0,
                Math.min(mOverview.getHeight(), mDrawer.getTop() - mOverview.getTop()));
        mOverviewClip.set(0, 0, mOverview.getWidth(), visibleHeight);
        mOverview.setClipBounds(mOverviewClip);
    }

    private void requestGeometryUpdate() {
        if (mGeometryUpdatePending) {
            return;
        }
        mGeometryUpdatePending = true;
        mContainer.post(() -> {
            mGeometryUpdatePending = false;
            updateGeometry();
        });
    }

    private void updateGeometry() {
        final int parentHeight = mContainer.getHeight();
        if (parentHeight == 0 || mAppBar.getHeight() == 0 || mOverview.getHeight() == 0) {
            return;
        }
        final int spacing = mContainer.getResources().getDimensionPixelSize(
                R.dimen.arkui_home_drawer_top_spacing);
        // Fit the expanded drawer below the fixed toolbar, leaving search reachable.
        final int expandedTop = Math.min(parentHeight - 1, mAppBar.getBottom() + spacing);
        final int expandedHeight = parentHeight - expandedTop;
        final ViewGroup.LayoutParams params = mDrawer.getLayoutParams();
        if (params.height != expandedHeight) {
            params.height = expandedHeight;
            mDrawer.setLayoutParams(params);
        }

        // Collapse directly below the measured cards, including wrapped text and font scaling.
        final int minimumPeek = Math.min(expandedHeight,
                mContainer.getResources().getDimensionPixelSize(
                        R.dimen.arkui_home_drawer_min_peek_height)
                        + mScrollContent.getPaddingBottom());
        final int collapsedTop = Math.max(expandedTop,
                Math.min(mOverview.getBottom(), parentHeight - minimumPeek));
        mBehavior.setPeekHeight(parentHeight - collapsedTop);
        // View-state restoration can change the behavior without sending a state callback.
        updateState(mBehavior.getState());
    }
}
