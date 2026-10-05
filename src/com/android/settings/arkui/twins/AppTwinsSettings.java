/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.arkui.twins;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.View;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.search.SearchIndexable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SearchIndexable
public final class AppTwinsSettings extends SettingsPreferenceFragment {
    private AppTwinModel mModel;
    private long mSerial = -1;
    private String mPackage;
    private String mQuery = "";
    private SearchView mSearch;
    private static final int MENU_HELP = R.id.app_twin_search;
    private static final Set<String> RECOMMENDED = Set.of("com.tencent.mm", "com.tencent.mobileqq",
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.katana",
            "com.instagram.android", "com.sina.weibo", "jp.naver.line.android");

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_SYSTEM_CATEGORY; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getArguments() != null) {
            mSerial = getArguments().getLong("serial", -1);
            mPackage = getArguments().getString("package");
        }
        if (state != null) mQuery = state.getString("query", "");
        mModel = new ViewModelProvider(requireActivity()).get(AppTwinModel.class);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
    }

    @Override public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        if (mSerial < 0 && mPackage == null) {
            View header = setPinnedHeaderView(R.layout.arkui_app_twins_search);
            mSearch = header.findViewById(R.id.app_twin_search);
            mSearch.findViewById(com.android.internal.R.id.search_plate).setBackground(null);
            mSearch.setQuery(mQuery, false);
            mSearch.clearFocus();
            mSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) {
                    mSearch.clearFocus(); return true;
                }
                @Override public boolean onQueryTextChange(String query) {
                    mQuery = query; render(); return true;
                }
            });
        }
        mModel.revision.observe(getViewLifecycleOwner(), ignored -> render());
    }

    @Override public void onSaveInstanceState(Bundle state) {
        state.putString("query", mQuery);
        super.onSaveInstanceState(state);
    }

    @Override public void onDestroyView() {
        mSearch = null;
        super.onDestroyView();
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_HELP, Menu.NONE, R.string.app_twin_help)
                .setIcon(R.drawable.ic_info_outline_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != MENU_HELP) return super.onOptionsItemSelected(item);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.app_twins_title)
                .setMessage(getString(R.string.app_twins_intro) + "\n\n"
                        + getString(R.string.app_twin_count, mModel.twins.size(), AppTwinStore.LIMIT))
                .setPositiveButton(android.R.string.ok, null).show();
        return true;
    }

    @Override public void onResume() {
        super.onResume();
        if (!AppTwinStore.available(requireContext())) { finish(); return; }
        mModel.refresh();
    }

    private Preference row(String key, CharSequence title, CharSequence summary, Runnable click) {
        Preference preference = new Preference(requireContext());
        preference.setKey(key);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setIconSpaceReserved(false);
        preference.setSelectable(click != null);
        preference.setEnabled(!mModel.busy);
        if (click != null) preference.setOnPreferenceClickListener(p -> { click.run(); return true; });
        getPreferenceScreen().addPreference(preference);
        return preference;
    }

    private void render() {
        if (!isAdded()) return;
        getPreferenceScreen().removeAll();
        if (mModel.error != null) {
            Toast.makeText(requireContext(), getString(R.string.app_twin_error, mModel.error),
                    Toast.LENGTH_LONG).show();
            mModel.error = null;
        }
        if (mSerial >= 0) { renderTwin(); return; }
        if (mPackage != null) { renderApp(); return; }
        requireActivity().setTitle(R.string.app_twins_title);
        if (mSearch != null) mSearch.setQueryHint(getString(R.string.app_twin_search_count,
                mModel.apps.size()));
        if (mModel.apps.isEmpty() && mModel.busy) {
            row("loading", getString(R.string.app_twin_working), null, null); return;
        }
        String query = mQuery.trim().toLowerCase(Locale.ROOT);
        ArrayList<AppTwinStore.App> recommended = new ArrayList<>();
        ArrayList<AppTwinStore.App> others = new ArrayList<>();
        for (AppTwinStore.App app : mModel.apps) {
            if (!app.label.toString().toLowerCase(Locale.ROOT).contains(query)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            (RECOMMENDED.contains(app.packageName) ? recommended : others).add(app);
        }
        addApps("recommended", R.string.app_twin_recommended, recommended);
        addApps("apps", R.string.app_twin_available_apps, others);
        if (recommended.isEmpty() && others.isEmpty()) {
            row("empty", getString(R.string.app_twin_no_results), null, null);
        }
        // Interrupted or archived entries must remain manageable even without a launcher entry.
        List<AppTwinStore.Twin> unavailable = mModel.twins.stream().filter(twin ->
                mModel.apps.stream().noneMatch(app -> app.packageName.equals(twin.packageName)))
                .toList();
        if (!unavailable.isEmpty() && query.isEmpty()) {
            category("unavailable", R.string.app_twin_other_instances);
            addTwins(unavailable);
        }
    }

    private PreferenceCategory category(String key, int title) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setKey(key); category.setTitle(title);
        getPreferenceScreen().addPreference(category);
        return category;
    }

    private List<AppTwinStore.Twin> instances(String pkg) {
        return mModel.twins.stream().filter(twin -> pkg.equals(twin.packageName)).toList();
    }

    private void addApps(String key, int title, List<AppTwinStore.App> apps) {
        if (apps.isEmpty()) return;
        PreferenceCategory group = category(key, title);
        for (AppTwinStore.App app : apps) {
            List<AppTwinStore.Twin> twins = instances(app.packageName);
            PrimarySwitchPreference item = new PrimarySwitchPreference(requireContext());
            item.setKey("app_" + app.packageName); item.setPersistent(false);
            item.setTitle(app.label); item.setIcon(app.icon);
            item.setSummary(twins.isEmpty() ? getString(R.string.app_twin_not_enabled)
                    : getString(R.string.app_twin_enabled_count, twins.size()));
            item.setChecked(!twins.isEmpty());
            item.setEnabled(!mModel.busy);
            item.setSwitchEnabled(!mModel.busy && (!twins.isEmpty() || mModel.canCreate));
            item.setOnPreferenceClickListener(p -> {
                Bundle args = new Bundle(); args.putString("package", app.packageName);
                navigate(args, app.label); return true;
            });
            item.setOnPreferenceChangeListener((p, value) -> {
                // The framework switch changes its own visual state before invoking the listener.
                if (item.getSwitch() != null) item.getSwitch().setChecked(!twins.isEmpty());
                if ((Boolean) value) create(app.packageName);
                else new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.app_twin_disable_title, app.label))
                        .setMessage(getString(R.string.app_twin_disable_confirm, twins.size()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.app_twin_disable, (d, which) -> mModel.run(() -> {
                            for (AppTwinStore.Twin twin : twins) mModel.store.remove(twin.serial);
                        })).show();
                return false;
            });
            group.addPreference(item);
        }
    }

    private void navigate(Bundle args, CharSequence title) {
        new SubSettingLauncher(requireContext()).setDestination(AppTwinsSettings.class.getName())
                .setArguments(args).setTitleText(title)
                .setSourceMetricsCategory(getMetricsCategory()).launch();
    }

    private void renderApp() {
        AppTwinStore.App app = mModel.apps.stream().filter(a -> a.packageName.equals(mPackage))
                .findFirst().orElse(null);
        if (app == null) {
            if (mModel.loaded && !mModel.busy) finish();
            return;
        }
        requireActivity().setTitle(app.label);
        row("identity", app.label, mPackage, null).setIcon(app.icon);
        Preference add = row("create", getString(R.string.app_twin_add),
                mModel.busy ? getString(R.string.app_twin_working)
                        : getString(R.string.app_twin_count, mModel.twins.size(), AppTwinStore.LIMIT),
                () -> create(mPackage));
        add.setIcon(R.drawable.ic_add_24dp);
        add.setEnabled(!mModel.busy && mModel.canCreate);
        if (!mModel.busy && !mModel.canCreate) add.setSummary(R.string.app_twin_unavailable);
        List<AppTwinStore.Twin> twins = instances(mPackage);
        addTwins(twins);
        if (twins.isEmpty() && !mModel.busy) {
            row("empty", getString(R.string.app_twin_empty), getString(R.string.app_twin_empty_hint), null);
        }
    }

    private void addTwins(List<AppTwinStore.Twin> twins) {
        for (AppTwinStore.Twin twin : twins) {
            Preference item = row("twin_" + twin.serial, twin.name,
                    twin.appLabel + " · " + getString(twin.ready
                            ? (twin.paused ? R.string.app_twin_paused : R.string.app_twin_active)
                            : R.string.app_twin_incomplete), () -> {
                        Bundle args = new Bundle(); args.putLong("serial", twin.serial);
                        navigate(args, twin.name);
                    });
            item.setIcon(twin.icon);
        }
    }

    private void renderTwin() {
        AppTwinStore.Twin twin = mModel.twins.stream()
                .filter(item -> item.serial == mSerial).findFirst().orElse(null);
        if (twin == null) {
            if (!mModel.busy && mModel.loaded) finish();
            return;
        }
        requireActivity().setTitle(twin.name);
        row("identity", twin.appLabel, twin.packageName, null).setIcon(twin.icon);
        row("open", getString(R.string.app_twin_open),
                mModel.busy ? getString(R.string.app_twin_working) : null,
                () -> mModel.run(() -> mModel.store.launch(mSerial))).setEnabled(!mModel.busy && twin.ready);
        row("rename", getString(R.string.app_twin_rename), twin.name,
                () -> nameDialog(twin.name, name -> mModel.run(() -> mModel.store.rename(mSerial, name))));
        row("pin", getString(R.string.app_twin_pin), null,
                () -> mModel.run(() -> mModel.store.pin(mSerial))).setEnabled(!mModel.busy && twin.ready);

        SwitchPreferenceCompat pause = new SwitchPreferenceCompat(requireContext());
        pause.setPersistent(false);
        pause.setKey("pause"); pause.setTitle(R.string.app_twin_pause);
        pause.setSummary(R.string.app_twin_pause_hint);
        pause.setIconSpaceReserved(false); pause.setChecked(twin.paused);
        pause.setEnabled(!mModel.busy && twin.ready);
        pause.setOnPreferenceChangeListener((p, value) -> {
            mModel.run(() -> mModel.store.pause(mSerial, (Boolean) value)); return false;
        });
        getPreferenceScreen().addPreference(pause);
        row("app_info", getString(R.string.app_twin_app_info),
                getString(R.string.app_twin_app_info_hint), () -> openDetails(twin, false))
                .setEnabled(!mModel.busy && twin.ready && !twin.paused);
        row("notifications", getString(R.string.app_twin_notifications), null,
                () -> openDetails(twin, true)).setEnabled(!mModel.busy && twin.ready && !twin.paused);
        row("keyboard", getString(R.string.app_twin_keyboard),
                getString(R.string.app_twin_keyboard_hint), () -> startActivity(
                        new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        row("delete", getString(R.string.app_twin_delete), getString(R.string.app_twin_delete_hint),
                () -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.app_twin_delete_title, twin.name))
                        .setMessage(R.string.app_twin_delete_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.app_twin_delete, (dialog, which) ->
                                mModel.run(() -> mModel.store.remove(mSerial))).show());
    }

    private void openDetails(AppTwinStore.Twin snapshot, boolean notifications) {
        try {
            AppTwinStore.Twin twin = mModel.store.require(snapshot.serial);
            Intent intent = notifications
                    ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, twin.packageName)
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", twin.packageName, null));
            intent.setPackage(requireContext().getPackageName());
            requireActivity().startActivityAsUser(intent, UserHandle.of(twin.userId));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.app_twin_incomplete, Toast.LENGTH_LONG).show();
        }
    }

    private interface NameAction { void accept(String name); }

    private void nameDialog(String initial, NameAction action) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true); input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(24)});
        input.setHint(R.string.app_twin_name); input.setText(initial);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(requireContext());
        box.setPadding(padding, 0, padding, 0); box.addView(input,
                new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_twin_name).setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.codePoints().anyMatch(c -> Character.isISOControl(c)
                            || Character.getType(c) == Character.FORMAT)) {
                        input.setError(getString(R.string.app_twin_name_error)); return;
                    }
                    dialog.dismiss(); action.accept(name);
                }));
        dialog.show();
    }

    private void create(String pkg) {
        var names = new java.util.HashSet<String>();
        for (var twin : instances(pkg)) names.add(twin.name);
        int number = 1;
        while (names.contains(getString(R.string.app_twin_default_name, number))) number++;
        nameDialog(getString(R.string.app_twin_default_name, number), name ->
                mModel.run(() -> mModel.store.create(pkg, name)));
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arkui_app_twins) {
                @Override protected boolean isPageSearchEnabled(Context context) {
                    return AppTwinStore.available(context);
                }
            };
}
