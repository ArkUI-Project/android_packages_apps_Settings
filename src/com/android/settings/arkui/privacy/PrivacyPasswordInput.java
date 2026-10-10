/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.app.PrivacyPasswordManager;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.settings.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;

/** Unsaved credential entry shared by screen-lock layouts and privacy verification dialogs. */
public final class PrivacyPasswordInput {
    private final TextInputLayout mLayout;
    private final TextInputEditText mInput;
    private int mType;

    public PrivacyPasswordInput(Context context, int type, Runnable submit) {
        this(new TextInputLayout(new ContextThemeWrapper(context,
                R.style.Theme_ArkUI_PrivacyPassword_Input)), null, type, submit);
        mLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
        float radius = 28 * context.getResources().getDisplayMetrics().density;
        mLayout.setBoxCornerRadii(radius, radius, radius, radius);
    }

    public PrivacyPasswordInput(TextInputLayout layout, TextInputEditText input, int type,
            Runnable submit) {
        mLayout = layout;
        mLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        mLayout.setSaveEnabled(false);
        mLayout.setFilterTouchesWhenObscured(true);
        mInput = input != null ? input : new TextInputEditText(layout.getContext());
        if (input == null) {
            mInput.setId(R.id.privacy_password_entry);
            mLayout.addView(mInput, new TextInputLayout.LayoutParams(-1, -2));
        }
        mInput.setSingleLine(true);
        mInput.setSaveEnabled(false);
        mInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        mInput.setFilterTouchesWhenObscured(true);
        mInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        mInput.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
        setType(type);
    }

    public View getView() { return mLayout; }

    public void setError(CharSequence error) { mLayout.setError(error); }

    public void setType(int type) {
        mType = type;
        boolean pin = type == PrivacyPasswordManager.TYPE_PIN;
        mInput.setInputType(pin ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(pin ? 6 : 64)});
        mLayout.setHint(pin ? R.string.arkui_privacy_password_pin_hint
                : R.string.arkui_privacy_password_custom_hint);
        clear();
    }

    public byte[] takeCredential() {
        byte[] bytes = mInput.getText().toString().getBytes(StandardCharsets.UTF_8);
        clear();
        return bytes;
    }

    public boolean isValidNewPassword() {
        String text = mInput.getText().toString();
        boolean valid = mType == PrivacyPasswordManager.TYPE_PIN && text.matches("[0-9]{6}");
        if (!valid) setError(mLayout.getContext().getString(R.string.arkui_privacy_password_pin_error));
        return valid;
    }

    public void clear() { mInput.setText(""); setError(null); }

    public void showKeyboard() {
        mInput.requestFocus();
        mInput.post(() -> mLayout.getContext().getSystemService(InputMethodManager.class)
                .showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT));
    }

    public void hideKeyboard() {
        mLayout.getContext().getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(mInput.getWindowToken(), 0);
    }

    public void setBusy(boolean busy) { mInput.setEnabled(!busy); mLayout.setEndIconVisible(!busy); }

    public void showFailure(long retryMillis) {
        setError(mLayout.getContext().getString(retryMillis > 0 ? R.string.arkui_privacy_password_retry
                        : R.string.arkui_privacy_password_wrong,
                Math.max(1, (retryMillis + 999) / 1000)));
    }
}
