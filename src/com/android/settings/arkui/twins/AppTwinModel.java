/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

/** Serializes mutations and survives Settings activity recreation. */
public final class AppTwinModel extends AndroidViewModel {
    interface Operation { void run() throws Exception; }
    final MutableLiveData<Integer> revision = new MutableLiveData<>(0);
    final AppTwinStore store;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    List<AppTwinStore.Twin> twins = List.of();
    List<AppTwinStore.App> apps = List.of();
    boolean busy;
    boolean loaded;
    boolean canCreate;
    String error;

    public AppTwinModel(Application app) { super(app); store = new AppTwinStore(app); }

    void refresh() { if (!busy) run(() -> { }); }

    void run(Operation operation) {
        if (busy) return;
        busy = true;
        revision.setValue(revision.getValue() + 1);
        AppTwinStore.EXECUTOR.execute(() -> {
            String failure = null;
            List<AppTwinStore.Twin> loadedTwins = List.of();
            List<AppTwinStore.App> loadedApps = List.of();
            boolean allowed = false;
            try { operation.run(); }
            catch (Exception e) {
                Log.e("AppTwins", "Operation failed", e);
                failure = e.getLocalizedMessage();
                if (failure == null) failure = e.getClass().getSimpleName();
            }
            try {
                loadedTwins = store.twins();
                loadedApps = store.apps();
                allowed = store.canCreate();
            } catch (Exception e) {
                Log.e("AppTwins", "Refresh failed", e);
                failure = e.getLocalizedMessage();
            }
            final String result = failure;
            final var entries = loadedTwins;
            final var choices = loadedApps;
            final boolean enabled = allowed;
            mMain.post(() -> {
                twins = entries; apps = choices; canCreate = enabled;
                error = result; busy = false; loaded = true;
                revision.setValue(revision.getValue() + 1);
            });
        });
    }
}
