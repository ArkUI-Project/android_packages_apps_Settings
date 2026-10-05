/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class AppTwinReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppTwinStore.available(context) || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        String action = intent.getAction();
        boolean removal = Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action);
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !removal) return;
        String removed = removal && intent.getData() != null
                ? intent.getData().getSchemeSpecificPart() : null;
        PendingResult pending = goAsync();
        AppTwinStore.EXECUTOR.execute(() -> {
            try { new AppTwinStore(context).reconcile(removed); }
            catch (Exception e) { Log.e("AppTwins", "Could not reconcile app twins", e); }
            finally { pending.finish(); }
        });
    }
}
