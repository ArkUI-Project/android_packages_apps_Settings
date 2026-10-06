/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.depth;

import static com.android.settingslib.arkui.DepthWallpaperContract.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

/** SystemUI can request/read the current user's cache; no shared filesystem or shell helper. */
public final class DepthWallpaperProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private void enforce() { getContext().enforceCallingOrSelfPermission(PERMISSION,
            "Device depth wallpaper is restricted to platform-signed components"); }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        enforce();
        final long identity = Binder.clearCallingIdentity();
        try {
            if ("refresh".equals(method)) DepthWallpaperProcessor.request(getContext(), false);
            else if (!"status".equals(method)) throw new IllegalArgumentException("Unknown method");
            return DepthWallpaperStore.status(getContext());
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        enforce();
        if (!MASK_URI.getPath().equals(uri.getPath()) || !"r".equals(mode)) {
            throw new FileNotFoundException("Read-only current wallpaper mask");
        }
        final long identity = Binder.clearCallingIdentity();
        try { return DepthWallpaperStore.openMask(getContext()); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("ArkUI depth wallpaper: " + DepthWallpaperStore.status(getContext()));
    }
}
