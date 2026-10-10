/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deliberately narrow built-in rules. No generic close/skip or coordinate tapping. */
final class AdSkipMatcher {
    private static final Pattern SPACE = Pattern.compile("[\\s\\u00a0\\u200b\\u3000]+");
    private static final String NUMBER = "[()（）|·:：-]*[0-9]{1,2}(?:s|秒|sec|seconds?)?[()（）|·:：-]*";
    private static final String SKIP = "(?:(?:跳[过過]|略過)(?:广告|廣告)?|skip(?:this)?(?:ads?|advertisement)?)";
    private static final Pattern LABEL = Pattern.compile("^(?:" + NUMBER + ")?" + SKIP
            + "(?:" + NUMBER + ")?$");
    private static final Pattern EXPLICIT = Pattern.compile(
            "(?:跳[过過]|略過)(?:广告|廣告)|skip(?:this)?(?:ads?|advertisement)");
    private static final Pattern AD_ID = Pattern.compile(
            "(?:.*[_-])?(?:ad[_-]?skip|skip[_-]?ad|splash[_-]?.*skip|skip[_-]?.*splash)"
                    + "(?:[_-]?(?:btn|button|text|view|tv|container))?");
    private static final Pattern AD_LABEL = Pattern.compile("^(?:广告|廣告|ad|advertisement)$");

    private AdSkipMatcher() { }

    static String normalize(CharSequence value) {
        if (value == null || value.length() > 80) return "";
        return SPACE.matcher(value.toString().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    static boolean isAdMarker(CharSequence value) {
        return AD_LABEL.matcher(normalize(value)).matches();
    }

    static boolean matches(CharSequence text, CharSequence description, String id,
            boolean adMarker) {
        String resource = id == null ? "" : id.substring(id.lastIndexOf('/') + 1)
                .toLowerCase(Locale.ROOT);
        boolean adId = resource.length() <= 100 && AD_ID.matcher(resource).matches();
        return matchesLabel(normalize(text), adId, adMarker)
                || matchesLabel(normalize(description), adId, adMarker);
    }

    private static boolean matchesLabel(String label, boolean adId, boolean adMarker) {
        if (label.isEmpty() || !LABEL.matcher(label).matches()) return false;
        return EXPLICIT.matcher(label).find() || label.matches(".*[0-9].*")
                || adId || adMarker;
    }

    static boolean isCorner(int left, int top, int right, int bottom, int width, int height) {
        return width > 0 && height > 0 && left >= 0 && top >= 0 && right <= width
                && bottom <= height && right > left && bottom > top
                && right - left <= width * 0.45f && bottom - top <= height * 0.14f
                && bottom <= height * 0.35f
                && (left >= width * 0.60f || right <= width * 0.30f);
    }
}
