/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.personalization;

import static com.android.internal.arkui.CompatibilitySettings.*;
import android.os.UserManager;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ArkuiCompatibilitySettings extends PersonalizationPreferenceFragment {
    @Override protected int resource() { return R.xml.arkui_compatibility_settings; }
    @Override protected void configure() {
        bind(IGNORE_SECURE_FLAG, Table.SECURE, 0);
        bind(BLOCK_SCREENSHOT_DETECTION, Table.SECURE, 0);
        bind(DISABLE_UPDATE_SIGNATURE_CHECK, Table.GLOBAL, 0);
        bind(DISABLE_APK_SIGNATURE_VERIFICATION, Table.GLOBAL, 0);
        bind(ALLOW_APP_DOWNGRADE, Table.GLOBAL, 0);
    }
    @Override protected void afterRefresh() {
        final boolean owner = requireContext().getUserId() == 0
                && requireContext().getSystemService(UserManager.class).isAdminUser();
        findPreference(DISABLE_UPDATE_SIGNATURE_CHECK).setEnabled(owner);
        findPreference(DISABLE_APK_SIGNATURE_VERIFICATION).setEnabled(owner);
        findPreference(ALLOW_APP_DOWNGRADE).setEnabled(owner);
        findPreference("arkui_compatibility_owner_note").setVisible(!owner);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_compatibility_settings);
}
