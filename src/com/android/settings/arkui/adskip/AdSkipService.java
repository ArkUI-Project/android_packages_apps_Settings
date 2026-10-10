/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.adskip;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.TaskStackListener;
import android.app.WindowConfiguration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Slog;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** System-shipped, opt-in opening-ad rules running separately from the Settings UI. */
public final class AdSkipService extends AccessibilityService {
    private static final String TAG = "ArkAdSkip";
    private static final long SESSION_MS = 10_000;
    private static final long SCAN_INTERVAL_MS = 300;
    private static final int MAX_NODES = 400;
    private final HandlerThread mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
    private volatile Handler mWorker;
    private Set<String> mExcluded = Set.of();
    private String mPackage;
    private long mDeadline;
    private int mAttempts;
    private boolean mClicked;
    private volatile boolean mDestroyed;
    private volatile int mSuccessfulClicks;
    private final Runnable mScan = this::scan;
    private final Runnable mForeground = this::updateForeground;
    private final TaskStackListener mTasks = new TaskStackListener() {
        @Override public void onTaskStackChanged() { scheduleForeground(); }
        @Override public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task) {
            scheduleForeground();
        }
        @Override public void onTaskFocusChanged(int taskId, boolean focused) {
            scheduleForeground();
        }
    };
    private ContentObserver mObserver;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (mWorker != null) return;
        mThread.start();
        mWorker = new Handler(mThread.getLooper());
        // A stale cached splash button must never be reused for a later screen.
        setCacheEnabled(false);
        mObserver = new ContentObserver(mWorker) {
            @Override public void onChange(boolean selfChange) {
                mExcluded = AdSkipSettings.excluded(AdSkipService.this);
                if ((mPackage != null && mExcluded.contains(mPackage))
                        || !AdSkipSettings.enabled(AdSkipService.this)) {
                    mDeadline = 0;
                    mWorker.removeCallbacks(mScan);
                }
            }
        };
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(AdSkipSettings.EXCLUDED),
                false, mObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, mObserver);
        mWorker.post(() -> {
            mExcluded = AdSkipSettings.excluded(this);
            // Wait for the next app transition, rather than clicking the screen at enable time.
            try {
                if (mDestroyed) return;
                var tasks = ActivityTaskManager.getInstance().getTasks(1, false, false,
                        Display.DEFAULT_DISPLAY);
                if (!tasks.isEmpty() && tasks.get(0).topActivity != null) {
                    mPackage = tasks.get(0).topActivity.getPackageName();
                }
                ActivityTaskManager.getService().registerTaskStackListener(mTasks);
            } catch (RemoteException | RuntimeException e) {
                Slog.w(TAG, "Cannot observe foreground apps", e);
            }
        });
    }

    private void scheduleForeground() {
        Handler worker = mWorker;
        if (worker == null || mDestroyed) return;
        worker.removeCallbacks(mForeground);
        worker.post(mForeground);
    }

    private void updateForeground() {
        try {
            var tasks = ActivityTaskManager.getInstance().getTasks(1, false, false,
                    Display.DEFAULT_DISPLAY);
            var task = tasks.isEmpty() ? null : tasks.get(0);
            String name = task == null || task.topActivity == null ? null
                    : task.topActivity.getPackageName();
            boolean eligibleTask = task != null && task.userId == UserHandle.myUserId()
                    && task.getActivityType() == WindowConfiguration.ACTIVITY_TYPE_STANDARD
                    && task.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
            if (java.util.Objects.equals(name, mPackage)) {
                if (!eligibleTask) {
                    mDeadline = 0;
                    mWorker.removeCallbacks(mScan);
                }
                return;
            }
            mPackage = name;
            mDeadline = 0;
            mAttempts = 0;
            mClicked = false;
            mWorker.removeCallbacks(mScan);
            if (name == null || !eligibleTask
                    || name.equals(getPackageName()) || mExcluded.contains(name)
                    || UserHandle.getAppId(getPackageManager().getApplicationInfo(name, 0).uid)
                            < Process.FIRST_APPLICATION_UID || !deviceReady()) return;
            mDeadline = SystemClock.uptimeMillis() + SESSION_MS;
            mWorker.post(mScan);
        } catch (Exception e) {
            mDeadline = 0;
            Slog.w(TAG, "Cannot start opening-ad scan", e);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        Handler worker = mWorker;
        if (worker == null) return;
        // Task identity, rather than an overlay/keyboard event, controls the opening window.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scheduleForeground();
        }
        // Polling during the bounded opening window also handles countdowns without events.
    }

    private boolean deviceReady() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked()
                && getSystemService(UserManager.class).isUserUnlocked()
                && !getSystemService(UserManager.class).isManagedProfile()
                && getSystemService(ActivityManager.class).getLockTaskModeState()
                        == ActivityManager.LOCK_TASK_MODE_NONE;
    }

    private void scan() {
        if (mDestroyed || SystemClock.uptimeMillis() >= mDeadline || mClicked || mAttempts >= 2
                || mExcluded.contains(mPackage) || !deviceReady()
                || !AdSkipSettings.enabled(this)) return;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    if (mPackage.contentEquals(root.getPackageName() == null ? ""
                            : root.getPackageName())) inspect(root);
                } finally { root.recycle(); }
            }
        } catch (RuntimeException e) {
            // App windows may disappear while their accessibility tree is being retrieved.
            Slog.w(TAG, "Opening-ad window unavailable", e);
        }
        if (!mClicked && mAttempts < 2 && SystemClock.uptimeMillis() < mDeadline) {
            mWorker.postDelayed(mScan, SCAN_INTERVAL_MS);
        }
    }

    private void inspect(AccessibilityNodeInfo root) {
        Rect window = new Rect();
        root.getBoundsInScreen(window);
        if (window.isEmpty()) return;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        boolean adMarker = false;
        long until = SystemClock.uptimeMillis() + 80;
        try {
            while (!queue.isEmpty() && nodes.size() < MAX_NODES
                    && SystemClock.uptimeMillis() < until) {
                AccessibilityNodeInfo node = queue.removeFirst();
                nodes.add(node);
                if (!node.isVisibleToUser()) continue;
                adMarker |= AdSkipMatcher.isAdMarker(node.getText())
                        || AdSkipMatcher.isAdMarker(node.getContentDescription());
                for (int i = 0; i < node.getChildCount() && i < MAX_NODES
                        && nodes.size() + queue.size() < MAX_NODES
                        && SystemClock.uptimeMillis() < until;
                        i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.add(child);
                }
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (!node.isVisibleToUser() || !node.isEnabled()
                        || !mPackage.contentEquals(node.getPackageName() == null ? ""
                                : node.getPackageName())
                        || !AdSkipMatcher.matches(node.getText(), node.getContentDescription(),
                                node.getViewIdResourceName(), adMarker)) continue;
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (!corner(bounds, window)) continue;
                if (click(node, window, adMarker)) return;
            }
        } finally {
            for (AccessibilityNodeInfo node : nodes) node.recycle();
            for (AccessibilityNodeInfo node : queue) node.recycle();
        }
    }

    private boolean corner(Rect bounds, Rect window) {
        return AdSkipMatcher.isCorner(bounds.left - window.left, bounds.top - window.top,
                bounds.right - window.left, bounds.bottom - window.top,
                window.width(), window.height());
    }

    private boolean click(AccessibilityNodeInfo target, Rect window, boolean adMarker) {
        if (!target.refresh() || !AdSkipMatcher.matches(target.getText(),
                target.getContentDescription(), target.getViewIdResourceName(), adMarker)) return false;
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(target);
        try {
            for (int depth = 0; node != null && depth < 3; depth++) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (!node.isVisibleToUser() || !node.isEnabled() || !corner(bounds, window)
                        || !mPackage.contentEquals(node.getPackageName() == null ? ""
                                : node.getPackageName())) return false;
                if (node.isClickable()) {
                    // Recheck foreground identity immediately before sending the node action.
                    String expectedPackage = mPackage;
                    long expectedDeadline = mDeadline;
                    updateForeground();
                    if (mDestroyed || !expectedPackage.equals(mPackage) || mDeadline != expectedDeadline
                            || SystemClock.uptimeMillis() >= mDeadline || mAttempts >= 2
                            || !deviceReady() || !AdSkipSettings.enabled(this)) return false;
                    mAttempts++;
                    if (!node.refresh() || !node.isVisibleToUser() || !node.isEnabled()
                            || !node.isClickable()) return false;
                    node.getBoundsInScreen(bounds);
                    if (!corner(bounds, window)) return false;
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        mClicked = true;
                        mSuccessfulClicks++;
                        int count = Settings.Secure.getInt(getContentResolver(), AdSkipSettings.COUNT, 0);
                        Settings.Secure.putInt(getContentResolver(), AdSkipSettings.COUNT,
                                count == Integer.MAX_VALUE ? count : count + 1);
                        Slog.i(TAG, "Opening ad skipped");
                    }
                    return mClicked;
                }
                AccessibilityNodeInfo parent = node.getParent();
                node.recycle();
                node = parent;
            }
        } finally { if (node != null) node.recycle(); }
        return false;
    }

    @Override public void onInterrupt() {
        if (mWorker != null) mWorker.post(() -> {
            mDeadline = 0;
            mWorker.removeCallbacks(mScan);
        });
    }

    @Override public void onDestroy() {
        mDestroyed = true;
        if (mObserver != null) getContentResolver().unregisterContentObserver(mObserver);
        if (mWorker != null) {
            mWorker.removeCallbacksAndMessages(null);
            mWorker.post(() -> {
                try { ActivityTaskManager.getService().unregisterTaskStackListener(mTasks); }
                catch (RemoteException ignored) { }
                mThread.quitSafely();
            });
        } else mThread.quitSafely();
        super.onDestroy();
    }

    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("ArkUI ad skipper: successful node clicks=" + mSuccessfulClicks);
    }
}
