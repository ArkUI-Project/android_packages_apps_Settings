/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.app.PrivacyPasswordManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ThemeHelper;

import java.security.MessageDigest;
import java.util.Arrays;

/** Uses the screen-lock credential layout to set or verify the independent privacy PIN. */
public final class PrivacyPasswordActivity extends FragmentActivity {
    public static final String ACTION_SET_PASSWORD = "org.arkui.settings.SET_PRIVACY_PASSWORD";
    private static final int VERIFY = 0;
    private static final int NEW = 1;
    private static final int CONFIRM = 2;
    private final IBinder mOwner = new Binder();
    private PrivacyPasswordManager mManager;
    private IBinder mAuthorization;
    private ResultReceiver mCallback;
    private PrivacyPasswordInput mInput;
    private GlifLayout mLayout;
    private TextView mDescription;
    private FooterButton mPrimary;
    private FooterButton mCancel;
    private byte[] mNewPassword;
    private int mType = PrivacyPasswordManager.TYPE_PIN;
    private int mStage;
    private int mOperation;
    private boolean mConfirmOnly;
    private boolean mCompleted;
    private boolean mBusy;
    private boolean mVisible;
    private boolean mNeedsRefresh = true;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.GlifV4Theme_DayNight);
        ThemeHelper.trySetDynamicColor(this);
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext())) {
            ThemeHelper.trySetSuwTheme(this);
        }
        super.onCreate(state);
        mConfirmOnly = PrivacyPasswordManager.ACTION_CONFIRM.equals(getIntent().getAction());
        if (!mConfirmOnly && !ACTION_SET_PASSWORD.equals(getIntent().getAction())) {
            // Keep existing explicit links compatible while using the standard Settings host.
            startActivity(new Intent(this, Settings.PrivacyPasswordSettingsActivity.class));
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setHideOverlayWindows(true);
        setContentView(R.layout.choose_lock_password_expressive);
        setTitle(R.string.arkui_privacy_password_title);
        mManager = new PrivacyPasswordManager(this);
        if (mConfirmOnly) {
            mCallback = getIntent().getParcelableExtra(PrivacyPasswordManager.EXTRA_CALLBACK,
                    ResultReceiver.class);
        }
        mLayout = findViewById(R.id.setup_wizard_layout);
        mLayout.setIcon(getDrawable(R.drawable.ic_settings_privacy_filled));
        mLayout.setFilterTouchesWhenObscured(true);
        mDescription = findViewById(R.id.sud_layout_description);
        ((ViewGroup) findViewById(R.id.password_container))
                .setOpticalInsets(android.graphics.Insets.NONE);
        mInput = new PrivacyPasswordInput(findViewById(R.id.password_entry_layout),
                findViewById(R.id.password_entry), mType, this::submit);
        FooterBarMixin footer = mLayout.getMixin(FooterBarMixin.class);
        footer.setPrimaryButton(new FooterButton.Builder(this)
                .setText(R.string.arkui_privacy_password_continue)
                .setListener(view -> submit())
                .setButtonType(FooterButton.ButtonType.NEXT)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary).build());
        footer.setSecondaryButton(new FooterButton.Builder(this)
                .setText(android.R.string.cancel)
                .setListener(view -> complete(false))
                .setButtonType(FooterButton.ButtonType.CANCEL)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Secondary).build());
        mPrimary = footer.getPrimaryButton();
        mCancel = footer.getSecondaryButton();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { complete(false); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        mVisible = true;
        if (mInput != null && mNeedsRefresh && !mCompleted) {
            mNeedsRefresh = false;
            refresh();
        }
    }

    @Override protected void onPause() {
        mVisible = false;
        ++mOperation;
        mBusy = false;
        if (mInput != null) mInput.clear();
        wipeNewPassword();
        endAuthorization();
        mNeedsRefresh = true;
        if (mConfirmOnly && !mCompleted) complete(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        endAuthorization();
        wipeNewPassword();
        if (mCallback != null && !mCompleted) complete(false);
        super.onDestroy();
    }

    private void refresh() {
        try {
            Bundle state = mManager.getState(UserHandle.myUserId());
            if (state.getBoolean(PrivacyPasswordManager.KEY_CONFIGURED)) {
                mType = state.getInt(PrivacyPasswordManager.KEY_TYPE);
                mAuthorization = mManager.beginAuthentication(UserHandle.myUserId(), mOwner);
                render(VERIFY);
                long retry = state.getLong(PrivacyPasswordManager.KEY_RETRY_MILLIS);
                if (retry > 0) mInput.showFailure(retry);
            } else {
                mType = PrivacyPasswordManager.TYPE_PIN;
                render(NEW);
            }
        } catch (RuntimeException error) { unavailable(); }
    }

    private void render(int stage) {
        mStage = stage;
        // A legacy custom password can still be verified, but every new credential is a PIN.
        if (stage != VERIFY) mType = PrivacyPasswordManager.TYPE_PIN;
        mInput.setType(mType);
        setBusy(false);
        int title, description, primary;
        if (stage == VERIFY) {
            title = R.string.arkui_privacy_password_enter;
            description = R.string.arkui_privacy_password_verify_summary;
            primary = R.string.arkui_privacy_password_verify;
        } else if (stage == CONFIRM) {
            title = R.string.arkui_privacy_password_confirm;
            description = R.string.arkui_privacy_password_confirm_summary;
            primary = R.string.arkui_privacy_password_save;
        } else {
            title = R.string.arkui_privacy_password_create;
            description = R.string.arkui_privacy_password_intro;
            primary = R.string.arkui_privacy_password_continue;
        }
        mLayout.setHeaderText(title);
        mDescription.setText(description);
        mPrimary.setText(this, primary);
        mInput.getView().post(() -> {
            if (mVisible && !mCompleted) mInput.showKeyboard();
        });
    }

    private void submit() {
        if (mBusy || !mVisible || mCompleted) return;
        if (mStage == NEW) {
            if (!mInput.isValidNewPassword()) return;
            wipeNewPassword();
            mNewPassword = mInput.takeCredential();
            render(CONFIRM);
            mInput.showKeyboard();
            return;
        }
        if (mStage == CONFIRM) {
            byte[] confirmation = mInput.takeCredential();
            boolean matches = mNewPassword != null && MessageDigest.isEqual(mNewPassword, confirmation);
            Arrays.fill(confirmation, (byte) 0);
            if (!matches) {
                wipeNewPassword();
                render(NEW);
                mInput.setError(getString(R.string.arkui_privacy_password_mismatch));
                return;
            }
        }
        final boolean verifying = mStage == VERIFY;
        final byte[] password = verifying ? mInput.takeCredential() : mNewPassword;
        mNewPassword = null;
        final IBinder authorization = mAuthorization;
        final int type = mType;
        final int operation = ++mOperation;
        setBusy(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            Bundle result = null;
            IBinder updated = null;
            try {
                if (verifying) result = mManager.verify(authorization, password);
                else updated = mManager.setPassword(UserHandle.myUserId(), mOwner, authorization,
                        type, password);
            } catch (RuntimeException ignored) { }
            finally { if (password != null) Arrays.fill(password, (byte) 0); }
            final Bundle response = result;
            final IBinder session = updated;
            ThreadUtils.postOnMainThread(() -> {
                if (!mVisible || operation != mOperation || mCompleted) {
                    if (session != null) mManager.endAuthentication(session);
                    return;
                }
                setBusy(false);
                if (verifying && response != null) {
                    if (!response.getBoolean(PrivacyPasswordManager.KEY_MATCHED)) {
                        mInput.showFailure(response.getLong(PrivacyPasswordManager.KEY_RETRY_MILLIS));
                        return;
                    }
                    mAuthorization = response.getBinder(PrivacyPasswordManager.KEY_TOKEN);
                } else if (session != null) {
                    mAuthorization = session;
                    toast(R.string.arkui_privacy_password_saved);
                } else { unavailable(); return; }
                if (verifying && !mConfirmOnly) render(NEW);
                else complete(true);
            });
        });
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mInput.setBusy(busy);
        mPrimary.setEnabled(!busy);
        mCancel.setEnabled(!busy);
    }

    private void complete(boolean success) {
        if (mCompleted) return;
        mCompleted = true;
        mInput.hideKeyboard();
        mInput.clear();
        // The callback contains only a result. Credential-management capabilities stay in Settings.
        if (mCallback != null) mCallback.send(success ? RESULT_OK : RESULT_CANCELED, new Bundle());
        setResult(success ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    private void endAuthorization() {
        if (mAuthorization == null || mManager == null) return;
        try { mManager.endAuthentication(mAuthorization); }
        catch (RuntimeException ignored) { }
        mAuthorization = null;
    }

    private void wipeNewPassword() {
        if (mNewPassword != null) Arrays.fill(mNewPassword, (byte) 0);
        mNewPassword = null;
    }

    private void unavailable() { toast(R.string.arkui_privacy_password_unavailable); complete(false); }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
