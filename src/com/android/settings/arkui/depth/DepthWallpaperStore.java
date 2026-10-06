/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Private device-protected cache, tied to the actual current wallpaper ID. */
final class DepthWallpaperStore {
    private static final Object LOCK = new Object();
    private static AtomicFile file(Context context, String name) {
        final File dir = new File(context.createDeviceProtectedStorageContext().getFilesDir(),
                "depth_wallpaper");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create cache");
        return new AtomicFile(new File(dir, name));
    }
    static int which(Context context) {
        return WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_LOCK) > 0
                ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
    }
    static int wallpaperId(Context context) {
        return WallpaperManager.getInstance(context).getWallpaperId(which(context));
    }
    static boolean isStatic(Context context) {
        return WallpaperManager.getInstance(context).getWallpaperInfo(which(context)) == null;
    }
    static JSONObject metadata(Context context) {
        synchronized (LOCK) {
            try {
                return new JSONObject(new String(file(context, "metadata.json").readFully(),
                        StandardCharsets.UTF_8));
            } catch (Exception ignored) { return new JSONObject(); }
        }
    }
    static boolean matches(Context context, JSONObject meta) {
        return meta.optInt("wallpaper", -1) == wallpaperId(context)
                && meta.optInt("which", -1) == which(context) && isStatic(context);
    }
    static Bundle status(Context context) {
        final JSONObject meta = metadata(context);
        final Bundle result = new Bundle();
        final boolean matching = matches(context, meta);
        result.putString("state", DepthWallpaperProcessor.isProcessing() ? "processing"
                : !isStatic(context) ? "unsupported" : matching
                ? meta.optString("state", "empty") : "empty");
        result.putString("source", matching ? meta.optString("source", "auto") : "auto");
        result.putInt("wallpaper", wallpaperId(context));
        result.putInt("coverage", matching ? meta.optInt("coverage") : 0);
        result.putLong("elapsed", matching ? meta.optLong("elapsed") : 0);
        result.putBoolean("ready", matching && file(context, "mask.png").getBaseFile().exists());
        return result;
    }
    static ParcelFileDescriptor openMask(Context context) throws java.io.FileNotFoundException {
        synchronized (LOCK) {
            if (!matches(context, metadata(context))) {
                throw new java.io.FileNotFoundException("No mask for current wallpaper");
            }
            return ParcelFileDescriptor.open(file(context, "mask.png").getBaseFile(),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }
    static Bitmap readMask(Context context) {
        try (ParcelFileDescriptor fd = openMask(context)) {
            return BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
        } catch (Exception ignored) { return null; }
    }
    static void save(Context context, Bitmap mask, int id, int which, String source,
            String state, long elapsed) throws Exception {
        save(context, mask, id, which, source, state, elapsed,
                SUBJECT.read(context.getContentResolver(), context.getUserId()),
                CONFIDENCE.read(context.getContentResolver(), context.getUserId()));
    }
    static void save(Context context, Bitmap mask, int id, int which, String source,
            String state, long elapsed, int subject, int confidence) throws Exception {
        synchronized (LOCK) {
            if (id != wallpaperId(context) || which != which(context)) {
                throw new IllegalStateException("Wallpaper changed before mask was saved");
            }
            final JSONObject meta = new JSONObject();
            meta.put("wallpaper", id);
            meta.put("which", which);
            meta.put("source", source);
            meta.put("state", state);
            meta.put("subject", subject);
            meta.put("confidence", confidence);
            meta.put("elapsed", elapsed);
            int coverage = 0;
            if (mask != null) {
                final int[] pixels = new int[mask.getWidth() * mask.getHeight()];
                mask.getPixels(pixels, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
                long alpha = 0;
                for (int pixel : pixels) alpha += pixel >>> 24;
                coverage = (int) (alpha * 100 / (255L * pixels.length));
                final AtomicFile image = file(context, "mask.png");
                FileOutputStream stream = image.startWrite();
                try {
                    if (!mask.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new java.io.IOException("Cannot encode mask");
                    }
                    image.finishWrite(stream);
                } catch (Exception e) { image.failWrite(stream); throw e; }
            } else {
                file(context, "mask.png").delete();
            }
            meta.put("coverage", coverage);
            final AtomicFile description = file(context, "metadata.json");
            FileOutputStream stream = description.startWrite();
            try {
                stream.write(meta.toString().getBytes(StandardCharsets.UTF_8));
                description.finishWrite(stream);
            } catch (Exception e) { description.failWrite(stream); throw e; }
        }
        notifyChanged(context);
    }
    static void notifyChanged(Context context) {
        final var resolver = context.getContentResolver();
        final long revision = Math.max(System.currentTimeMillis(),
                Settings.Secure.getLong(resolver, REVISION, 0) + 1);
        Settings.Secure.putLong(resolver, REVISION, revision);
        resolver.notifyChange(URI, null);
    }
}
