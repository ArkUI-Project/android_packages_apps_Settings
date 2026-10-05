/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Process;
import android.os.Bundle;
import android.os.IProgressListener;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethod;

import com.android.settings.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** All mutation paths resolve a durable serial number back to an owned app-twin profile. */
public final class AppTwinStore {
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String READY = "arkui_app_twin_ready";
    static final int LIMIT = 8;
    private final Context mContext;
    private final UserManager mUsers;
    private final PackageManager mPackages;

    public AppTwinStore(Context context) {
        mContext = context.getApplicationContext();
        mUsers = mContext.getSystemService(UserManager.class);
        mPackages = mContext.getPackageManager();
    }

    public static boolean available(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        return users.isMainUser();
    }

    boolean canCreate() {
        return available(mContext)
                && !mUsers.hasUserRestriction(UserManager.DISALLOW_ADD_USER)
                && !mUsers.hasUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE)
                && !mUsers.hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS)
                && mUsers.canAddMoreProfilesToUser(UserManager.USER_TYPE_PROFILE_APP_TWIN,
                        UserHandle.myUserId());
    }

    static final class App {
        final String packageName;
        final CharSequence label;
        final Drawable icon;
        App(ApplicationInfo info, PackageManager pm) {
            packageName = info.packageName;
            label = info.loadLabel(pm);
            icon = info.loadIcon(pm);
        }
    }

    static final class Twin {
        final long serial;
        final int userId;
        final String name;
        final String packageName;
        final CharSequence appLabel;
        final Drawable icon;
        final boolean paused;
        final boolean ready;
        Twin(UserInfo user, String pkg, ApplicationInfo app, PackageManager pm, boolean complete) {
            serial = user.serialNumber;
            userId = user.id;
            name = user.name;
            packageName = pkg;
            appLabel = app == null ? (TextUtils.isEmpty(pkg) ? user.name : pkg) : app.loadLabel(pm);
            icon = app == null ? null : pm.getUserBadgedIcon(app.loadIcon(pm), user.getUserHandle());
            paused = user.isQuietModeEnabled();
            ready = complete && app != null;
        }
    }

    List<App> apps() {
        TreeMap<String, App> unique = new TreeMap<>();
        for (ResolveInfo info : mPackages.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            ApplicationInfo app = info.activityInfo.applicationInfo;
            if (eligible(app)) unique.put(app.packageName, new App(app, mPackages));
        }
        ArrayList<App> result = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance();
        result.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return result;
    }

    private boolean eligible(ApplicationInfo app) {
        if (UserHandle.getAppId(app.uid) < Process.FIRST_APPLICATION_UID || !app.enabled
                || (app.flags & ApplicationInfo.FLAG_PERSISTENT) != 0
                || app.packageName.equals(mContext.getPackageName())
                || app.packageName.startsWith("org.arkui.")) return false;
        if (!mPackages.queryIntentServices(new Intent(InputMethod.SERVICE_INTERFACE)
                .setPackage(app.packageName), PackageManager.MATCH_DISABLED_COMPONENTS).isEmpty()) {
            return false;
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(app.packageName);
        return mPackages.queryIntentActivities(home, 0).isEmpty()
                && mPackages.getLaunchIntentForPackage(app.packageName) != null;
    }

    List<Twin> twins() {
        ArrayList<Twin> result = new ArrayList<>();
        if (!available(mContext)) return result;
        for (UserInfo user : mUsers.getProfiles(UserHandle.myUserId())) {
            if (user.isAppTwinProfile()) result.add(read(user));
        }
        return result;
    }

    private Twin read(UserInfo user) {
        String pkg = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                UserManager.APP_TWIN_PACKAGE_SETTING, user.id);
        ApplicationInfo app = null;
        if (!TextUtils.isEmpty(pkg)) {
                try { app = mPackages.getApplicationInfoAsUser(pkg,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES),
                        user.id); }
            catch (PackageManager.NameNotFoundException ignored) { }
        }
        return new Twin(user, pkg, app, mPackages, Settings.Secure.getIntForUser(
                mContext.getContentResolver(), READY, 0, user.id) == 1);
    }

    Twin require(long serial) {
        if (!available(mContext)) throw new SecurityException("Not the main user");
        UserHandle handle = mUsers.getUserForSerialNumber(serial);
        UserInfo user = handle == null ? null : mUsers.getUserInfo(handle.getIdentifier());
        UserInfo parent = user == null ? null : mUsers.getProfileParent(user.id);
        if (user == null || user.serialNumber != serial || !user.isAppTwinProfile() || parent == null
                || parent.id != UserHandle.myUserId()) {
            throw new IllegalArgumentException("App twin no longer exists");
        }
        return read(user);
    }

    private String checkedName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.codePointCount(0, clean.length()) > 24
                || clean.codePoints().anyMatch(c -> Character.isISOControl(c)
                        || Character.getType(c) == Character.FORMAT)) {
            throw new IllegalArgumentException(mContext.getString(R.string.app_twin_name_error));
        }
        return clean;
    }

    void create(String pkg, String name) throws Exception {
        String clean = checkedName(name);
        checkUniqueName(pkg, clean, -1);
        if (!canCreate() || !eligible(mPackages.getApplicationInfo(pkg, 0))) {
            throw new IllegalStateException(mContext.getString(R.string.app_twin_unavailable));
        }
        ArrayList<String> disallowed = new ArrayList<>(List.of("org.arkui.sidebar",
                "org.arkui.wheel", "org.arkui.game", pkg));
        for (ResolveInfo ime : mPackages.queryIntentServices(new Intent(InputMethod.SERVICE_INTERFACE),
                PackageManager.MATCH_DISABLED_COMPONENTS)) {
            disallowed.add(ime.serviceInfo.packageName);
        }
        UserInfo created = mUsers.createProfileForUser(clean,
                UserManager.USER_TYPE_PROFILE_APP_TWIN, UserInfo.FLAG_INITIALIZED,
                UserHandle.myUserId(), disallowed.toArray(String[]::new));
        if (created == null) throw new IllegalStateException("Could not create profile");
        boolean complete = false;
        try {
            if (!Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    UserManager.APP_TWIN_PACKAGE_SETTING, pkg, created.id)) {
                throw new IllegalStateException("Could not save profile identity");
            }
            int result = AppGlobals.getPackageManager().installExistingPackageAsUser(pkg, created.id,
                    PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                    PackageManager.INSTALL_REASON_USER, null);
            if (result != PackageManager.INSTALL_SUCCEEDED) {
                throw new IllegalStateException("Package installation failed: " + result);
            }
            if (!ActivityManager.getService().startProfile(created.id)) {
                throw new IllegalStateException("Could not start profile");
            }
            if (!Settings.Secure.putIntForUser(mContext.getContentResolver(), READY, 1, created.id)) {
                throw new IllegalStateException("Could not finish profile setup");
            }
            complete = true;
        } finally {
            if (!complete) mUsers.removeUser(created.id);
        }
    }

    void rename(long serial, String name) {
        Twin twin = require(serial);
        String clean = checkedName(name);
        checkUniqueName(twin.packageName, clean, serial);
        mUsers.setUserName(twin.userId, clean);
        mContext.getSystemService(ShortcutManager.class).updateShortcuts(
                List.of(shortcut(require(serial))));
    }

    private void checkUniqueName(String pkg, String name, long exceptSerial) {
        for (Twin other : twins()) {
            if (other.serial != exceptSerial && pkg.equals(other.packageName)
                    && name.equals(other.name)) {
                throw new IllegalArgumentException(mContext.getString(R.string.app_twin_name_taken));
            }
        }
    }

    void pause(long serial, boolean paused) {
        Twin twin = require(serial);
        if (!mUsers.requestQuietModeEnabled(paused, UserHandle.of(twin.userId))) {
            throw new IllegalStateException(mContext.getString(R.string.app_twin_unlock));
        }
    }

    void remove(long serial) {
        Twin twin = require(serial);
        if (!mUsers.removeUser(twin.userId)) throw new IllegalStateException("Could not remove profile");
        mContext.getSystemService(ShortcutManager.class).disableShortcuts(
                List.of(shortcutId(serial)), mContext.getString(R.string.app_twin_removed));
    }

    void launch(long serial) throws Exception {
        Twin twin = require(serial);
        if (!twin.ready) throw new IllegalStateException(mContext.getString(R.string.app_twin_incomplete));
        if (twin.paused) pause(serial, false);
        CountDownLatch unlocked = new CountDownLatch(1);
        if (!ActivityManager.getService().startProfileWithListener(twin.userId,
                new IProgressListener.Stub() {
                    @Override public void onStarted(int id, Bundle extras) { }
                    @Override public void onProgress(int id, int progress, Bundle extras) { }
                    @Override public void onFinished(int id, Bundle extras) { unlocked.countDown(); }
                }) || (!mUsers.isUserUnlocked(UserHandle.of(twin.userId))
                        && !unlocked.await(15, TimeUnit.SECONDS))) {
            throw new IllegalStateException(mContext.getString(R.string.app_twin_unlock));
        }
        LauncherApps launcher = mContext.getSystemService(LauncherApps.class);
        var activities = launcher.getActivityList(twin.packageName, UserHandle.of(twin.userId));
        if (activities.isEmpty()) throw new IllegalStateException("No launchable activity");
        launcher.startMainActivity(activities.get(0).getComponentName(),
                UserHandle.of(twin.userId), null, null);
    }

    void pin(long serial) {
        ShortcutManager shortcuts = mContext.getSystemService(ShortcutManager.class);
        if (!shortcuts.isRequestPinShortcutSupported()
                || !shortcuts.requestPinShortcut(shortcut(require(serial)), null)) {
            throw new IllegalStateException(mContext.getString(R.string.app_twin_pin_failed));
        }
    }

    private static String shortcutId(long serial) { return "arkui_app_twin_" + serial; }

    private ShortcutInfo shortcut(Twin twin) {
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(mContext, shortcutId(twin.serial))
                .setShortLabel(twin.name).setLongLabel(twin.appLabel + " · " + twin.name)
                .setIntent(new Intent(mContext, AppTwinLaunchActivity.class)
                        .setAction(Intent.ACTION_MAIN).putExtra("serial", twin.serial));
        if (twin.icon != null) {
            Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            twin.icon.setBounds(0, 0, 192, 192);
            twin.icon.draw(new Canvas(bitmap));
            builder.setIcon(Icon.createWithBitmap(bitmap));
        }
        return builder.build();
    }

    /** Reconcile only our profiles after boot / uninstall, never other Android user types. */
    void reconcile(String removedPackage) {
        for (Twin twin : twins()) {
            boolean unfinished = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    READY, 0, twin.userId) != 1;
            boolean originalRemoved = false;
            // A reboot also repairs missed package broadcasts; archived originals remain valid.
            if (!TextUtils.isEmpty(twin.packageName)
                    && (removedPackage == null || removedPackage.equals(twin.packageName))) {
                try { mPackages.getApplicationInfo(twin.packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES)); }
                catch (PackageManager.NameNotFoundException e) { originalRemoved = true; }
            }
            if (unfinished || originalRemoved) remove(twin.serial);
        }
    }
}
