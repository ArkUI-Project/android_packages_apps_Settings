/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.app.PrivacyPasswordManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.transition.TransitionManager;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.security.MessageDigest;
import java.util.Arrays;

/** MD3E setup, verification and management for the system's independent privacy credential. */
public final class PrivacyPasswordActivity extends FragmentActivity {
    private static final int VERIFY = 0;
    private static final int NEW = 1;
    private static final int CONFIRM = 2;
    private static final int MANAGE = 3;
    private final IBinder mOwner = new Binder();
    private PrivacyPasswordManager mManager;
    private IBinder mAuthorization;
    private ResultReceiver mCallback;
    private PrivacyPasswordInput mInput;
    private FrameLayout mInputContainer;
    private ViewGroup mContent;
    private TextView mTitle;
    private TextView mDescription;
    private MaterialButtonToggleGroup mTypes;
    private MaterialButton mPrimary;
    private MaterialButton mApps;
    private MaterialButton mDisable;
    private AlertDialog mDialog;
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
        setTheme(R.style.Theme_Settings_Expressive_NoActionBar);
        super.onCreate(state);
        getTheme().applyStyle(R.style.SettingsPreferenceTheme_Expressive, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setHideOverlayWindows(true);
        Utils.setupEdgeToEdge(this);
        setContentView(R.layout.arkui_privacy_password);
        setTitle(R.string.arkui_privacy_password_title);
        mManager = new PrivacyPasswordManager(this);
        mConfirmOnly = PrivacyPasswordManager.ACTION_CONFIRM.equals(getIntent().getAction());
        mCallback = getIntent().getParcelableExtra(PrivacyPasswordManager.EXTRA_CALLBACK,
                ResultReceiver.class);
        mContent = findViewById(R.id.credential_content);
        mTitle = findViewById(R.id.app_lock_message);
        mDescription = findViewById(R.id.app_lock_subtitle);
        ((ImageView) findViewById(R.id.app_lock_icon))
                .setImageResource(R.drawable.ic_settings_privacy_filled);
        mTypes = findViewById(R.id.privacy_type_group);
        mPrimary = findViewById(R.id.app_lock_verify);
        mPrimary.setId(R.id.privacy_primary);
        mApps = findViewById(R.id.privacy_apps);
        mDisable = findViewById(R.id.privacy_disable);
        mInput = new PrivacyPasswordInput(this, mType, this::submit);
        mInputContainer = findViewById(R.id.app_lock_password_container);
        mInputContainer.addView(mInput, new FrameLayout.LayoutParams(-1, -2));
        mPrimary.setOnClickListener(view -> submit());
        mApps.setOnClickListener(view -> startActivity(new Intent("org.arkui.settings.APP_LOCK")
                .setPackage(getPackageName())));
        mDisable.setOnClickListener(view -> confirmDisable());
        View cancel = findViewById(R.id.app_lock_cancel);
        cancel.setId(R.id.privacy_cancel);
        cancel.setOnClickListener(view -> cancelOrBack());
        mTypes.addOnButtonCheckedListener((group, checked, selected) -> {
            if (!selected || mStage != NEW || mBusy) return;
            mType = checked == R.id.privacy_pin_type ? PrivacyPasswordManager.TYPE_PIN
                    : PrivacyPasswordManager.TYPE_PASSWORD;
            mInput.setType(mType);
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { cancelOrBack(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        mVisible = true;
        if (mNeedsRefresh && !mCompleted) {
            mNeedsRefresh = false;
            refresh();
        }
    }

    @Override protected void onPause() {
        mVisible = false;
        ++mOperation;
        mBusy = false;
        mInput.clear();
        wipeNewPassword();
        endAuthorization();
        if (mDialog != null) { mDialog.dismiss(); mDialog = null; }
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
        if (mContent.isLaidOut()) {
            var transition = new MaterialSharedAxis(MaterialSharedAxis.X, stage != NEW);
            transition.setDuration(300);
            TransitionManager.beginDelayedTransition(mContent, transition);
        }
        mStage = stage;
        mTypes.setVisibility(stage == NEW ? View.VISIBLE : View.GONE);
        mTypes.check(mType == PrivacyPasswordManager.TYPE_PIN
                ? R.id.privacy_pin_type : R.id.privacy_custom_type);
        mInputContainer.setVisibility(stage == MANAGE ? View.GONE : View.VISIBLE);
        mInput.setType(mType);
        setBusy(false);
        mApps.setVisibility(stage == MANAGE ? View.VISIBLE : View.GONE);
        mDisable.setVisibility(stage == MANAGE ? View.VISIBLE : View.GONE);
        int title, description, primary;
        if (stage == VERIFY) {
            title = R.string.arkui_privacy_password_enter;
            description = R.string.arkui_privacy_password_verify_summary;
            primary = R.string.arkui_privacy_password_verify;
        } else if (stage == CONFIRM) {
            title = R.string.arkui_privacy_password_confirm;
            description = R.string.arkui_privacy_password_confirm_summary;
            primary = R.string.arkui_privacy_password_save;
        } else if (stage == MANAGE) {
            title = R.string.arkui_privacy_password_enabled;
            description = R.string.arkui_privacy_password_enabled_summary;
            primary = R.string.arkui_privacy_password_change;
        } else {
            title = R.string.arkui_privacy_password_create;
            description = R.string.arkui_privacy_password_intro;
            primary = R.string.arkui_privacy_password_continue;
        }
        mTitle.setText(title);
        mDescription.setText(description);
        mPrimary.setText(primary);
        if (stage == MANAGE) mInput.hideKeyboard();
        else mInput.post(() -> {
            if (mVisible && !mCompleted && mStage != MANAGE) mInput.showKeyboard();
        });
    }

    private void submit() {
        if (mBusy || !mVisible || mCompleted) return;
        if (mStage == MANAGE) { render(NEW); mInput.showKeyboard(); return; }
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
                if (mConfirmOnly) complete(true);
                else render(MANAGE);
            });
        });
    }

    private void confirmDisable() {
        if (mBusy || mStage != MANAGE || mAuthorization == null) return;
        mDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.arkui_privacy_password_disable)
                .setMessage(R.string.arkui_privacy_password_disable_summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.arkui_privacy_password_disable, (dialog, button) -> {
                    try {
                        mManager.clearPassword(UserHandle.myUserId(), mAuthorization);
                        mAuthorization = null;
                        toast(R.string.arkui_privacy_password_disabled);
                        render(NEW);
                    } catch (RuntimeException error) { unavailable(); }
                }).show();
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        mDialog.getWindow().setHideOverlayWindows(true);
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mInput.setBusy(busy);
        mPrimary.setEnabled(!busy);
        mTypes.setEnabled(!busy);
        for (int i = 0; i < mTypes.getChildCount(); i++) mTypes.getChildAt(i).setEnabled(!busy);
    }

    private void cancelOrBack() {
        if (mBusy) { complete(false); return; }
        if (!mConfirmOnly && mAuthorization != null && mStage != VERIFY && mStage != MANAGE) {
            wipeNewPassword(); render(MANAGE); return;
        }
        complete(false);
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
