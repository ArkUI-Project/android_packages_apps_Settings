/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.applock;

import android.app.AppLockManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Authenticated, per-user settings for the system app-lock policy. */
@SearchIndexable
public final class AppLockSettings extends SettingsPreferenceFragment {
    private static final String TAG = "AppLockSettings";
    private static final Set<String> CRITICAL_PACKAGES = Set.of("android",
            "com.android.settings", "com.android.systemui", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.certinstaller",
            "com.android.keyguard", "com.android.emergency");

    private record App(String packageName, CharSequence label, Drawable icon, boolean installed) { }

    private final IBinder mOwner = new Binder();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (mToken != null && isResumed()) refresh();
        }
    };
    private AppLockManager mManager;
    private IBinder mChallenge;
    private IBinder mToken;
    private CancellationSignal mCancellation;
    private Bundle mSettings;
    private List<App> mApps = List.of();
    private boolean mAppsLoaded;
    private boolean mAppsLoading;
    private boolean mOriginalSecure;
    private boolean mRegistered;
    private boolean mVisible;
    private boolean mUpdating;
    private int mAppGeneration;
    private AlertDialog mDialog;

    @Override public int getMetricsCategory() { return SettingsEnums.SECURITY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mManager = new AppLockManager(requireContext());
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        mOriginalSecure = (requireActivity().getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SECURE) != 0;
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        requireActivity().getWindow().setHideOverlayWindows(true);
        renderUnauthenticated();
    }

    @Override public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        view.setFilterTouchesWhenObscured(true);
    }

    @Override public void onResume() {
        super.onResume();
        mVisible = true;
        if (mAppsLoaded) mAppsLoaded = false;
        if (!AppLockPreferenceController.isAvailable(requireContext())) { finish(); return; }
        requireActivity().setTitle(R.string.arkui_app_lock_title);
        if (!mRegistered) {
            requireContext().registerReceiver(mReceiver,
                    new IntentFilter(AppLockManager.ACTION_APP_LOCK_STATE_CHANGED),
                    AppLockManager.PERMISSION, null, Context.RECEIVER_EXPORTED);
            mRegistered = true;
        }
        if (mToken != null) refresh();
        else {
            renderUnauthenticated();
            if (hasCredential()) authenticate();
        }
    }

    @Override public void onPause() {
        mVisible = false;
        // A quick return from Home can resume this page without ever delivering onStop.
        // Revoke an existing session immediately, while leaving pending authentication intact.
        if (mToken != null) {
            endSession();
            if (mDialog != null) { mDialog.dismiss(); mDialog = null; }
            renderUnauthenticated();
        }
        super.onPause();
    }

    @Override public void onStop() {
        // The device-credential screen can stop this page while authentication is pending.
        // Only an already redeemed management session is ended here.
        endSession();
        if (mDialog != null) { mDialog.dismiss(); mDialog = null; }
        if (mRegistered) {
            requireContext().unregisterReceiver(mReceiver);
            mRegistered = false;
        }
        if (isAdded()) renderUnauthenticated();
        super.onStop();
    }

    @Override public void onDestroy() {
        ++mAppGeneration;
        endSession();
        if (mCancellation != null) { mCancellation.cancel(); mCancellation = null; }
        cancelChallenge();
        if (!mOriginalSecure && getActivity() != null) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onDestroy();
    }

    private boolean hasCredential() {
        return requireContext().getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    private void authenticate() {
        if (!mVisible || mToken != null || mCancellation != null || !hasCredential()) return;
        try {
            mChallenge = mManager.beginManagementAuthentication(UserHandle.myUserId(), mOwner);
            Bundle info = mManager.getAuthenticationInfo(mChallenge);
            if (info == null || !info.getBoolean(AppLockManager.KEY_MANAGEMENT)
                    || info.getInt(AppLockManager.KEY_USER_ID, -1) != UserHandle.myUserId()) {
                cancelChallenge();
                return;
            }
            mCancellation = new CancellationSignal();
            new BiometricPrompt.Builder(requireContext())
                    .setTitle(getString(R.string.arkui_app_lock_auth_title))
                    .setSubtitle(getString(R.string.arkui_app_lock_manage_subtitle))
                    .setDescription(getString(R.string.arkui_app_lock_auth_description))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticateUser(mCancellation, requireContext().getMainExecutor(),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override public void onAuthenticationSucceeded(
                                        BiometricPrompt.AuthenticationResult result) {
                                    mCancellation = null;
                                    if (!isAdded() || mChallenge == null) return;
                                    try {
                                        mToken = mManager.completeAuthentication(mChallenge);
                                        mChallenge = null;
                                        if (mToken == null) throw new SecurityException("No session");
                                        if (mVisible) refresh();
                                        // onResume will read and validate this newly issued token
                                        // after the system credential screen returns to the page.
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "Management authentication expired", e);
                                        endSession();
                                        renderUnauthenticated();
                                        toast(R.string.arkui_app_lock_session_expired);
                                    }
                                }

                                @Override public void onAuthenticationError(int errorCode,
                                        CharSequence errString) {
                                    mCancellation = null;
                                    cancelChallenge();
                                    if (isAdded()) finish();
                                }
                            }, UserHandle.myUserId());
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not begin management authentication", e);
            cancelChallenge();
            mCancellation = null;
            toast(R.string.arkui_app_lock_failed);
        }
    }

    private void cancelChallenge() {
        if (mChallenge == null || mManager == null) return;
        try { mManager.cancelAuthentication(mChallenge); }
        catch (RuntimeException e) { Log.w(TAG, "Challenge already unavailable", e); }
        mChallenge = null;
    }

    private void endSession() {
        if (mToken != null && mManager != null) {
            try { mManager.endManagementSession(mToken); }
            catch (RuntimeException e) { Log.w(TAG, "Session already unavailable", e); }
        }
        mToken = null;
        mSettings = null;
    }

    private void renderUnauthenticated() {
        if (!isAdded() || getPreferenceScreen() == null) return;
        getPreferenceScreen().removeAll();
        row("app_lock_intro", R.string.arkui_app_lock_title,
                getString(R.string.arkui_app_lock_intro), null);
        if (!hasCredential()) {
            row("app_lock_credential", R.string.arkui_app_lock_credential_needed,
                    getString(R.string.arkui_app_lock_credential_summary), null);
            row("app_lock_setup", R.string.arkui_app_lock_set_credential, null, () ->
                    startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
                            .setPackage(requireContext().getPackageName())));
        } else {
            row("app_lock_authenticate", R.string.arkui_app_lock_auth_retry,
                    getString(R.string.arkui_app_lock_auth_description), this::authenticate);
        }
    }

    private void refresh() {
        if (!isAdded() || mToken == null) return;
        try {
            mSettings = mManager.getSettings(UserHandle.myUserId(), mToken);
            if (mSettings == null) throw new SecurityException("Session expired");
            renderSettings();
            if (!mAppsLoaded) loadApps();
        } catch (RuntimeException e) {
            Log.w(TAG, "Management session is no longer valid", e);
            endSession();
            if (mDialog != null) { mDialog.dismiss(); mDialog = null; }
            renderUnauthenticated();
            if (mVisible) authenticate();
        }
    }

    private void renderSettings() {
        mUpdating = true;
        getPreferenceScreen().removeAll();
        SettingsMainSwitchPreference main = new SettingsMainSwitchPreference(requireContext());
        main.setKey(AppLockManager.KEY_ENABLED);
        main.setTitle(R.string.arkui_app_lock_enable);
        main.setPersistent(false);
        main.setChecked(mSettings.getBoolean(AppLockManager.KEY_ENABLED));
        main.addOnSwitchChangeListener((button, checked) -> {
            if (mUpdating) return;
            if (checked && selectedPackages().isEmpty()) {
                toast(R.string.arkui_app_lock_choose_first);
                refresh();
                chooseApps();
                return;
            }
            update(AppLockManager.KEY_ENABLED, checked);
        });
        getPreferenceScreen().addPreference(main);
        row("app_lock_apps", R.string.arkui_app_lock_apps,
                getString(R.string.arkui_app_lock_apps_count, selectedPackages().size()),
                this::chooseApps);
        row("app_lock_intro", R.string.arkui_app_lock_title,
                getString(R.string.arkui_app_lock_intro), null);
        PreferenceCategory privacy = category("app_lock_privacy", R.string.arkui_app_lock_privacy);
        addSwitch(privacy, AppLockManager.KEY_HIDE_NOTIFICATIONS,
                R.string.arkui_app_lock_hide_notifications,
                R.string.arkui_app_lock_hide_notifications_summary, true);
        PreferenceCategory relock = category("app_lock_relock", R.string.arkui_app_lock_relock);
        ListPreference mode = new ListPreference(requireContext());
        mode.setKey(AppLockManager.KEY_LOCK_MODE);
        mode.setTitle(R.string.arkui_app_lock_mode);
        mode.setEntries(R.array.arkui_app_lock_modes);
        mode.setEntryValues(R.array.arkui_app_lock_mode_values);
        mode.setPersistent(false);
        mode.setValue(Integer.toString(mSettings.getInt(AppLockManager.KEY_LOCK_MODE)));
        mode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mode.setOnPreferenceChangeListener((p, value) -> {
            update(AppLockManager.KEY_LOCK_MODE, Integer.parseInt((String) value));
            return false;
        });
        relock.addPreference(mode);
        if (mSettings.getInt(AppLockManager.KEY_LOCK_MODE) == AppLockManager.MODE_TIMEOUT) {
            Preference timeout = new Preference(requireContext());
            timeout.setKey(AppLockManager.KEY_TIMEOUT_MINUTES);
            timeout.setTitle(R.string.arkui_app_lock_timeout);
            timeout.setSummary(getString(R.string.arkui_app_lock_timeout_summary,
                    mSettings.getInt(AppLockManager.KEY_TIMEOUT_MINUTES, 5)));
            timeout.setOnPreferenceClickListener(p -> { chooseTimeout(); return true; });
            relock.addPreference(timeout);
        }
        addSwitch(relock, AppLockManager.KEY_LOCK_ON_SCREEN_OFF,
                R.string.arkui_app_lock_screen_off, R.string.arkui_app_lock_screen_off_summary, true)
                .setEnabled(mSettings.getInt(AppLockManager.KEY_LOCK_MODE)
                        != AppLockManager.MODE_SCREEN_OFF);
        Preference lockNow = new Preference(requireContext());
        lockNow.setKey("app_lock_now");
        lockNow.setTitle(R.string.arkui_app_lock_lock_now);
        lockNow.setEnabled(mSettings.getBoolean(AppLockManager.KEY_ENABLED));
        lockNow.setOnPreferenceClickListener(p -> {
            try {
                mManager.lockAll(UserHandle.myUserId(), mToken);
                toast(R.string.arkui_app_lock_locked_now);
                refresh();
            } catch (RuntimeException e) { operationFailed(e); }
            return true;
        });
        relock.addPreference(lockNow);
        mUpdating = false;
    }

    private SwitchPreferenceCompat addSwitch(PreferenceCategory parent, String key, int title,
            int summary, boolean defaultValue) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(requireContext());
        preference.setKey(key);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setPersistent(false);
        preference.setChecked(mSettings.getBoolean(key, defaultValue));
        preference.setOnPreferenceChangeListener((p, value) -> { update(key, value); return false; });
        parent.addPreference(preference);
        return preference;
    }

    private PreferenceCategory category(String key, int title) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setKey(key);
        category.setTitle(title);
        getPreferenceScreen().addPreference(category);
        return category;
    }

    private void row(String key, int title, CharSequence summary, Runnable action) {
        Preference preference = new Preference(requireContext());
        preference.setKey(key);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setIconSpaceReserved(false);
        preference.setSelectable(action != null);
        if (action != null) preference.setOnPreferenceClickListener(p -> { action.run(); return true; });
        getPreferenceScreen().addPreference(preference);
    }

    private Set<String> selectedPackages() {
        ArrayList<String> packages = mSettings == null ? null
                : mSettings.getStringArrayList(AppLockManager.KEY_PACKAGES);
        return packages == null ? new HashSet<>() : new HashSet<>(packages);
    }

    private void update(String key, Object value) {
        if (mToken == null || mSettings == null) { refresh(); return; }
        Bundle settings = new Bundle(mSettings);
        if (value instanceof Boolean) settings.putBoolean(key, (Boolean) value);
        else settings.putInt(key, (Integer) value);
        if (settings.getInt(AppLockManager.KEY_LOCK_MODE) == AppLockManager.MODE_SCREEN_OFF) {
            settings.putBoolean(AppLockManager.KEY_LOCK_ON_SCREEN_OFF, true);
        }
        save(settings);
    }

    private void save(Bundle settings) {
        if (mToken == null || !mVisible) return;
        try {
            mManager.setSettings(UserHandle.myUserId(), mToken, settings);
            refresh();
        } catch (RuntimeException e) { operationFailed(e); }
    }

    private void operationFailed(RuntimeException e) {
        Log.w(TAG, "Could not update app-lock policy", e);
        toast(R.string.arkui_app_lock_failed);
        refresh();
    }

    private void chooseTimeout() {
        if (mSettings == null || mToken == null) return;
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });
        input.setHint(R.string.arkui_app_lock_timeout_hint);
        input.setText(Integer.toString(mSettings.getInt(AppLockManager.KEY_TIMEOUT_MINUTES, 5)));
        input.selectAll();
        input.setFilterTouchesWhenObscured(true);
        LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(dp(24), dp(8), dp(24), dp(8));
        container.addView(input, new LinearLayout.LayoutParams(-1, -2));
        mDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.arkui_app_lock_timeout).setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create();
        secureDialog(mDialog);
        mDialog.show();
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int minutes;
            try { minutes = Integer.parseInt(input.getText().toString().trim()); }
            catch (NumberFormatException e) { minutes = 0; }
            if (minutes < 1 || minutes > 1440) {
                input.setError(getString(R.string.arkui_app_lock_timeout_error));
                return;
            }
            update(AppLockManager.KEY_TIMEOUT_MINUTES, minutes);
            if (mDialog != null) { mDialog.dismiss(); mDialog = null; }
        });
    }

    private void loadApps() {
        if (mAppsLoading) return;
        mAppsLoading = true;
        final int generation = ++mAppGeneration;
        final Context context = requireContext().getApplicationContext();
        final Set<String> selected = selectedPackages();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                List<App> apps = queryApps(context, selected);
                ThreadUtils.postOnMainThread(() -> {
                    if (!isAdded() || generation != mAppGeneration) return;
                    mApps = apps;
                    mAppsLoading = false;
                    mAppsLoaded = true;
                });
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not load protectable apps", e);
                ThreadUtils.postOnMainThread(() -> {
                    if (!isAdded() || generation != mAppGeneration) return;
                    mAppsLoading = false;
                    toast(R.string.arkui_app_lock_failed);
                });
            }
        });
    }

    private static List<App> queryApps(Context context, Set<String> selected) {
        PackageManager pm = context.getPackageManager();
        Set<String> homePackages = new HashSet<>();
        for (var home : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME), 0)) {
            homePackages.add(home.activityInfo.packageName);
        }
        ArrayList<App> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            String pkg = info.packageName;
            boolean critical = CRITICAL_PACKAGES.contains(pkg) || homePackages.contains(pkg)
                    || pkg.equals(pm.getPermissionControllerPackageName())
                    || UserHandle.getAppId(info.uid) < Process.FIRST_APPLICATION_UID;
            if (critical || (pm.getLaunchIntentForPackage(pkg) == null && !selected.contains(pkg))) {
                continue;
            }
            apps.add(new App(pkg, info.loadLabel(pm), info.loadIcon(pm), true));
            seen.add(pkg);
        }
        for (String pkg : selected) {
            if (!seen.contains(pkg) && !CRITICAL_PACKAGES.contains(pkg)
                    && !homePackages.contains(pkg)) apps.add(new App(pkg, pkg, null, false));
        }
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return apps;
    }

    private void chooseApps() {
        if (mSettings == null || mToken == null) return;
        if (!mAppsLoaded) { toast(R.string.arkui_app_lock_loading); return; }
        Set<String> selected = selectedPackages();
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(12), 0);
        content.setFilterTouchesWhenObscured(true);
        SearchView search = new SearchView(requireContext());
        search.setIconifiedByDefault(false);
        search.setQueryHint(getString(R.string.arkui_app_lock_search));
        search.setBackgroundResource(R.drawable.arkui_home_search_background);
        search.findViewById(com.android.internal.R.id.search_plate).setBackground(null);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView empty = new TextView(requireContext());
        empty.setText(R.string.arkui_app_lock_no_results);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(24), dp(16), dp(24));
        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        RecyclerView list = new RecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        AppAdapter adapter = new AppAdapter(selected, empty);
        list.setAdapter(adapter);
        int height = Math.min(dp(400), Math.max(dp(120),
                getResources().getDisplayMetrics().heightPixels / 2));
        content.addView(list, new LinearLayout.LayoutParams(-1, height));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { search.clearFocus(); return true; }
            @Override public boolean onQueryTextChange(String query) { adapter.filter(query); return true; }
        });
        adapter.filter("");
        mDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.arkui_app_lock_apps).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (mSettings == null || mToken == null) return;
                    Bundle settings = new Bundle(mSettings);
                    settings.putStringArrayList(AppLockManager.KEY_PACKAGES, new ArrayList<>(selected));
                    if (selected.isEmpty()) settings.putBoolean(AppLockManager.KEY_ENABLED, false);
                    save(settings);
                }).create();
        secureDialog(mDialog);
        mDialog.show();
        search.clearFocus();
        mDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void secureDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setHideOverlayWindows(true);
        }
    }

    private final class AppAdapter extends RecyclerView.Adapter<AppHolder> {
        private final Set<String> mSelected;
        private final TextView mEmpty;
        private final ArrayList<App> mFiltered = new ArrayList<>();

        AppAdapter(Set<String> selected, TextView empty) { mSelected = selected; mEmpty = empty; }

        void filter(String query) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            mFiltered.clear();
            for (App app : mApps) {
                if (app.label.toString().toLowerCase(Locale.ROOT).contains(needle)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) mFiltered.add(app);
            }
            mFiltered.sort((a, b) -> Boolean.compare(mSelected.contains(b.packageName),
                    mSelected.contains(a.packageName)));
            mEmpty.setVisibility(mFiltered.isEmpty() ? View.VISIBLE : View.GONE);
            notifyDataSetChanged();
        }

        @Override public AppHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new AppHolder(new LinearLayout(parent.getContext()));
        }

        @Override public void onBindViewHolder(AppHolder holder, int position) {
            App app = mFiltered.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.title.setText(app.label);
            holder.summary.setText(app.installed ? app.packageName
                    : getString(R.string.arkui_app_lock_unavailable_app));
            holder.check.setChecked(mSelected.contains(app.packageName));
            holder.itemView.setEnabled(app.installed || mSelected.contains(app.packageName));
            holder.itemView.setOnClickListener(v -> {
                if (mSelected.contains(app.packageName)) mSelected.remove(app.packageName);
                else if (app.installed) mSelected.add(app.packageName);
                holder.check.setChecked(mSelected.contains(app.packageName));
                holder.itemView.setEnabled(app.installed || mSelected.contains(app.packageName));
            });
        }

        @Override public int getItemCount() { return mFiltered.size(); }
    }

    private final class AppHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView summary;
        final CheckBox check;

        AppHolder(LinearLayout row) {
            super(row);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(76));
            row.setPadding(dp(12), dp(10), dp(4), dp(10));
            row.setFilterTouchesWhenObscured(true);
            icon = new ImageView(row.getContext());
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout labels = new LinearLayout(row.getContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMarginStart(dp(16));
            row.addView(labels, labelParams);
            title = new TextView(row.getContext());
            title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
            labels.addView(title);
            summary = new TextView(row.getContext());
            summary.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
            labels.addView(summary);
            check = new CheckBox(row.getContext());
            check.setClickable(false);
            check.setFocusable(false);
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(int message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_app_lock_settings) {
                @Override protected boolean isPageSearchEnabled(Context context) {
                    return AppLockPreferenceController.isAvailable(context);
                }
            };
}
