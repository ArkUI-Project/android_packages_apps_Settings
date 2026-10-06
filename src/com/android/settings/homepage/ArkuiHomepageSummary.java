/*
 * Copyright (C) 2026 The ArkUI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.homepage;

import android.app.usage.StorageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.text.format.Formatter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.text.NumberFormat;

/** Binds the homepage overview to the device, battery and primary storage. */
final class ArkuiHomepageSummary implements DefaultLifecycleObserver {
    private final SettingsHomepageActivity mActivity;
    private boolean mStarted;
    private int mStorageGeneration;

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) {
                return;
            }
            final int percent = Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
            final TextView battery = mActivity.findViewById(R.id.arkui_home_battery);
            battery.setText(NumberFormat.getPercentInstance().format(percent / 100d));
            mActivity.findViewById(R.id.arkui_home_battery_card).setContentDescription(
                    mActivity.getString(R.string.power_usage_summary_title) + ", "
                            + battery.getText());
            ((ProgressBar) mActivity.findViewById(R.id.arkui_home_battery_progress))
                    .setProgress(percent);
        }
    };

    ArkuiHomepageSummary(SettingsHomepageActivity activity) {
        mActivity = activity;
        ((TextView) activity.findViewById(R.id.arkui_home_device_model)).setText(Build.MODEL);
        ((TextView) activity.findViewById(R.id.arkui_home_android_version))
                .setText(Build.VERSION.RELEASE);
        bindDestination(R.id.arkui_home_device_card, Settings.MyDeviceInfoActivity.class);
        bindDestination(R.id.arkui_home_battery_card, Settings.PowerUsageSummaryActivity.class);
        bindDestination(R.id.arkui_home_android_card, Settings.FirmwareVersionActivity.class);
        bindDestination(R.id.arkui_home_storage_card, Settings.StorageDashboardActivity.class);
    }

    private void bindDestination(int viewId, Class<?> destination) {
        mActivity.findViewById(viewId).setOnClickListener(view -> {
            ActivityEmbeddingRulesController.registerSubSettingsPairRule(mActivity, true);
            mActivity.startActivity(new Intent(mActivity, destination)
                    .putExtra(SettingsHomepageActivity.EXTRA_IS_FROM_SETTINGS_HOMEPAGE, true));
        });
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        mStarted = true;
        mActivity.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        updateStorage();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        mStarted = false;
        mActivity.unregisterReceiver(mBatteryReceiver);
    }

    private void updateStorage() {
        final int generation = ++mStorageGeneration;
        ThreadUtils.postOnBackgroundThread(() -> {
            final StatFs data = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = data.getTotalBytes();
            long free = data.getAvailableBytes();
            final StorageStatsManager storage =
                    mActivity.getSystemService(StorageStatsManager.class);
            if (storage != null) {
                try {
                    total = storage.getTotalBytes(StorageManager.UUID_DEFAULT);
                    free = storage.getFreeBytes(StorageManager.UUID_DEFAULT);
                } catch (IOException | SecurityException ignored) {
                    // Keep the mounted filesystem values when storage statistics are unavailable.
                }
            }
            final long totalBytes = total;
            final long usedBytes = Math.max(0, Math.min(total, total - free));
            ThreadUtils.postOnMainThread(() -> {
                if (!mStarted || generation != mStorageGeneration) {
                    return;
                }
                ((TextView) mActivity.findViewById(R.id.arkui_home_storage)).setText(
                        mActivity.getString(R.string.arkui_home_storage_usage,
                                Formatter.formatShortFileSize(mActivity, usedBytes),
                                Formatter.formatShortFileSize(mActivity, totalBytes)));
                ((ProgressBar) mActivity.findViewById(R.id.arkui_home_storage_progress))
                        .setProgress(totalBytes > 0
                                ? (int) Math.round(usedBytes * 100d / totalBytes) : 0);
            });
        });
    }
}
