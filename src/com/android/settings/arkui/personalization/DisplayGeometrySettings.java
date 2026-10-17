/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.RoundedCorner;
import android.view.Surface;

import java.util.Locale;

/** Keeps each display's calibration separate and applies complete overlay transactions. */
final class DisplayGeometrySettings {
    static final int LEFT = 0, RIGHT = 1, TOP = 2, HEIGHT = 3, RADIUS = 4,
            CUTOUT = 5, X = 6, Y = 7, WIDTH = 8, CUTOUT_HEIGHT = 9, COUNT = 10;
    static final int[] DEFAULTS = {-1, -1, -1, -1, -1, 0, 50, 4, 16, 16};
    private static final String[] OVERLAYS = {
            "arkui_status_geometry", "arkui_status_geometry_sysui", "arkui_screen_geometry"};
    private static final Object LOCK = new Object();
    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final String mPrefix;
    final String uniqueId;
    final int displayWidth, displayHeight;
    final float physicalDensity = DisplayMetrics.DENSITY_DEVICE_STABLE / 160f;
    final float statusDensity;
    final float nativeRadius, nativeStatusHeight;
    final RectF nativeCutout;

    DisplayGeometrySettings(Context context) {
        Display display = context.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        mContext = context.createDisplayContext(display);
        uniqueId = display.getUniqueId();
        if (uniqueId == null || uniqueId.isEmpty()) {
            throw new IllegalStateException("Calibration requires the built-in display");
        }
        Display.Mode largest = display.getMode();
        for (Display.Mode mode : display.getSupportedModes()) {
            if ((long) mode.getPhysicalWidth() * mode.getPhysicalHeight()
                    > (long) largest.getPhysicalWidth() * largest.getPhysicalHeight()) largest = mode;
        }
        displayWidth = largest.getPhysicalWidth();
        displayHeight = largest.getPhysicalHeight();
        mPreferences = context.getSharedPreferences("arkui_display_geometry", Context.MODE_PRIVATE);
        mPrefix = uniqueId + "/";
        Point size = new Point();
        display.getRealSize(size);
        boolean sideways = (display.getRotation() & 1) != 0;
        float ratio = (float) displayWidth / (sideways ? size.y : size.x);
        statusDensity = mContext.getResources().getDisplayMetrics().density * ratio;
        if (!mPreferences.contains(mPrefix + "native_radius")) {
            DisplayCutout cutout = display.getCutout();
            Rect bounds = cutout == null ? new Rect() : cutout.getRotated(size.x, size.y,
                    display.getRotation(), Surface.ROTATION_0).getBoundingRectTop();
            RoundedCorner corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
            mPreferences.edit()
                    .putFloat(mPrefix + "native_radius", corner == null ? 0 : corner.getRadius() * ratio)
                    .putFloat(mPrefix + "native_status", Math.max(bounds.bottom,
                            mContext.getResources().getDimensionPixelSize(
                                    com.android.internal.R.dimen.status_bar_height_default)) * ratio)
                    .putFloat(mPrefix + "native_left", bounds.left * ratio)
                    .putFloat(mPrefix + "native_top", bounds.top * ratio)
                    .putFloat(mPrefix + "native_right", bounds.right * ratio)
                    .putFloat(mPrefix + "native_bottom", bounds.bottom * ratio).apply();
        }
        nativeRadius = mPreferences.getFloat(mPrefix + "native_radius", 0);
        nativeStatusHeight = mPreferences.getFloat(mPrefix + "native_status", 24 * physicalDensity);
        nativeCutout = new RectF(mPreferences.getFloat(mPrefix + "native_left", 0),
                mPreferences.getFloat(mPrefix + "native_top", 0),
                mPreferences.getFloat(mPrefix + "native_right", 0),
                mPreferences.getFloat(mPrefix + "native_bottom", 0));
    }

    int[] read() {
        int[] values = DEFAULTS.clone();
        if (!nativeCutout.isEmpty()) {
            values[X] = Math.round(nativeCutout.centerX() / displayWidth * 100);
            values[Y] = Math.round(nativeCutout.top / physicalDensity);
            values[WIDTH] = Math.round(nativeCutout.width() / physicalDensity);
            values[CUTOUT_HEIGHT] = Math.round(nativeCutout.height() / physicalDensity);
        }
        for (int i = 0; i < COUNT; i++) values[i] = mPreferences.getInt(mPrefix + i, values[i]);
        clamp(values);
        return values;
    }

    static void clamp(int[] values) {
        for (int i = LEFT; i <= RIGHT; i++) values[i] = Math.max(-1, Math.min(80, values[i]));
        values[TOP] = Math.max(-1, Math.min(32, values[TOP]));
        values[HEIGHT] = values[HEIGHT] < 0 ? -1 : Math.max(24, Math.min(100, values[HEIGHT]));
        values[RADIUS] = Math.max(-1, Math.min(100, values[RADIUS]));
        values[CUTOUT] = values[CUTOUT] == 1 ? 1 : 0;
        values[X] = Math.max(0, Math.min(100, values[X]));
        values[Y] = Math.max(0, Math.min(100, values[Y]));
        values[WIDTH] = Math.max(4, Math.min(100, values[WIDTH]));
        values[CUTOUT_HEIGHT] = Math.max(4, Math.min(100, values[CUTOUT_HEIGHT]));
    }

    RectF cutout(int[] values) {
        if (values[CUTOUT] == 0) return new RectF(nativeCutout);
        float width = Math.min(displayWidth, values[WIDTH] * physicalDensity);
        float height = Math.min(displayHeight, values[CUTOUT_HEIGHT] * physicalDensity);
        float cx = Math.max(width / 2, Math.min(displayWidth - width / 2,
                displayWidth * values[X] / 100f));
        float top = Math.min(displayHeight - height, values[Y] * physicalDensity);
        return new RectF(cx - width / 2, top, cx + width / 2, top + height);
    }

