/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.core.SettingsBaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

/** Explicit, user-selected sharing between the main space and its app twins. */
public final class AppTwinShareActivity extends SettingsBaseActivity {
    private record Target(CharSequence label, ComponentName component, int userId) { }

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_SubSettings_Expressive);
        super.onCreate(state);
        getTheme().applyStyle(R.style.SettingsPreferenceTheme_Expressive, false);
        try {
            Intent share = sanitize();
            UserManager users = getSystemService(UserManager.class);
            UserInfo self = users.getUserInfo(UserHandle.myUserId());
            if (self == null || (!self.isMain() && !self.isAppTwinProfile())) {
                finish(); return;
            }
            int parentId = self.isMain() ? self.id : users.getProfileParent(self.id).id;
            ArrayList<Target> targets = new ArrayList<>();
            for (UserInfo user : users.getProfiles(parentId)) {
                if (user.id == self.id || user.isQuietModeEnabled()
                        || !users.isUserUnlocked(user.getUserHandle())) continue;
                if (user.id != parentId && !user.isAppTwinProfile()) continue;
                Intent query = new Intent(share);
                if (user.isAppTwinProfile()) {
                    String pkg = Settings.Secure.getStringForUser(getContentResolver(),
                            UserManager.APP_TWIN_PACKAGE_SETTING, user.id);
                    if (TextUtils.isEmpty(pkg)) continue;
                    query.setPackage(pkg);
                }
                for (var match : getPackageManager().queryIntentActivitiesAsUser(query,
                        PackageManager.MATCH_DEFAULT_ONLY, user.id)) {
                    var activity = match.activityInfo;
                    if (!activity.exported || activity.permission != null
                            || activity.packageName.equals(getPackageName())) continue;
                    targets.add(new Target(match.loadLabel(getPackageManager()) + " · "
                            + (user.id == parentId ? getString(R.string.app_twin_original) : user.name),
                            new ComponentName(activity.packageName, activity.name), user.id));
                }
            }
            CharSequence[] labels = targets.stream().map(Target::label).toArray(CharSequence[]::new);
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_twin_share).setNegativeButton(android.R.string.cancel,
                            (d, which) -> finish()).setOnCancelListener(d -> finish());
            if (targets.isEmpty()) dialog.setMessage(R.string.app_twin_no_share_targets);
            else dialog.setItems(labels, (d, which) -> {
                Target target = targets.get(which);
                try {
                    // Preserve the original sender as the grant authority. The system UID must
                    // not issue URI grants on its own authority, even for a validated attachment.
                    Intent outgoing = new Intent(share).setComponent(target.component());
                    outgoing.fixUris(UserHandle.myUserId());
                    startActivityAsCaller(outgoing, null, false, target.userId());
                } catch (Exception e) {
                    Log.w("AppTwins", "Could not deliver shared content", e);
                    Toast.makeText(this, R.string.app_twin_share_failed, Toast.LENGTH_LONG).show();
                }
                finish();
            });
            dialog.show();
        } catch (Exception e) {
            Log.w("AppTwins", "Could not prepare share chooser", e);
            Toast.makeText(this, R.string.app_twin_share_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private Intent sanitize() {
        Intent source = getIntent();
        String action = source.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            throw new IllegalArgumentException("Not a share intent");
        }
        Intent result = new Intent(action).setType(source.getType());
        for (String key : new String[] {Intent.EXTRA_TEXT, Intent.EXTRA_SUBJECT, Intent.EXTRA_HTML_TEXT}) {
            CharSequence text = source.getCharSequenceExtra(key);
            if (text != null) result.putExtra(key, text.toString());
        }
        ArrayList<Uri> streams = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = source.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (stream != null) streams.add(stream);
        } else {
            ArrayList<Uri> multiple = source.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            if (multiple != null) streams.addAll(multiple);
        }
        if (streams.isEmpty() && source.getClipData() != null) {
            ClipData data = source.getClipData();
            if (data.getItemCount() > 100) throw new IllegalArgumentException("Too many attachments");
            for (int i = 0; i < data.getItemCount(); i++) {
                Uri uri = data.getItemAt(i).getUri();
                if (uri != null) streams.add(uri);
            }
            if (Intent.ACTION_SEND.equals(action) && streams.size() > 1) {
                throw new IllegalArgumentException("Multiple attachments require SEND_MULTIPLE");
            }
        }
        if (streams.size() > 100) throw new IllegalArgumentException("Too many attachments");
        ClipData clip = null;
        for (Uri uri : streams) {
            // Settings is privileged: never forward a URI just because Settings can read it.
            if (uri == null || !"content".equals(uri.getScheme())
                    || getInitialCaller().checkContentUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Sender cannot grant this attachment");
            }
            if (clip == null) clip = ClipData.newRawUri("attachment", uri);
            else clip.addItem(new ClipData.Item(uri));
        }
        if (clip != null) {
            result.setClipData(clip);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Intent.ACTION_SEND.equals(action)) result.putExtra(Intent.EXTRA_STREAM, streams.get(0));
            else result.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
        }
        return result;
    }
}
