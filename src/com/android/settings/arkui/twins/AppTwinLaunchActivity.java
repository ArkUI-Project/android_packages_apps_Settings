/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.widget.Toast;
import com.android.settings.R;

/** Pinned shortcuts carry a serial, never a caller-selected user or component. */
public final class AppTwinLaunchActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int caller = getLaunchedFromUid();
        var home = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (caller != Process.myUid() && (home == null
                || caller != home.activityInfo.applicationInfo.uid)) {
            finish(); return;
        }
        long serial = getIntent().getLongExtra("serial", -1);
        AppTwinStore.EXECUTOR.execute(() -> {
            boolean failed = false;
            try { new AppTwinStore(this).launch(serial); }
            catch (Exception e) { failed = true; }
            final boolean showError = failed;
            runOnUiThread(() -> {
                if (showError) Toast.makeText(this, R.string.app_twin_removed, Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }
}
