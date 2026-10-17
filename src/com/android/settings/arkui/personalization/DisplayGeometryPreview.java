/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.android.settingslib.Utils;

/** A scaled portrait preview using the same natural-display coordinates as the overlay. */
public final class DisplayGeometryPreview extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private DisplayGeometrySettings mModel;
    private int[] mValues;

    public DisplayGeometryPreview(Context context, AttributeSet attrs) { super(context, attrs); }

    void update(DisplayGeometrySettings model, int[] values) {
        mModel = model;
        mValues = values.clone();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.round(Math.min(width * 1.05f, 340 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mModel == null || mValues == null) return;
        float inset = 12 * getResources().getDisplayMetrics().density;
        float scale = Math.min((getWidth() - 2 * inset) / mModel.displayWidth,
                (getHeight() - 2 * inset) / mModel.displayHeight);
        float left = (getWidth() - mModel.displayWidth * scale) / 2;
        float top = (getHeight() - mModel.displayHeight * scale) / 2;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        float width = mModel.displayWidth, height = mModel.displayHeight;
        float density = mModel.physicalDensity;
        float radius = mValues[DisplayGeometrySettings.RADIUS] < 0 ? mModel.nativeRadius
                : mValues[DisplayGeometrySettings.RADIUS] * density;
        // Both Settings themes define platform colors; Material attributes are optional.
        int surface = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorBackground, Color.WHITE);
        int ink = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary, Color.BLACK);
        int primary = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorAccent, ink);
        mPaint.setColor(ink);
        canvas.drawRoundRect(-4 / scale, -4 / scale, width + 4 / scale, height + 4 / scale,
                radius + 4 / scale, radius + 4 / scale, mPaint);
        mClip.rewind();
        mClip.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
        canvas.clipPath(mClip);
        canvas.drawColor(surface);
        float statusHeight = mModel.statusHeight(mValues);
        mPaint.setColor((primary & 0x00ffffff) | 0x30000000);
        canvas.drawRect(0, 0, width, statusHeight, mPaint);
        float paddingLeft = (mValues[0] < 0 ? 12 : mValues[0]) * mModel.statusDensity;
        float paddingRight = (mValues[1] < 0 ? 12 : mValues[1]) * mModel.statusDensity;
        float paddingTop = Math.max(0, mValues[2]) * mModel.statusDensity;
        mPaint.setColor(ink);
        mPaint.setTextSize(13 * density);
        float baseline = paddingTop + (statusHeight - paddingTop) / 2
                - (mPaint.ascent() + mPaint.descent()) / 2;
        canvas.drawText("12:30", paddingLeft, baseline, mPaint);
        float iconsRight = width - paddingRight;
        canvas.drawRoundRect(iconsRight - 20 * density, baseline - 9 * density,
                iconsRight, baseline, 2 * density, 2 * density, mPaint);
        for (int i = 0; i < 3; i++) {
            canvas.drawRect(iconsRight - (35 - i * 4) * density,
                    baseline - (3 + i * 3) * density,
                    iconsRight - (33 - i * 4) * density, baseline, mPaint);
        }
        mPaint.setColor((primary & 0x00ffffff) | 0x24000000);
        for (int i = 0; i < 4; i++) {
            float y = statusHeight + (24 + i * 64) * density;
            canvas.drawRoundRect(18 * density, y, width - 18 * density,
                    y + 48 * density, 12 * density, 12 * density, mPaint);
        }
        RectF hole = mModel.cutout(mValues);
        if (!hole.isEmpty()) {
            mPaint.setColor(Color.BLACK);
            canvas.drawRoundRect(hole, hole.height() / 2, hole.height() / 2, mPaint);
        }
        mPaint.setColor(ink);
        canvas.drawRoundRect(width * .36f, height - 12 * density,
                width * .64f, height - 8 * density, 2 * density, 2 * density, mPaint);
        canvas.restore();
    }
}