    float statusHeight(int[] values) {
        float selected = values[HEIGHT] < 0 ? nativeStatusHeight : values[HEIGHT] * statusDensity;
        return Math.max(cutout(values).bottom,
                Math.max(selected, (Math.max(0, values[TOP]) + 24) * statusDensity));
    }

    private String cutoutPath(int[] values) {
        RectF rect = cutout(values);
        return String.format(Locale.ROOT,
                "M %.3f,%.3f a %.3f,%.3f 0 1,0 %.3f,0 a %.3f,%.3f 0 1,0 %.3f,0 Z @left",
                rect.left, rect.centerY(), rect.width() / 2, rect.height() / 2, rect.width(),
                rect.width() / 2, rect.height() / 2, -rect.width());
    }

    void apply(int[] draft, boolean shape) {
        if (mContext.getUserId() != UserHandle.USER_SYSTEM) {
            throw new SecurityException("Only the device owner can calibrate the display");
        }
        synchronized (LOCK) {
            int[] before = read(), values = before.clone();
            int start = shape ? RADIUS : LEFT, end = shape ? COUNT : RADIUS;
            System.arraycopy(draft, start, values, start, end - start);
            clamp(values);
            // Persist before configuration dispatch so a recreated page reads the new selection.
            save(values);
            try {
                OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
                if (shape) {
                    FabricatedOverlay overlay = overlay(OVERLAYS[2], "android");
                    if (values[RADIUS] >= 0 || values[CUTOUT] != 0) {
                        overlay.setResourceValue("android:string/config_arkuiDisplayShapeUniqueId",
                                TypedValue.TYPE_STRING, uniqueId, null);
                        dimension(overlay, "android:dimen/config_arkuiRoundedCornerRadius",
                                values[RADIUS] < 0 ? -1 : values[RADIUS] * physicalDensity,
                                TypedValue.COMPLEX_UNIT_PX);
                        overlay.setResourceValue("android:bool/config_arkuiDisplayCutoutOverride",
                                TypedValue.TYPE_INT_BOOLEAN, values[CUTOUT], null);
                        overlay.setResourceValue("android:string/config_arkuiDisplayCutoutPath",
                                TypedValue.TYPE_STRING, values[CUTOUT] == 0 ? "" : cutoutPath(values), null);
                        enable(transaction, overlay);
                    } else transaction.unregisterFabricatedOverlay(overlay.getIdentifier());
                } else {
                    FabricatedOverlay framework = overlay(OVERLAYS[0], "android");
                    if (values[HEIGHT] >= 0 || values[TOP] >= 0) {
                        float height = Math.max(values[HEIGHT] < 0
                                        ? nativeStatusHeight / statusDensity : values[HEIGHT],
                                Math.max(0, values[TOP]) + 24);
                        for (String name : new String[] {"status_bar_height_default", "status_bar_height",
                                "status_bar_height_portrait", "status_bar_height_landscape"}) {
                            dimension(framework, "android:dimen/" + name, height, TypedValue.COMPLEX_UNIT_DIP);
                            dimension(framework, "android:dimen/" + name, height, TypedValue.COMPLEX_UNIT_DIP, "land");
                        }
                        enable(transaction, framework);
                    } else transaction.unregisterFabricatedOverlay(framework.getIdentifier());
                    FabricatedOverlay sysui = overlay(OVERLAYS[1], "com.android.systemui");
                    if (values[LEFT] >= 0 || values[RIGHT] >= 0 || values[TOP] >= 0) {
                        dimension(sysui, "com.android.systemui:dimen/arkui_status_bar_padding_left", values[LEFT], TypedValue.COMPLEX_UNIT_DIP);
                        dimension(sysui, "com.android.systemui:dimen/arkui_status_bar_padding_right", values[RIGHT], TypedValue.COMPLEX_UNIT_DIP);
                        dimension(sysui, "com.android.systemui:dimen/arkui_status_bar_padding_top", values[TOP], TypedValue.COMPLEX_UNIT_DIP);
                        if (values[TOP] >= 0) dimension(sysui, "com.android.systemui:dimen/status_bar_padding_top", values[TOP], TypedValue.COMPLEX_UNIT_DIP);
                        enable(transaction, sysui);
                    } else transaction.unregisterFabricatedOverlay(sysui.getIdentifier());
                }
                mContext.getSystemService(OverlayManager.class).commit(transaction.build());
            } catch (RuntimeException e) {
                save(before);
                throw e;
            }
        }
    }

    private void save(int[] values) {
        SharedPreferences.Editor editor = mPreferences.edit();
        for (int i = 0; i < COUNT; i++) editor.putInt(mPrefix + i, values[i]);
        if (!editor.commit()) throw new IllegalStateException("Cannot persist display calibration");
    }

    private FabricatedOverlay overlay(String name, String target) {
        return new FabricatedOverlay.Builder(mContext.getPackageName(), name, target).build();
    }

    private void enable(OverlayManagerTransaction.Builder transaction, FabricatedOverlay overlay) {
        transaction.registerFabricatedOverlay(overlay);
        transaction.setEnabled(overlay.getIdentifier(), true, UserHandle.USER_SYSTEM);
    }

    private static void dimension(FabricatedOverlay overlay, String name, float value, int unit) {
        dimension(overlay, name, value, unit, null);
    }

    private static void dimension(FabricatedOverlay overlay, String name, float value, int unit, String config) {
        overlay.setResourceValue(name, value, unit, config);
    }
}
