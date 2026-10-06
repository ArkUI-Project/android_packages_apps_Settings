/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.spa.app.appinfo

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.internal.arkui.MotionSensorSettings
import com.android.settings.R
import com.android.settings.applications.appinfo.AppInfoDashboardFragment
import com.android.settings.arkui.ArkuiMotionSensorSettings
import com.android.settings.core.SubSettingLauncher
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel

/** Motion access entry for the current Compose app-information page. */
@Composable
fun ArkuiMotionSensorPreference(app: ApplicationInfo) {
    if (UserHandle.getAppId(app.uid) < Process.FIRST_APPLICATION_UID) return
    val context = LocalContext.current
    val resolver = context.contentResolver
    val userId = UserHandle.getUserId(app.uid)
    fun readMode() = MotionSensorSettings.getMode(resolver, app.packageName, userId)
    var mode by remember(app.uid, app.packageName) { mutableIntStateOf(readMode()) }
    DisposableEffect(app.uid, app.packageName, resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { mode = readMode() }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(MotionSensorSettings.KEY),
            false, observer, userId)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    Preference(object : PreferenceModel {
        override val title = context.getString(R.string.arkui_motion_sensor_title)
        override val summary = {
            context.getString(when (mode) {
                MotionSensorSettings.DENY -> R.string.arkui_motion_sensor_deny
                MotionSensorSettings.BLOCK_ON_OPEN -> R.string.arkui_motion_sensor_open
                else -> R.string.arkui_motion_sensor_allow
            })
        }
        override val onClick = {
            val arguments = Bundle().apply {
                putString(AppInfoDashboardFragment.ARG_PACKAGE_NAME, app.packageName)
                putInt(AppInfoDashboardFragment.ARG_PACKAGE_UID, app.uid)
            }
            SubSettingLauncher(context)
                .setDestination(ArkuiMotionSensorSettings::class.java.name)
                .setArguments(arguments)
                .setTitleRes(R.string.arkui_motion_sensor_title)
                .setSourceMetricsCategory(AppInfoSettingsProvider.METRICS_CATEGORY)
                .launch()
        }
    })
}
