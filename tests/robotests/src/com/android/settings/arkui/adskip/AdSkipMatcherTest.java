/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdSkipMatcherTest {
    @Test public void openingAds_matchChineseEnglishAndDescriptions() {
        assertTrue(matches("跳过 5 秒"));
        assertTrue(matches("5s | 跳過"));
        assertTrue(matches("略過廣告"));
        assertTrue(matches("Skip ad"));
        assertTrue(matches("Skip (3s)"));
        assertTrue(AdSkipMatcher.matches(null, "跳过广告", null, false));
        assertTrue(AdSkipMatcher.matches("跳过", null, "app:id/splash_skip", false));
        assertTrue(AdSkipMatcher.matches("Skip", null, null, true));
    }

    @Test public void ordinaryControls_andLongContent_doNotMatch() {
        assertFalse(matches("跳过"));
        assertFalse(matches("Skip"));
        assertFalse(matches("关闭"));
        assertFalse(matches("跳过登录"));
        assertFalse(matches("Skip tutorial"));
        assertFalse(AdSkipMatcher.matches("跳过", null, "app:id/download_skip", false));
        assertFalse(matches("Read about how to skip ads"));
        assertFalse(AdSkipMatcher.matches("跳过登录", null, "app:id/ad_skip", true));
        assertFalse(AdSkipMatcher.isAdMarker("广告设置"));
        assertFalse(AdSkipMatcher.isAdMarker("Read this ad"));
        assertTrue(AdSkipMatcher.isAdMarker("廣告"));
    }

    @Test public void cornerRule_rejectsContentAndOversizedParents() {
        assertTrue(AdSkipMatcher.isCorner(800, 60, 1030, 160, 1080, 2400));
        assertTrue(AdSkipMatcher.isCorner(30, 60, 260, 160, 1080, 2400));
        assertFalse(AdSkipMatcher.isCorner(420, 60, 650, 160, 1080, 2400));
        assertFalse(AdSkipMatcher.isCorner(800, 1800, 1030, 1900, 1080, 2400));
        assertFalse(AdSkipMatcher.isCorner(0, 0, 1080, 2400, 1080, 2400));
        assertFalse(AdSkipMatcher.isCorner(-20, 50, 100, 150, 1080, 2400));
    }

    private static boolean matches(String text) {
        return AdSkipMatcher.matches(text, null, null, false);
    }
}
