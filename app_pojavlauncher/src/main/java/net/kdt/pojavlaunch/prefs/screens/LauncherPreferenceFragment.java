package net.kdt.pojavlaunch.prefs.screens;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LauncherPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected Runnable mVisibilityUpdater = () -> {};

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Apply custom preference theme
        getActivity().setTheme(R.style.FearPreferenceTheme);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Background
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_app));

        // Divider (silver)
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            DividerItemDecoration divider = new DividerItemDecoration(requireContext(),
                    DividerItemDecoration.VERTICAL);
            divider.setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider_silver));
            recyclerView.addItemDecoration(divider);
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
    }

    // 🔥 Apply gradient background to each preference item after the list is drawn
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        }
        mVisibilityUpdater.run();

        // Post to ensure the list is ready
        getListView().post(() -> applyBackgroundToAllPreferences());
    }

    private void applyBackgroundToAllPreferences() {
        RecyclerView recyclerView = getListView();
        if (recyclerView == null) return;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            // We need to find the actual preference item view (LinearLayout)
            // The preference item is usually a direct child or inside a container.
            // We'll recursively apply to all LinearLayouts within the recycler.
            applyBackgroundToView(child);
        }
    }

    private void applyBackgroundToView(View view) {
        if (view instanceof LinearLayout) {
            // This is the preference item container
            view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.preference_background));
            // Add margin to separate items
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(16, 8, 16, 8);
            view.setLayoutParams(params);
        }
        // If it's a ViewGroup, iterate children (but we may only need top-level)
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyBackgroundToView(group.getChildAt(i));
            }
        }
    }

    private void updateVisibility() {
        requirePreference("notification_permission_request")
                .setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if (activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        } else {
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if (preference != null) return preference;
        throw new IllegalStateException("Preference " + key + " is null");
    }

    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if (preferenceClass.isInstance(preference)) return (T) preference;
        throw new IllegalStateException("Preference " + key + " is not an instance of " + preferenceClass.getSimpleName());
    }

    protected LauncherActivity getLauncherActivity() {
        return ((LauncherActivity) getActivity());
    }
}
