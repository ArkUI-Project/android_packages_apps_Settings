/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.applock;

import android.app.AppLockManager;
import android.app.PrivacyPasswordManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.arkui.privacy.PrivacyPasswordInput;
import com.android.settingslib.utils.ThreadUtils;

import java.util.Arrays;

/** Redeems a system-owned, single-use app-lock challenge only after real authentication. */
public final class AppLockConfirmActivity extends FragmentActivity {
    public static final String EXTRA_MANAGEMENT_TOKEN =
            "android.app.extra.APP_LOCK_MANAGEMENT_TOKEN";
    private static final String TAG = "AppLockConfirm";

    private AppLockManager mManager;
    private IBinder mChallenge;
    private CancellationSignal mCancellation;
    private boolean mManagement;
    private boolean mCompleted;
    private int mUserId;
    private CharSequence mLabel;
    private int mAuthenticationGeneration;
    private int mAuthMethod;
    private PrivacyPasswordInput mPasswordInput;
    private boolean mPasswordVerifying;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_Settings_Expressive_NoActionBar);
        super.onCreate(state);
        getTheme().applyStyle(R.style.SettingsPreferenceTheme_Expressive, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setHideOverlayWindows(true);
        Utils.setupEdgeToEdge(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { cancel(); }
        });
        mManager = new AppLockManager(this);
        showChallenge(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle extras = intent.getExtras();
        IBinder challenge = extras == null ? null : extras.getBinder(AppLockManager.EXTRA_CHALLENGE);
        if (challenge == null || mCompleted || isFinishing()) return;
        if (challenge == mChallenge) {
            // A repeated launch only brings this challenge's existing task forward. The
            // credential fallback may be above us; restarting its prompt would cancel it.
            setIntent(intent);
            return;
        }
        try {
            // Reject invalid intents before disturbing the ongoing, trusted prompt.
            mManager.getAuthenticationInfo(challenge);
        } catch (RuntimeException e) {
            Log.w(TAG, "Ignoring an unavailable replacement challenge", e);
            return;
        }
        ++mAuthenticationGeneration;
        mPasswordVerifying = false;
        if (mPasswordInput != null) mPasswordInput.clear();
        if (mCancellation != null) { mCancellation.cancel(); mCancellation = null; }
        try { mManager.cancelAuthentication(mChallenge); }
        catch (RuntimeException e) { Log.w(TAG, "Previous challenge already unavailable", e); }
        setIntent(intent);
        showChallenge(intent);
    }

    private void showChallenge(Intent intent) {
        Bundle extras = intent.getExtras();
        mChallenge = extras == null ? null : extras.getBinder(AppLockManager.EXTRA_CHALLENGE);
        if (mChallenge == null) { finish(); return; }
        try {
            Bundle info = mManager.getAuthenticationInfo(mChallenge);
            if (info == null) { finish(); return; }
            mUserId = info.getInt(AppLockManager.KEY_USER_ID, -1);
            mManagement = info.getBoolean(AppLockManager.KEY_MANAGEMENT);
            mAuthMethod = info.getInt(AppLockManager.KEY_AUTH_METHOD);
            if (mUserId < 0) { cancel(); return; }
            String packageName = info.getString(AppLockManager.KEY_PACKAGE_NAME);
            mLabel = getString(R.string.arkui_app_lock_title);
            setContentView(R.layout.arkui_app_lock_confirm);
            if (!mManagement && packageName != null) {
                mLabel = packageName;
                try {
                    var app = getPackageManager().getApplicationInfoAsUser(packageName, 0, mUserId);
                    mLabel = app.loadLabel(getPackageManager());
                    ((ImageView) findViewById(R.id.app_lock_icon))
                            .setImageDrawable(app.loadIcon(getPackageManager()));
                } catch (PackageManager.NameNotFoundException ignored) {
                    // A package can disappear while its launch challenge is pending.
                }
            }
            setTitle(R.string.arkui_app_lock_title);
            ((TextView) findViewById(R.id.app_lock_message)).setText(mManagement
                    ? getString(R.string.arkui_app_lock_manage_subtitle)
                    : getString(R.string.arkui_app_lock_unlock_title, mLabel));
            ((TextView) findViewById(R.id.app_lock_subtitle)).setText(mManagement
                    ? R.string.arkui_app_lock_auth_description
                    : R.string.arkui_app_lock_unlock_subtitle);
            findViewById(R.id.app_lock_verify).setOnClickListener(v -> authenticate());
            findViewById(R.id.app_lock_cancel).setOnClickListener(v -> cancel());
            if (mAuthMethod == AppLockManager.AUTH_PRIVACY_PASSWORD) {
                ((TextView) findViewById(R.id.app_lock_subtitle))
                        .setText(R.string.arkui_privacy_password_verify_summary);
                FrameLayout container = findViewById(R.id.app_lock_password_container);
                container.setVisibility(View.VISIBLE);
                mPasswordInput = new PrivacyPasswordInput(this,
                        info.getInt(PrivacyPasswordManager.KEY_TYPE), this::authenticate);
                container.addView(mPasswordInput.getView(), new FrameLayout.LayoutParams(-1, -2));
                mPasswordInput.getView().post(mPasswordInput::showKeyboard);
            } else {
                mPasswordInput = null;
                findViewById(R.id.app_lock_verify).post(this::authenticate);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "App-lock challenge is no longer available", e);
            cancel();
        }
    }

    private void authenticate() {
        if (mCompleted || isFinishing() || mCancellation != null) return;
        if (mAuthMethod == AppLockManager.AUTH_PRIVACY_PASSWORD) {
            verifyPrivacyPassword();
            return;
        }
        try {
            // Recheck the challenge after returning from screen lock, user switching, or sleep.
            if (mManager.getAuthenticationInfo(mChallenge) == null) { cancel(); return; }
            final int generation = ++mAuthenticationGeneration;
            final IBinder challenge = mChallenge;
            mCancellation = new CancellationSignal();
            new BiometricPrompt.Builder(this)
                    .setTitle(getString(R.string.arkui_app_lock_auth_title))
                    .setSubtitle(mManagement ? getString(R.string.arkui_app_lock_manage_subtitle)
                            : getString(R.string.arkui_app_lock_unlock_title, mLabel))
                    .setDescription(getString(R.string.arkui_app_lock_auth_description))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticateUser(mCancellation, getMainExecutor(),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override public void onAuthenticationSucceeded(
                                        BiometricPrompt.AuthenticationResult result) {
                                    if (mCompleted || isFinishing()
                                            || generation != mAuthenticationGeneration
                                            || challenge != mChallenge) return;
                                    try {
                                        IBinder token = mManager.completeAuthentication(challenge);
                                        if (mManagement && token == null) { cancel(); return; }
                                        mCompleted = true;
                                        mCancellation = null;
                                        if (mManagement) {
                                            Bundle resultExtras = new Bundle();
                                            resultExtras.putBinder(EXTRA_MANAGEMENT_TOKEN, token);
                                            setResult(RESULT_OK, new Intent().putExtras(resultExtras));
                                        }
                                        // The system resumes its own saved task/IntentSender.
                                        // No application-supplied destination is trusted here.
                                        finish();
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "App-lock authentication expired", e);
                                        cancel();
                                    }
                                }

                                @Override public void onAuthenticationError(int errorCode,
                                        CharSequence errString) {
                                    if (mCompleted || isFinishing()
                                            || generation != mAuthenticationGeneration
                                            || challenge != mChallenge) return;
                                    mCancellation = null;
                                    // A newer app's prompt can replace this one. Tear down only
                                    // our own challenge; sending Home would cover that new app.
                                    cancel(errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED);
                                }
                            }, mUserId);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not authenticate app-lock challenge", e);
            Toast.makeText(this, R.string.arkui_app_lock_session_expired, Toast.LENGTH_SHORT).show();
            cancel();
        }
    }

    private void verifyPrivacyPassword() {
        if (mPasswordVerifying || mPasswordInput == null) return;
        final IBinder challenge = mChallenge;
        final int generation = ++mAuthenticationGeneration;
        byte[] password = mPasswordInput.takeCredential();
        mPasswordVerifying = true;
        mPasswordInput.setBusy(true);
        findViewById(R.id.app_lock_verify).setEnabled(false);
        ThreadUtils.postOnBackgroundThread(() -> {
            Bundle result;
            try { result = mManager.verifyPrivacyPassword(challenge, password); }
            catch (RuntimeException error) { result = null; }
            finally { Arrays.fill(password, (byte) 0); }
            final Bundle response = result;
            ThreadUtils.postOnMainThread(() -> {
                if (mCompleted || isFinishing() || generation != mAuthenticationGeneration
                        || challenge != mChallenge) return;
                mPasswordVerifying = false;
                mPasswordInput.setBusy(false);
                findViewById(R.id.app_lock_verify).setEnabled(true);
                if (response == null) { cancel(); return; }
                if (!response.getBoolean(PrivacyPasswordManager.KEY_MATCHED)) {
                    mPasswordInput.showFailure(response.getLong(PrivacyPasswordManager.KEY_RETRY_MILLIS));
                    return;
                }
                try {
                    IBinder token = mManager.completeAuthentication(challenge);
                    if (mManagement && token == null) { cancel(); return; }
                    mCompleted = true;
                    mPasswordInput.hideKeyboard();
                    mPasswordInput.clear();
                    if (mManagement) {
                        Bundle extras = new Bundle();
                        extras.putBinder(EXTRA_MANAGEMENT_TOKEN, token);
                        setResult(RESULT_OK, new Intent().putExtras(extras));
                    }
                    finish();
                } catch (RuntimeException error) { cancel(); }
            });
        });
    }

    @Override protected void onStop() {
        if (mAuthMethod == AppLockManager.AUTH_PRIVACY_PASSWORD && !mCompleted) cancel(false);
        super.onStop();
    }

    private void cancel() {
        cancel(true);
    }

    private void cancel(boolean navigateHome) {
        if (mCompleted) return;
        mCompleted = true;
        ++mAuthenticationGeneration;
        if (mPasswordInput != null) { mPasswordInput.clear(); mPasswordInput.hideKeyboard(); }
        if (mCancellation != null) {
            mCancellation.cancel();
            mCancellation = null;
        }
        if (mChallenge != null && mManager != null) {
            try { mManager.cancelAuthentication(mChallenge); }
            catch (RuntimeException e) { Log.w(TAG, "Challenge already canceled", e); }
        }
        setResult(RESULT_CANCELED);
        if (!mManagement && navigateHome) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    @Override public boolean onNavigateUp() { cancel(); return true; }

    @Override public void onDestroy() {
        if (!mCompleted) cancel(false);
        super.onDestroy();
    }
}
