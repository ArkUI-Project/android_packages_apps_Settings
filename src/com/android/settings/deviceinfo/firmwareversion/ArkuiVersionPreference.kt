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

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.contract.TAG_DEVICE_STATE_PREFERENCE
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding

class ArkuiVersionPreference : PreferenceMetadata, PreferenceBinding {

    override val key: String
        get() = "arkui_version"

    override val title: Int
        get() = R.string.arkui_version_title

    override val summary: Int
        get() = R.string.arkui_version_name

    override val indexable
        get() = false

    override fun tags(context: Context) = arrayOf(TAG_DEVICE_STATE_PREFERENCE)

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isSelectable = false
        preference.isCopyingEnabled = true
    }
}
