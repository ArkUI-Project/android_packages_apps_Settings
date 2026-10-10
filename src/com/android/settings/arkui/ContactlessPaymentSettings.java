/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui;

import android.content.pm.PackageManager;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public final class ContactlessPaymentSettings extends ArkuiFeatureToggleFragment {
    @Override protected int resource() { return R.xml.arkui_nfc_contactless_payment; }
    @Override protected String setting() { return "arkui_nfc_contactless_payment"; }
    @Override protected boolean supported() {
        return requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_nfc_contactless_payment);
}
