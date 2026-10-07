/*
 * Copyright (C) 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.settingslib.Utils;

/** Small, vector-drawn appearance previews using the device's current accent. */
public class DisplayModePreview extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mDark;

    public DisplayModePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setDark(boolean dark) {
        mDark = dark;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth();
        final float height = getHeight();
        final int accent = Utils.getColorAccentDefaultColor(getContext());
        final int surface = ColorUtils.blendARGB(mDark ? 0xff101418 : 0xfff8fafc, accent, .12f);
        mPaint.setColor(Color.WHITE);
        mPaint.setShader(new LinearGradient(0, 0, width, height,
                surface, ColorUtils.blendARGB(surface, accent, .34f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0, 0, width, height, width * .18f, width * .18f, mPaint);
        mPaint.setShader(null);
        final int ink = mDark ? 0xffedf2f5 : 0xff263238;
        mPaint.setColor(ColorUtils.setAlphaComponent(ink, 160));
        canvas.drawRoundRect(width * .13f, height * .11f, width * .35f, height * .14f,
                height * .02f, height * .02f, mPaint);
        canvas.drawCircle(width * .84f, height * .125f, height * .018f, mPaint);
        for (int row = 0; row < 2; row++) {
            final float top = height * (.30f + row * .27f);
            mPaint.setColor(ColorUtils.blendARGB(surface, mDark ? Color.BLACK : Color.WHITE, .55f));
            canvas.drawRoundRect(width * .10f, top, width * .90f, top + height * .21f,
                    height * .07f, height * .07f, mPaint);
            mPaint.setColor(ColorUtils.setAlphaComponent(ink, 150));
            canvas.drawRoundRect(width * .19f, top + height * .06f,
                    width * .76f, top + height * .085f, height * .02f, height * .02f, mPaint);
            mPaint.setColor(ColorUtils.setAlphaComponent(ink, 65));
            canvas.drawRoundRect(width * .19f, top + height * .12f,
                    width * .59f, top + height * .145f, height * .02f, height * .02f, mPaint);
        }
        mPaint.setColor(ColorUtils.setAlphaComponent(ink, 100));
        canvas.drawRoundRect(width * .37f, height * .92f, width * .63f, height * .94f,
                height * .02f, height * .02f, mPaint);
    }
}
