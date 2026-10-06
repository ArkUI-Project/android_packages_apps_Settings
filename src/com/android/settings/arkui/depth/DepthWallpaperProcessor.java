/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Offline semantic segmentation. One coalesced job, discarded if user/wallpaper/settings change. */
final class DepthWallpaperProcessor {
    static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor();
    private static final Object LOCK = new Object();
    private static ScheduledFuture<?> sPending;
    private static String sActiveKey;
    private static int sGeneration;

    static boolean isProcessing() { synchronized (LOCK) { return sActiveKey != null; } }
    static void cancel() {
        synchronized (LOCK) {
            ++sGeneration;
            if (sPending != null) sPending.cancel(false);
            sActiveKey = null;
        }
    }
    static void request(Context context, boolean force) {
        final Context app = context.getApplicationContext();
        if (!DepthWallpaperStore.isStatic(app)) return;
        final int user = app.getUserId();
        if (!force && (ENABLED.read(app.getContentResolver(), user) == 0
                || AUTO_REFRESH.read(app.getContentResolver(), user) == 0)) return;
        final int id = DepthWallpaperStore.wallpaperId(app);
        final int subject = SUBJECT.read(app.getContentResolver(), user);
        final int confidence = CONFIDENCE.read(app.getContentResolver(), user);
        final JSONObject meta = DepthWallpaperStore.metadata(app);
        if (!force && DepthWallpaperStore.matches(app, meta)
                && (!"auto".equals(meta.optString("source"))
                || (meta.optInt("subject", -1) == subject
                && meta.optInt("confidence", -1) == confidence))) return;
        final String key = user + ":" + id + ":" + subject + ":" + confidence;
        synchronized (LOCK) {
            if (key.equals(sActiveKey)) return;
            final int generation = ++sGeneration;
            sActiveKey = key;
            if (sPending != null) sPending.cancel(false);
            sPending = WORKER.schedule(() -> process(app, id, generation, subject, confidence),
                    150, TimeUnit.MILLISECONDS);
        }
        app.getContentResolver().notifyChange(URI, null);
    }

