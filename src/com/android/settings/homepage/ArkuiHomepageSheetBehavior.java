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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

/** Shares both drag distance and fling momentum between the drawer and its scrolling content. */
public final class ArkuiHomepageSheetBehavior extends BottomSheetBehavior<View> {
    private final int[] mSheetConsumed = new int[2];
    private boolean mNonTouchScrollInProgress;

    public ArkuiHomepageSheetBehavior(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout parent, @NonNull View child,
            @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
        final boolean accepted = super.onStartNestedScroll(
                parent, child, directTargetChild, target, axes, type);
        if (accepted && type == ViewCompat.TYPE_NON_TOUCH) {
            mNonTouchScrollInProgress = true;
        }
        return accepted;
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout parent, @NonNull View child,
            @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        // Material normally ignores non-touch scrolling. Apply the same distance rules during
        // a fling: expand first, then let the scroll view use the remainder of each frame.
        super.onNestedPreScroll(parent, child, target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout parent, @NonNull View child,
            @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, int type, @NonNull int[] consumed) {
        if (dyUnconsumed == 0) {
            return;
        }
        // A downward frame may reach the top of the list part way through. Give the remaining
        // distance to the drawer immediately so the same drag or fling continues to collapse it.
        mSheetConsumed[0] = 0;
        mSheetConsumed[1] = 0;
        super.onNestedPreScroll(parent, child, target, dxUnconsumed, dyUnconsumed,
                mSheetConsumed, ViewCompat.TYPE_TOUCH);
        consumed[0] += mSheetConsumed[0];
        consumed[1] += mSheetConsumed[1];
    }

    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout parent, @NonNull View child,
            @NonNull View target, float velocityX, float velocityY) {
        // Let the scroll view run one fling across the sheet and list instead of consuming
        // the velocity just because the sheet has not yet reached its expanded position.
        return false;
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout parent, @NonNull View child,
            @NonNull View target, int type) {
        if (type == ViewCompat.TYPE_TOUCH && mNonTouchScrollInProgress) {
            // ACTION_UP starts the fling before ending the touch scroll. Settle only after the
            // fling ends; an independent settling animation would compete with its movement.
            return;
        }
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            mNonTouchScrollInProgress = false;
        }
        super.onStopNestedScroll(parent, child, target, type);
    }
}
