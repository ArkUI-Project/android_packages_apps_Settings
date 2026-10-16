/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.arkui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.PathParser;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draws installed application icons with the exact mask supplied by Launcher. */
public class ArkuiIconPreviewPreference extends Preference {
    static final class AppIcon {
        final CharSequence label;
        final Drawable drawable;

        AppIcon(CharSequence label, Drawable drawable) {
            this.label = label;
            this.drawable = drawable;
        }
    }

    private List<AppIcon> mIcons = Collections.emptyList();
    private Path mMask = new Path();
    private int mSizePercent = 115;
    private float mBaseSizeDp = 56f;
    private boolean mShowLabels = true;
    private IconPreviewView mView;

    public ArkuiIconPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.arkui_icon_preview_preference);
        setSelectable(false);
        mMask.addCircle(50, 50, 50, Path.Direction.CW);
    }

    static List<AppIcon> loadIcons(Context context) {
        PackageManager pm = context.getPackageManager();
        List<AppIcon> icons = new ArrayList<>();
        String[] packages = {"com.android.dialer", "com.android.messaging",
                "org.lineageos.jelly", "com.android.settings", "com.android.contacts",
                "org.lineageos.aperture", "com.android.deskclock"};
        for (String packageName : packages) {
            try {
                ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
                if (!app.enabled) continue;
                icons.add(new AppIcon(pm.getApplicationLabel(app),
                        pm.getApplicationIcon(app).mutate()));
                if (icons.size() == 4) break;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Some products do not include every optional system application.
            }
        }
        return icons;
    }

    void setIcons(List<AppIcon> icons) {
        mIcons = icons;
        if (mView != null) mView.invalidate();
    }

    void setConfiguration(int sizePercent, float baseSizeDp, String mask, boolean showLabels) {
        mSizePercent = sizePercent;
        mBaseSizeDp = baseSizeDp;
        mShowLabels = showLabels;
        try {
            Path path = PathParser.createPathFromPathData(mask);
            if (path != null && !path.isEmpty()) mMask = path;
        } catch (RuntimeException ignored) {
            // Retain the last usable shape if a provider returns invalid path data.
        }
        if (mView != null) mView.invalidate();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        FrameLayout container = (FrameLayout) holder.findViewById(R.id.icon_preview_container);
        container.removeAllViews();
        mView = new IconPreviewView(getContext());
        container.addView(mView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private final class IconPreviewView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBounds = new RectF();
        private final float mDensity;
        private final int mBackground;
        private final int mForeground;

        IconPreviewView(Context context) {
            super(context);
            mDensity = getResources().getDisplayMetrics().density;
            mTextPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            TypedArray colors = context.obtainStyledAttributes(new int[] {
                    com.google.android.material.R.attr.colorSurfaceContainer,
                    android.R.attr.textColorPrimary});
            mBackground = colors.getColor(0, context.getColor(android.R.color.background_light));
            mForeground = colors.getColor(1, context.getColor(android.R.color.black));
            colors.recycle();
        }

        private float dp(float value) {
            return value * mDensity;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = Math.round(dp(126) + mTextPaint.getFontSpacing());
            setMeasuredDimension(resolveSize(dpInt(320), widthMeasureSpec),
                    resolveSize(height, heightMeasureSpec));
        }

        private int dpInt(float value) {
            return Math.round(dp(value));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mBounds.set(0, 0, getWidth(), getHeight());
            mPaint.setColor(mBackground);
            canvas.drawRoundRect(mBounds, dp(24), dp(24), mPaint);
            if (mIcons.isEmpty()) return;
            float cellWidth = (getWidth() - dp(16)) / mIcons.size();
            // Reserve the maximum icon height so labels stay still while the slider moves.
            float iconSize = Math.min(dp(mBaseSizeDp * mSizePercent / 100f),
                    Math.min(cellWidth - dp(12), dp(88)));
            float centerY = dp(60);
            mTextPaint.setColor(mForeground);
            for (int i = 0; i < mIcons.size(); i++) {
                AppIcon icon = mIcons.get(i);
                float centerX = dp(8) + (i + .5f) * cellWidth;
                int save = canvas.save();
                canvas.translate(centerX - iconSize / 2, centerY - iconSize / 2);
                canvas.scale(iconSize / 100f, iconSize / 100f);
                canvas.clipPath(mMask);
                icon.drawable.setBounds(0, 0, 100, 100);
                if (icon.drawable instanceof AdaptiveIconDrawable adaptive) {
                    // Let the platform establish adaptive layer bounds and overscan, then
                    // render those layers through Launcher's selected mask instead of the
                    // platform default mask baked into AdaptiveIconDrawable.draw().
                    Drawable background = adaptive.getBackground();
                    Drawable foreground = adaptive.getForeground();
                    if (background != null) background.draw(canvas);
                    if (foreground != null) foreground.draw(canvas);
                } else {
                    icon.drawable.draw(canvas);
                }
                canvas.restoreToCount(save);
                if (mShowLabels) {
                    CharSequence label = TextUtils.ellipsize(icon.label, mTextPaint,
                            cellWidth - dp(8), TextUtils.TruncateAt.END);
                    canvas.drawText(label.toString(), centerX,
                            dp(112) - mTextPaint.ascent(), mTextPaint);
                }
            }
        }
    }
}
