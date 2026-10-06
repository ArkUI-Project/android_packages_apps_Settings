/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.privacy;

import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** A trusted password prompt; it never treats an activity result as app-lock authorization. */
public final class PrivacyPasswordDialog {
    private PrivacyPasswordDialog() { }

    public static AlertDialog show(Context context, int type, Function<byte[], Bundle> verify,
            Consumer<Bundle> success, Runnable cancel) {
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean busy = new AtomicBoolean();
        AlertDialog[] dialog = new AlertDialog[1];
        PrivacyPasswordInput[] input = new PrivacyPasswordInput[1];
        Runnable submit = () -> {
            if (finished.get() || !busy.compareAndSet(false, true)) return;
            byte[] password = input[0].takeCredential();
            input[0].setBusy(true);
            dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            ThreadUtils.postOnBackgroundThread(() -> {
                Bundle result;
                try { result = verify.apply(password); }
                catch (RuntimeException error) { result = null; }
                finally { Arrays.fill(password, (byte) 0); }
                final Bundle response = result;
                ThreadUtils.postOnMainThread(() -> {
                    if (finished.get()) return;
                    busy.set(false);
                    input[0].setBusy(false);
                    dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (response == null) {
                        if (finished.compareAndSet(false, true)) cancel.run();
                        dialog[0].dismiss();
                    } else if (response.getBoolean(android.app.PrivacyPasswordManager.KEY_MATCHED)) {
                        finished.set(true);
                        input[0].hideKeyboard();
                        dialog[0].dismiss();
                        success.accept(response);
                    } else {
                        input[0].showFailure(response.getLong(
                                android.app.PrivacyPasswordManager.KEY_RETRY_MILLIS));
                    }
                });
            });
        };
        input[0] = new PrivacyPasswordInput(context, type, submit);
        LinearLayout container = new LinearLayout(context);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input[0], new LinearLayout.LayoutParams(-1, -2));
        dialog[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.arkui_privacy_password_enter)
                .setMessage(R.string.arkui_privacy_password_verify_summary)
                .setView(container)
                .setPositiveButton(R.string.arkui_privacy_password_verify, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog[0].setCanceledOnTouchOutside(false);
        dialog[0].setOnDismissListener(value -> {
            input[0].clear();
            if (finished.compareAndSet(false, true)) cancel.run();
        });
        dialog[0].setOnShowListener(value -> {
            dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> submit.run());
            dialog[0].getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog[0].getWindow().setHideOverlayWindows(true);
            input[0].showKeyboard();
        });
        dialog[0].show();
        return dialog[0];
    }
}
