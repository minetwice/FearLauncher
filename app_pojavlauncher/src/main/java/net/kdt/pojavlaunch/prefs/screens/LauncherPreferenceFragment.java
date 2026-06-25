package net.kdt.pojavlaunch.prefs.screens;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 * 
 * Updated with Silver+Black theme and custom styling.
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected Runnable mVisibilityUpdater = () -> {};

    // ==========================================
    // APPLY CUSTOM THEME BEFORE INFLATING
    // ==========================================
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 🔥 Force apply Silver+Black Preference Theme
        getActivity().setTheme(R.style.FearPreferenceTheme);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Set background color (Deep Black)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_app));

        // Set divider (Silver) for the ListView
        ListView listView = getListView();
        if (listView != null) {
            listView.setDivider(ContextCompat.getDrawable(requireContext(), R.drawable.divider_silver));
            listView.setDividerHeight(1);
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        applyThemeToPreferences(); // Additional theming if needed
    }

    // ==========================================
    // THEME APPLICATION TO PREFERENCES (Optional)
    // ==========================================
    private void applyThemeToPreferences() {
        // If any preference needs dynamic theming, add here.
        // For example, set icon tint or summary color programmatically.
        // Currently handled by the theme.
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
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        }
        mVisibilityUpdater.run();
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
