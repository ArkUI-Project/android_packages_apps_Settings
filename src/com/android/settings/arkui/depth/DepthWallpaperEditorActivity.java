/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

/** Native expressive editor; wallpaper changes are made only when Apply is pressed. */
public final class DepthWallpaperEditorActivity extends Activity {
    private static final int PHOTO = 1, MASK = 2;
    private record Session(Bitmap photo, Bitmap mask, int id, int which,
            boolean newPhoto, String source, int subject, int confidence) { }
    private DepthWallpaperPreviewView mPreview;
    private MaterialButton mApply, mUndo;
    private MaterialButtonToggleGroup mModes;
    private LinearLayout mBody;
    private TextView mStatus;
    private int mWallpaperId, mWhich, mTaskGeneration, mPreviewId;
    private boolean mNewPhoto, mBusy;
    private String mSource = "manual";
    private int mSubject, mConfidence;
    private final List<MaterialButton> mButtons = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_Arkui_DepthWallpaperEditor);
        super.onCreate(state);
        final UserManager users = getSystemService(UserManager.class);
        if (users.isManagedProfile() || users.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER)) {
            finish(); return;
        }
        getWindow().setDecorFitsSystemWindows(false);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(root,
                com.google.android.material.R.attr.colorSurface, Color.BLACK));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            final var bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        final MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.arkui_depth_edit);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.arkui_depth_back);
        toolbar.setNavigationOnClickListener(view -> finish());
        mApply = button(R.string.arkui_depth_apply, false, this::apply);
        final androidx.appcompat.widget.Toolbar.LayoutParams applyParams =
                new androidx.appcompat.widget.Toolbar.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), Gravity.END);
        applyParams.setMarginEnd(dp(12));
        toolbar.addView(mApply, applyParams);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        mBody = new LinearLayout(this);
        mBody.setOrientation(LinearLayout.VERTICAL);
        mBody.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(mBody);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final TextView intro = text(R.string.arkui_depth_editor_intro, 14);
        intro.setLineSpacing(dp(3), 1f);
        add(intro, -2, 0);
        mPreview = new DepthWallpaperPreviewView(this);
        mPreview.setOnEdit(() -> {
            mSource = "manual";
            mUndo.setEnabled(mPreview.canUndo());
        });
        add(mPreview, dp(400), 16);
        mStatus = text(R.string.arkui_depth_loading, 14);
        add(mStatus, -2, 12);
        mModes = new MaterialButtonToggleGroup(this);
        mModes.setSingleSelection(true);
        mModes.setSelectionRequired(true);
        final int[] modeIds = new int[3];
        final int[] modeTitles = {R.string.arkui_depth_view, R.string.arkui_depth_draw,
                R.string.arkui_depth_erase};
        for (int i = 0; i < 3; i++) {
            final MaterialButton mode = button(modeTitles[i], true, () -> { });
            modeIds[i] = View.generateViewId();
            mode.setId(modeIds[i]);
            mModes.addView(mode, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        mPreviewId = modeIds[0];
        mModes.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (checked) for (int i = 0; i < modeIds.length; i++) {
                if (checkedId == modeIds[i]) mPreview.setMode(i);
            }
        });
        mModes.check(mPreviewId);
        add(mModes, -2, 16);
        final LinearLayout primary = row();
        primary.addView(button(R.string.arkui_depth_recognize, false, this::recognize), cell());
        primary.addView(button(R.string.arkui_depth_photo, true, () -> pick(PHOTO)), cell());
        add(primary, -2, 16);
        final LinearLayout secondary = row();
        secondary.addView(button(R.string.arkui_depth_import, true, () -> pick(MASK)), cell());
        mUndo = button(R.string.arkui_depth_undo, true, () -> mPreview.undo());
        mUndo.setEnabled(false);
        secondary.addView(mUndo, cell());
        secondary.addView(button(R.string.arkui_depth_invert, true, () -> mPreview.invertMask()), cell());
        add(secondary, -2, 12);
        final TextView brushTitle = text(R.string.arkui_depth_brush, 16);
        add(brushTitle, -2, 20);
        final Slider brush = new Slider(this);
        brush.setValueFrom(4); brush.setValueTo(64); brush.setStepSize(1); brush.setValue(18);
        brush.setContentDescription(getString(R.string.arkui_depth_brush));
        brush.setLabelFormatter(value -> getString(R.string.arkui_depth_value_dp, Math.round(value)));
        brush.addOnChangeListener((slider, value, fromUser) -> mPreview.setBrushSize(value));
        add(brush, dp(56), 8);
        add(button(R.string.arkui_depth_clear, true, () -> mPreview.clearMask()), dp(52), 12);
        setContentView(root);
        final Session session = (Session) getLastNonConfigurationInstance();
        if (session != null && session.photo() != null) {
            mWallpaperId = session.id(); mWhich = session.which();
            mNewPhoto = session.newPhoto(); mSource = session.source();
            mSubject = session.subject(); mConfidence = session.confidence();
            mPreview.setImages(session.photo(), session.mask(), true);
            busy(false, R.string.arkui_depth_ready);
        } else {
            busy(true, R.string.arkui_depth_loading);
            final int generation = ++mTaskGeneration;
            DepthWallpaperProcessor.WORKER.execute(() -> {
                try {
                    final int id = DepthWallpaperStore.wallpaperId(this);
                    final int which = DepthWallpaperStore.which(this);
                    final Bitmap photo = DepthWallpaperProcessor.readWallpaper(this);
                    final Bitmap mask = DepthWallpaperStore.readMask(this);
                    final String source = DepthWallpaperStore.metadata(this).optString("source", "manual");
                    final var meta = DepthWallpaperStore.metadata(this);
                    runOnUiThread(() -> {
                        if (!valid(generation)) return;
                        mWallpaperId = id; mWhich = which; mSource = source;
                        mSubject = meta.optInt("subject", SUBJECT.read(getContentResolver(), getUserId()));
                        mConfidence = meta.optInt("confidence", CONFIDENCE.read(getContentResolver(), getUserId()));
                        mPreview.setImages(photo, mask, true);
                        busy(false, photo == null ? R.string.arkui_depth_live : R.string.arkui_depth_ready);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (valid(generation)) busy(false, R.string.arkui_depth_photo_error); });
                }
            });
        }
    }
    private boolean valid(int generation) {
        return !isFinishing() && !isDestroyed() && generation == mTaskGeneration;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(int title, int size) {
        final TextView text = new TextView(this);
        text.setText(title); text.setTextSize(size);
        text.setTextColor(MaterialColors.getColor(text,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        return text;
    }
    private MaterialButton button(int title, boolean outlined, Runnable action) {
        final MaterialButton button = outlined ? new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle) : new MaterialButton(this);
        button.setText(title); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> { if (!mBusy) action.run(); });
        mButtons.add(button);
        return button;
    }
    private LinearLayout row() {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }
    private LinearLayout.LayoutParams cell() {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
        params.setMarginEnd(dp(6));
        return params;
    }
    private void add(View view, int height, int margin) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.topMargin = dp(margin);
        mBody.addView(view, params);
    }
    private void busy(boolean value, int status) {
        mBusy = value;
        for (MaterialButton button : mButtons) button.setEnabled(!value);
        mApply.setEnabled(!value && mPreview.getPhoto() != null);
        mUndo.setEnabled(!value && mPreview.canUndo());
        mPreview.setEnabled(!value);
        for (int i = 0; i < mModes.getChildCount(); i++) mModes.getChildAt(i).setEnabled(!value);
        mStatus.setText(status);
    }
    private void pick(int request) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_TITLE, getString(request == PHOTO ? R.string.arkui_depth_choose_image
                        : R.string.arkui_depth_choose_mask)), request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final int generation = ++mTaskGeneration;
        busy(true, R.string.arkui_depth_loading);
        DepthWallpaperProcessor.WORKER.execute(() -> {
            try {
                final Bitmap image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(
                        getContentResolver(), data.getData()), (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    final int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (longest > 1920) decoder.setTargetSize(
                            Math.max(1, info.getSize().getWidth() * 1920 / longest),
                            Math.max(1, info.getSize().getHeight() * 1920 / longest));
                });
                if (request == PHOTO) {
                    final Bitmap photo = DepthWallpaperProcessor.cropForDisplay(this, image);
                    runOnUiThread(() -> {
                        if (!valid(generation)) return;
                        mNewPhoto = true; mSource = "manual";
                        mPreview.setImages(photo, null, true);
                        busy(false, R.string.arkui_depth_empty);
                        recognize();
                    });
                } else {
                    if (mPreview.getPhoto() == null) throw new IllegalStateException("No photo");
                    final Bitmap mask = importMask(image, mPreview.getPhoto());
                    runOnUiThread(() -> {
                        if (!valid(generation)) return;
                        mSource = "imported";
                        mPreview.setImages(mPreview.getPhoto(), mask, true);
                        mModes.check(mPreviewId);
                        busy(false, R.string.arkui_depth_ready);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { if (valid(generation)) busy(false,
                        request == PHOTO ? R.string.arkui_depth_photo_error : R.string.arkui_depth_mask_error); });
            }
        });
    }
    private Bitmap importMask(Bitmap image, Bitmap photo) {
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        boolean transparent = false, grayscale = true;
        for (int pixel : pixels) {
            transparent |= (pixel >>> 24) < 250;
            final int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
            grayscale &= Math.abs(r - g) <= 3 && Math.abs(r - b) <= 3;
        }
        if (!transparent && !grayscale) throw new IllegalArgumentException("Not an outline");
        for (int i = 0; i < pixels.length; i++) {
            final int alpha = transparent ? pixels[i] >>> 24 : pixels[i] & 255;
            pixels[i] = (alpha << 24) | 0x00ffffff;
        }
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(pixels, image.getWidth(),
                image.getHeight(), Bitmap.Config.ARGB_8888), photo.getWidth(), photo.getHeight(), true);
    }
    private void recognize() {
        final Bitmap photo = mPreview.getPhoto();
        if (photo == null) return;
        final int generation = ++mTaskGeneration;
        busy(true, R.string.arkui_depth_processing);
        final int subject = SUBJECT.read(getContentResolver(), getUserId());
        final int confidence = CONFIDENCE.read(getContentResolver(), getUserId());
        DepthWallpaperProcessor.WORKER.execute(() -> {
            try {
                final Bitmap mask = DepthWallpaperProcessor.segment(this, photo, subject, confidence);
                final boolean found = DepthWallpaperProcessor.hasSubject(mask);
                runOnUiThread(() -> {
                    if (!valid(generation)) return;
                    mSource = "auto";
                    mSubject = subject; mConfidence = confidence;
                    mPreview.setImages(photo, mask, true);
                    mModes.check(mPreviewId);
                    busy(false, found ? R.string.arkui_depth_ready : R.string.arkui_depth_no_subject);
                });
            } catch (Exception | LinkageError e) {
                runOnUiThread(() -> { if (valid(generation)) busy(false, R.string.arkui_depth_error); });
            }
        });
    }
    private void apply() {
        final Bitmap photo = mPreview.getPhoto(), mask = mPreview.getMask();
        if (photo == null || mask == null) return;
        if (!mNewPhoto && (mWallpaperId != DepthWallpaperStore.wallpaperId(this)
                || mWhich != DepthWallpaperStore.which(this))) {
            Toast.makeText(this, R.string.arkui_depth_changed, Toast.LENGTH_LONG).show();
            return;
        }
        final int generation = ++mTaskGeneration;
        busy(true, R.string.arkui_depth_loading);
        DepthWallpaperProcessor.cancel();
        DepthWallpaperProcessor.WORKER.execute(() -> {
            try {
                if (!mNewPhoto && (mWallpaperId != DepthWallpaperStore.wallpaperId(this)
                        || mWhich != DepthWallpaperStore.which(this))) {
                    throw new IllegalStateException("Wallpaper changed before apply");
                }
                int id = mWallpaperId, which = mWhich;
                if (mNewPhoto) {
                    id = WallpaperManager.getInstance(this).setBitmap(photo,
                            new Rect(0, 0, photo.getWidth(), photo.getHeight()), true,
                            WallpaperManager.FLAG_LOCK);
                    which = WallpaperManager.FLAG_LOCK;
                }
                DepthWallpaperStore.save(this, mask, id, which, mSource,
                        DepthWallpaperProcessor.hasSubject(mask) ? "ready" : "no_subject", 0,
                        mSubject, mConfidence);
                DepthWallpaperProcessor.cancel();
                ENABLED.write(getContentResolver(), getUserId(), 1);
                runOnUiThread(() -> {
                    if (!valid(generation)) return;
                    Toast.makeText(this, R.string.arkui_depth_applied, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (valid(generation)) busy(false, R.string.arkui_depth_save_error); });
            }
        });
    }
    @Override public Object onRetainNonConfigurationInstance() {
        return new Session(mPreview.getPhoto(), mPreview.getMask(), mWallpaperId, mWhich,
                mNewPhoto, mSource, mSubject, mConfidence);
    }
    @Override protected void onDestroy() { ++mTaskGeneration; super.onDestroy(); }
}
