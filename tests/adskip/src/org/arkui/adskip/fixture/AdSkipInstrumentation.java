/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package org.arkui.adskip.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** Short end-to-end checks with the actual built-in service, preserving user configuration. */
public final class AdSkipInstrumentation extends Instrumentation {
    private static final String SERVICE = "com.android.settings/.arkui.adskip.AdSkipService";
    private static final String EXCLUDED = "arkui_ad_skip_excluded_packages";
    private static final String COUNT = "arkui_ad_skip_count";
    private final List<String> mPassed = new ArrayList<>();

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        ContentResolver resolver = getTargetContext().getContentResolver();
        String services = Settings.Secure.getString(resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String excluded = Settings.Secure.getString(resolver, EXCLUDED);
        String count = Settings.Secure.getString(resolver, COUNT);
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            setEnabled(true, services);
            Settings.Secure.putString(resolver, EXCLUDED, "");
            SystemClock.sleep(1200);
            check("Chinese countdown", "跳过 5 秒", false, false, 0, true, 3000);
            check("English accessible parent", "Skip ad", true, false, 500, true, 3000);
            check("Ordinary skip stays untouched", "跳过", false, false, 0, false, 1200);
            check("Center content stays untouched", "Skip ad", false, true, 0, false, 1200);
            Settings.Secure.putString(resolver, EXCLUDED, getTargetContext().getPackageName());
            SystemClock.sleep(300);
            check("Excluded app", "跳过广告", false, false, 0, false, 1200);
            Settings.Secure.putString(resolver, EXCLUDED, "");
            check("Late button stays untouched", "Skip ad", false, false, 10_700, false, 12_000);
            setEnabled(false, services);
            SystemClock.sleep(500);
            check("Disabled service", "跳过广告", false, false, 0, false, 1200);
            result.putString("stream", "PASS: " + String.join(", ", mPassed) + "\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "FAIL after " + mPassed + ": " + error + "\n");
        } finally {
            Settings.Secure.putString(resolver, EXCLUDED, excluded);
            Settings.Secure.putString(resolver, COUNT, count);
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services);
        }
        finish(status, result);
    }

    private void setEnabled(boolean enabled, String previous) {
        String retained = previous == null ? "" : previous.replace(SERVICE, "")
                .replace("com.android.settings/com.android.settings.arkui.adskip.AdSkipService", "");
        String joined = enabled ? retained + ":" + SERVICE : retained;
        Settings.Secure.putString(getTargetContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, joined);
    }

    private void check(String name, String label, boolean parent, boolean center, long delay,
            boolean expected, long wait) {
        getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(350);
        AdActivity activity = (AdActivity) startActivitySync(new Intent(getTargetContext(), AdActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("label", label).putExtra("parent", parent).putExtra("center", center)
                .putExtra("delay", delay));
        long deadline = SystemClock.uptimeMillis() + wait;
        while (SystemClock.uptimeMillis() < deadline) {
            if (expected && activity.clicked) break;
            SystemClock.sleep(100);
        }
        boolean actual = activity.clicked;
        runOnMainSync(activity::finish);
        if (actual != expected) throw new AssertionError(name + ": clicked=" + actual);
        mPassed.add(name);
    }
}
