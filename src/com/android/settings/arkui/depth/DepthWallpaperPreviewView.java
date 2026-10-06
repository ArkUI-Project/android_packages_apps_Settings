/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayDeque;

/** Actual-photo preview and alpha-mask brush editor. No synthetic foreground is shown. */
public final class DepthWallpaperPreviewView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mClock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBrush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mPhone = new RectF();
    private final RectF mClockBounds = new RectF();
    private final Matrix mBackgroundMatrix = new Matrix();
    private final Matrix mMaskMatrix = new Matrix();
    private final Matrix mInverse = new Matrix();
    private final Path mClip = new Path();
    private final ArrayDeque<Bitmap> mUndo = new ArrayDeque<>();
    private Bitmap mBackground, mMask, mSoftMask;
    private Canvas mMaskCanvas;
    private int mMode, mOpacity = 100, mCoverage = 55, mScale = 100, mDx, mDy, mFeather;
    private int mPrepareGeneration;
    private float mBrushDp = 18, mLastX, mLastY;
    private boolean mMutable, mStroke;
    private Runnable mOnEdit;

    public DepthWallpaperPreviewView(Context context) { this(context, null); }
    public DepthWallpaperPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mClock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mClock.setTextAlign(Paint.Align.CENTER);
        mBrush.setStrokeCap(Paint.Cap.ROUND);
        mBrush.setStrokeJoin(Paint.Join.ROUND);
        mBrush.setStyle(Paint.Style.STROKE);
        setContentDescription(context.getString(R.string.arkui_depth_preview));
        readOptions();
    }
    public void setImages(Bitmap background, Bitmap mask, boolean mutable) {
        mBackground = background;
        mMutable = mutable;
        if (background != null && mutable) {
            mMask = mask == null ? Bitmap.createBitmap(background.getWidth(),
                    background.getHeight(), Bitmap.Config.ARGB_8888)
                    : mask.copy(Bitmap.Config.ARGB_8888, true);
            mMaskCanvas = new Canvas(mMask);
        } else {
            mMask = mask;
            mMaskCanvas = null;
        }
        mUndo.clear();
        prepare();
        invalidate();
    }
    public Bitmap getPhoto() { return mBackground; }
    public Bitmap getMask() { return mMask; }
    public void setOnEdit(Runnable listener) { mOnEdit = listener; }
    public boolean canUndo() { return !mUndo.isEmpty(); }
    public void setMode(int mode) {
        mMode = mode;
        ++mPrepareGeneration;
        if (mode == 0) prepare();
        invalidate();
    }
    public void setBrushSize(float dp) { mBrushDp = dp; }
    public void readOptions() {
        final var resolver = getContext().getContentResolver();
        final int user = getContext().getUserId();
        mOpacity = OPACITY.read(resolver, user);
        mCoverage = COVERAGE.read(resolver, user);
        mScale = SCALE.read(resolver, user);
        mDx = OFFSET_X.read(resolver, user);
        mDy = OFFSET_Y.read(resolver, user);
        mFeather = FEATHER.read(resolver, user);
        prepare();
    }
    private void prepare() {
        final int generation = ++mPrepareGeneration;
        mSoftMask = mMask;
        if (mMask == null || mFeather == 0 || mMode != 0) { invalidate(); return; }
        final Bitmap mask = mMask;
        final int screenWidth = getContext().getSystemService(WindowManager.class)
                .getMaximumWindowMetrics().getBounds().width();
        final int radius = Math.round(mFeather * getResources().getDisplayMetrics().density
                * mask.getWidth() / Math.max(1, screenWidth));
        ThreadUtils.postOnBackgroundThread(() -> {
            final Bitmap result = feather(mask, radius);
            post(() -> {
                if (generation == mPrepareGeneration) { mSoftMask = result; invalidate(); }
            });
        });
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final Rect bounds = getContext().getSystemService(WindowManager.class)
                .getMaximumWindowMetrics().getBounds();
        final float ratio = (float) bounds.width() / Math.max(1, bounds.height());
        final float h = Math.min(getHeight() - 8f, (getWidth() - 8f) / ratio), w = h * ratio;
        mPhone.set((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        mClip.reset();
        mClip.addRoundRect(mPhone, w * 0.09f, w * 0.09f, Path.Direction.CW);
        final int clipped = canvas.save();
        canvas.clipPath(mClip);
        mPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY));
        mPaint.setAlpha(255);
        mPaint.setColorFilter(null);
        mPaint.setXfermode(null);
        canvas.drawRect(mPhone, mPaint);
        if (mBackground != null) {
            matrix(mBackground, mBackgroundMatrix, 100, 0, 0);
            canvas.drawBitmap(mBackground, mBackgroundMatrix, mPaint);
        }
        if (mMask != null) {
            matrix(mMask, mMaskMatrix, mScale, mDx, mDy);
            mMaskMatrix.invert(mInverse);
        }
        final String hour = android.text.format.DateFormat.format("HH", new java.util.Date()).toString();
        final String minute = android.text.format.DateFormat.format("mm", new java.util.Date()).toString();
        mClock.setColor(Color.WHITE);
        mClock.setTextSize(w * 0.42f);
        final float y1 = mPhone.top + h * 0.23f, y2 = mPhone.top + h * 0.40f;
        mClockBounds.set(mPhone.left + w * 0.14f, y1 - mClock.getTextSize(),
                mPhone.right - w * 0.14f, y2 + mClock.getTextSize() * 0.1f);
        final int clock = canvas.saveLayer(mClockBounds, null);
        canvas.drawText(hour, mPhone.centerX(), y1, mClock);
        canvas.drawText(minute, mPhone.centerX(), y2, mClock);
        final Bitmap mask = mMode == 0 ? mSoftMask : mMask;
        if (mask != null) {
            final float coverage = coverage(mask);
            final float alpha = coverage == 0 ? mOpacity / 100f
                    : Math.min(mOpacity / 100f, mCoverage / 100f / coverage);
            mPaint.setAlpha(Math.round(alpha * 255));
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvas.drawBitmap(mask, mMaskMatrix, mPaint);
            mPaint.setXfermode(null);
            mPaint.setAlpha(255);
        }
        canvas.restoreToCount(clock);
        if (mMode != 0 && mMask != null) {
            mPaint.setColorFilter(new PorterDuffColorFilter(MaterialColors.getColor(this,
                    androidx.appcompat.R.attr.colorPrimary, Color.CYAN),
                    PorterDuff.Mode.SRC_IN));
            mPaint.setAlpha(100);
            canvas.drawBitmap(mMask, mMaskMatrix, mPaint);
            mPaint.setAlpha(255);
            mPaint.setColorFilter(null);
        }
        mClock.setTextSize(w * 0.065f);
        canvas.drawText(android.text.format.DateFormat.format("MMM d · EEE",
                new java.util.Date()).toString(), mPhone.centerX(), mPhone.top + h * 0.47f, mClock);
        mClock.setStrokeWidth(w * 0.018f);
        canvas.drawLine(mPhone.centerX() - w * 0.15f, mPhone.bottom - h * 0.025f,
                mPhone.centerX() + w * 0.15f, mPhone.bottom - h * 0.025f, mClock);
        canvas.restoreToCount(clipped);
    }
    private void matrix(Bitmap bitmap, Matrix matrix, int zoom, int dx, int dy) {
        final float scale = Math.max(mPhone.width() / bitmap.getWidth(),
                mPhone.height() / bitmap.getHeight()) * zoom / 100f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(mPhone.centerX() - bitmap.getWidth() * scale / 2f
                + mPhone.width() * dx / 100f,
                mPhone.centerY() - bitmap.getHeight() * scale / 2f
                + mPhone.height() * dy / 100f);
    }
    private float coverage(Bitmap mask) {
        float sum = 0;
        final float[] point = new float[2];
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            point[0] = mClockBounds.left + mClockBounds.width() * (x + 0.5f) / 12;
            point[1] = mClockBounds.top + mClockBounds.height() * (y + 0.5f) / 12;
            mInverse.mapPoints(point);
            final int bx = (int) point[0], by = (int) point[1];
            if (bx >= 0 && by >= 0 && bx < mask.getWidth() && by < mask.getHeight()) {
                sum += (mask.getPixel(bx, by) >>> 24) / 255f;
            }
        }
        return sum / 144;
    }
    private void remember() {
        if (mMask == null) return;
        if (mUndo.size() == 4) mUndo.removeFirst();
        mUndo.addLast(mMask.copy(Bitmap.Config.ARGB_8888, true));
    }
    private void edited() {
        ++mPrepareGeneration;
        mSoftMask = mMask;
        if (mOnEdit != null) mOnEdit.run();
        invalidate();
    }
    public void undo() {
        if (mUndo.isEmpty()) return;
        mMask = mUndo.removeLast();
        mMaskCanvas = new Canvas(mMask);
        edited();
        if (mMode == 0) prepare();
    }
    public void clearMask() {
        if (!mMutable || mMask == null) return;
        remember();
        mMaskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        edited();
        if (mMode == 0) prepare();
    }
    public void invertMask() {
        if (!mMutable || mMask == null) return;
        remember();
        final int[] pixels = new int[mMask.getWidth() * mMask.getHeight()];
        mMask.getPixels(pixels, 0, mMask.getWidth(), 0, 0, mMask.getWidth(), mMask.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = ((255 - (pixels[i] >>> 24)) << 24) | 0x00ffffff;
        }
        mMask.setPixels(pixels, 0, mMask.getWidth(), 0, 0, mMask.getWidth(), mMask.getHeight());
        edited();
        if (mMode == 0) prepare();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mMutable || mMode == 0 || mMask == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!mPhone.contains(event.getX(), event.getY())) return false;
            remember();
            mStroke = true;
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (!mStroke) return false;
        final float[] point = {event.getX(), event.getY()};
        mInverse.mapPoints(point);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            mBrush.setColor(Color.WHITE);
            mBrush.setXfermode(mMode == 2 ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
            mBrush.setStrokeWidth(mBrushDp * getResources().getDisplayMetrics().density
                    * mMask.getWidth() / Math.max(1f, mPhone.width()));
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mLastX = point[0]; mLastY = point[1];
            }
            mMaskCanvas.drawLine(mLastX, mLastY, point[0], point[1], mBrush);
            mLastX = point[0]; mLastY = point[1];
            edited();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mStroke = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