    private static void process(Context context, int id, int generation, int subject,
            int confidence) {
        final long started = SystemClock.elapsedRealtime();
        try {
            final int which = DepthWallpaperStore.which(context);
            final Bitmap background = readWallpaper(context);
            if (background == null) throw new IllegalStateException("No static wallpaper");
            final Bitmap mask = segment(context, background, subject, confidence);
            synchronized (LOCK) {
                if (generation != sGeneration || id != DepthWallpaperStore.wallpaperId(context)) {
                    return;
                }
                final int[] pixels = new int[mask.getWidth() * mask.getHeight()];
                mask.getPixels(pixels, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
                int foreground = 0;
                for (int pixel : pixels) if ((pixel >>> 24) > 127) ++foreground;
                DepthWallpaperStore.save(context, mask, id, which, "auto",
                        foreground < pixels.length / 500 ? "no_subject" : "ready",
                        SystemClock.elapsedRealtime() - started, subject, confidence);
            }
        } catch (Exception | LinkageError e) {
            Log.e("ArkuiDepthWallpaper", "Cannot segment wallpaper", e);
            synchronized (LOCK) {
                if (generation == sGeneration) {
                    try {
                        DepthWallpaperStore.save(context, null, id,
                                DepthWallpaperStore.which(context), "auto", "error", 0);
                    } catch (Exception ignored) { }
                }
            }
        } finally {
            synchronized (LOCK) { if (generation == sGeneration) sActiveKey = null; }
            context.getContentResolver().notifyChange(URI, null);
        }
    }

    static Bitmap readWallpaper(Context context) throws Exception {
        if (!DepthWallpaperStore.isStatic(context)) return null;
        final WallpaperManager manager = WallpaperManager.getInstance(context);
        final int which = DepthWallpaperStore.which(context);
        Bitmap bitmap;
        try (ParcelFileDescriptor fd = manager.getWallpaperFile(which)) {
            if (fd == null) {
                bitmap = manager.getBitmapAsUser(context.getUserId(), false, which, true);
            } else {
                final BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, bounds);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) {
                    options.inSampleSize *= 2;
                }
                bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
            }
        }
        if (bitmap == null) return null;
        final int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= 1920) return bitmap;
        return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 1920 / longest,
                bitmap.getHeight() * 1920 / longest, true);
    }

    /** Crop a newly selected picture to the display before segmentation and wallpaper application. */
    static Bitmap cropForDisplay(Context context, Bitmap source) {
        final Rect bounds = context.getSystemService(android.view.WindowManager.class)
                .getMaximumWindowMetrics().getBounds();
        final float factor = Math.min(1f, 1920f / Math.max(bounds.width(), bounds.height()));
        final int w = Math.round(bounds.width() * factor), h = Math.round(bounds.height() * factor);
        final Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final float scale = Math.max((float) w / source.getWidth(), (float) h / source.getHeight());
        final float dx = (w - source.getWidth() * scale) / 2f;
        final float dy = (h - source.getHeight() * scale) / 2f;
        new Canvas(result).drawBitmap(source, null,
                new RectF(dx, dy, dx + source.getWidth() * scale,
                        dy + source.getHeight() * scale),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return result;
    }

    static Bitmap segment(Context context, Bitmap background, int subject, int confidence)
            throws Exception {
        final ByteBuffer model;
        try (InputStream stream = context.getAssets().open("depth/deeplab_v3.tflite");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            stream.transferTo(bytes);
            final byte[] data = bytes.toByteArray();
            model = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
            model.put(data).rewind();
        }
        try (Interpreter interpreter = new Interpreter(model,
                new Interpreter.Options().setNumThreads(2))) {
            final int[] in = interpreter.getInputTensor(0).shape();
            final int[] out = interpreter.getOutputTensor(0).shape();
            if (in.length != 4 || out.length != 4 || in[3] != 3 || out[3] != 21) {
                throw new IllegalStateException("Unexpected segmentation model");
            }
            final Bitmap inputImage = Bitmap.createScaledBitmap(background, in[2], in[1], true);
            final int[] rgb = new int[in[1] * in[2]];
            inputImage.getPixels(rgb, 0, in[2], 0, 0, in[2], in[1]);
            final ByteBuffer input = ByteBuffer.allocateDirect(rgb.length * 12)
                    .order(ByteOrder.nativeOrder());
            for (int pixel : rgb) {
                input.putFloat((((pixel >> 16) & 255) - 127.5f) / 127.5f);
                input.putFloat((((pixel >> 8) & 255) - 127.5f) / 127.5f);
                input.putFloat(((pixel & 255) - 127.5f) / 127.5f);
            }
            input.rewind();
            final ByteBuffer output = ByteBuffer.allocateDirect(out[1] * out[2] * out[3] * 4)
                    .order(ByteOrder.nativeOrder());
            interpreter.run(input, output);
            output.rewind();
            final int[] alpha = new int[out[1] * out[2]];
            final float[] scores = new float[out[3]];
            for (int i = 0; i < alpha.length; i++) {
                float max = -Float.MAX_VALUE, sum = 0;
                boolean nonnegative = true;
                int best = 0;
                for (int c = 0; c < scores.length; c++) {
                    scores[c] = output.getFloat();
                    nonnegative &= scores[c] >= 0;
                    sum += scores[c];
                    if (scores[c] > max) { max = scores[c]; best = c; }
                }
                final int category = subject == 1 ? 15 : best; // Pascal VOC person label.
                float probability = 0;
                if (category != 0) {
                    if (nonnegative && Math.abs(sum - 1f) < 0.01f) {
                        probability = scores[category];
                    } else {
                        float denominator = 0;
                        for (float score : scores) denominator += (float) Math.exp(score - max);
                        probability = (float) Math.exp(scores[category] - max) / denominator;
                    }
                }
                final int a = Math.round(255 * Math.max(0f, Math.min(1f,
                        (probability - confidence / 100f + 0.08f) / 0.16f)));
                alpha[i] = (a << 24) | 0x00ffffff;
            }
            return Bitmap.createScaledBitmap(Bitmap.createBitmap(alpha, out[2], out[1],
                    Bitmap.Config.ARGB_8888), background.getWidth(), background.getHeight(), true);
        }
    }

    static boolean hasSubject(Bitmap mask) {
        final int[] pixels = new int[mask.getWidth() * mask.getHeight()];
        mask.getPixels(pixels, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
        int foreground = 0;
        for (int pixel : pixels) if ((pixel >>> 24) > 127) ++foreground;
        return foreground >= Math.max(1, pixels.length / 500);
    }
}
