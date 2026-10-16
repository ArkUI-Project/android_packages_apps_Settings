/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

/** Two softly breathing lights orbit while Android waits for the user's home app. */
public final class StartupAnimationView extends View implements Choreographer.FrameCallback {
    private static final int ORBIT_DURATION_MILLIS = 3200;
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDotRadius;
    private final float mOrbitRadius;
    private final AccessibilityManager mAccessibility;
    private final ValueAnimator.DurationScaleChangeListener mDurationListener =
            scale -> updateAnimationState();
    private final AccessibilityManager.TouchExplorationStateChangeListener mExplorationListener =
            enabled -> updateAnimationState();
    private boolean mAttached, mVisible, mRunning;
    private boolean mActive = true;
    private long mLastFrameNanos;
    private double mElapsedMillis;
    private double mEntranceMillis;

    public StartupAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final float density = getResources().getDisplayMetrics().density;
        mDotRadius = 15f * density;
        mOrbitRadius = 33f * density;
        mDotPaint.setColor(Color.WHITE);
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

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateAnimationState();
    }

    private boolean motionEnabled() {
        return ValueAnimator.areAnimatorsEnabled()
                && (mAccessibility == null || !mAccessibility.isTouchExplorationEnabled());
    }

    private void updateAnimationState() {
        Choreographer.getInstance().removeFrameCallback(this);
        mLastFrameNanos = 0;
        mRunning = mAttached && mVisible && mActive && hasWindowFocus() && motionEnabled();
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
            double delta = (frameTimeNanos - mLastFrameNanos) / 1000000.0
                    / Math.max(.01f, ValueAnimator.getDurationScale());
            mElapsedMillis = (mElapsedMillis + delta) % ORBIT_DURATION_MILLIS;
            mEntranceMillis = Math.min(400, mEntranceMillis + delta);
        }
        mLastFrameNanos = frameTimeNanos;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        final double angle = motionEnabled()
                ? mElapsedMillis * Math.PI * 2 / ORBIT_DURATION_MILLIS : 0;
        final float progress = motionEnabled() ? (float) (mEntranceMillis / 400) : 1;
        final float entrance = 1 - (float) Math.pow(1 - progress, 3);
        final float breath = motionEnabled() ? (float) Math.sin(angle * 2) : 0;
        final float radius = mOrbitRadius * (1 + .12f * breath);
        final float dx = (float) Math.cos(angle) * radius;
        final float dy = (float) Math.sin(angle) * radius;
        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;
        final float size = mDotRadius * (.85f + .15f * entrance);
        final float opacity = .4f + .6f * entrance;
        mDotPaint.setAlpha(Math.round((235 + 20 * breath) * opacity));
        canvas.drawCircle(cx + dx, cy + dy, size * (1 + .08f * breath), mDotPaint);
        mDotPaint.setAlpha(Math.round((235 - 20 * breath) * opacity));
        canvas.drawCircle(cx - dx, cy - dy, size * (1 - .08f * breath), mDotPaint);
    }
}
