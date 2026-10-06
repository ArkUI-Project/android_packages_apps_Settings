/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import org.arkui.animation.StartupAnimation;

/** Runs the setup opening scene while Android is waiting for the user's home app. */
public final class StartupAnimationView extends View implements Choreographer.FrameCallback {
    private final StartupAnimation mAnimation;
    private final AccessibilityManager mAccessibility;
    private final ValueAnimator.DurationScaleChangeListener mDurationListener =
            scale -> updateAnimationState();
    private final AccessibilityManager.TouchExplorationStateChangeListener mExplorationListener =
            enabled -> updateAnimationState();
    private boolean mAttached, mVisible, mRunning;
    private boolean mActive = true;
    private long mLastFrameNanos;
    private double mElapsedMillis;

    public StartupAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAnimation = new StartupAnimation(getResources().getDisplayMetrics().density,
                context.getColor(com.android.internal.R.color.materialColorPrimary));
        mAccessibility = context.getSystemService(AccessibilityManager.class);
    }

    public void setRunning(boolean active) {
        mActive = active;
        updateAnimationState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        mVisible = isShown();
        ValueAnimator.registerDurationScaleChangeListener(mDurationListener);
        if (mAccessibility != null) {
            mAccessibility.addTouchExplorationStateChangeListener(mExplorationListener);
        }
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        updateAnimationState();
        ValueAnimator.unregisterDurationScaleChangeListener(mDurationListener);
        if (mAccessibility != null) {
            mAccessibility.removeTouchExplorationStateChangeListener(mExplorationListener);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        mVisible = visible;
        updateAnimationState();
    }

    private boolean motionEnabled() {
        return ValueAnimator.areAnimatorsEnabled()
                && (mAccessibility == null || !mAccessibility.isTouchExplorationEnabled());
    }

    private void updateAnimationState() {
        Choreographer.getInstance().removeFrameCallback(this);
        mLastFrameNanos = 0;
        mRunning = mAttached && mVisible && mActive && motionEnabled();
        if (mRunning) Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mRunning) return;
        if (!motionEnabled()) {
            updateAnimationState();
            return;
        }
        if (mLastFrameNanos != 0) {
            mElapsedMillis = (mElapsedMillis + (frameTimeNanos - mLastFrameNanos) / 1000000.0
                    / Math.max(.01f, ValueAnimator.getDurationScale()))
                    % StartupAnimation.DURATION_MILLIS;
        }
        mLastFrameNanos = frameTimeNanos;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        mAnimation.draw(canvas, getWidth(), getHeight(),
                motionEnabled() ? (long) mElapsedMillis : 3850);
    }
}
