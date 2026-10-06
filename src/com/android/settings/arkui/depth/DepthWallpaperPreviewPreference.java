/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

public final class DepthWallpaperPreviewPreference extends Preference {
    private Bitmap mBackground, mMask;
    private CharSequence mStatus;
    public DepthWallpaperPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.arkui_depth_preview_preference);
    }
    void update(Bitmap background, Bitmap mask, CharSequence status) {
        mBackground = background;
        mMask = mask;
        mStatus = status;
        notifyChanged();
    }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final DepthWallpaperPreviewView preview = (DepthWallpaperPreviewView)
                holder.findViewById(R.id.depth_preview_view);
        preview.setImages(mBackground, mMask, false);
        preview.readOptions();
        ((TextView) holder.findViewById(R.id.depth_preview_status)).setText(mStatus);
    }
}
