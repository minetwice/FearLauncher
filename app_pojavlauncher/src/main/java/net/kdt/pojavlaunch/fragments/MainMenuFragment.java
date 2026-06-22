package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Top Bar Views
        ImageView accountAvatar = view.findViewById(R.id.account_avatar);
        TextView accountName = view.findViewById(R.id.account_name);
        ImageButton settingsTopButton = view.findViewById(R.id.setting_button_top);

        // Hero Section
        TextView versionText = view.findViewById(R.id.version_text);
        Button playButton = view.findViewById(R.id.play_button);

        // Quick Actions
        View installationsButton = view.findViewById(R.id.installations_button);
        View modsButton = view.findViewById(R.id.mods_button);
        View skinsButton = view.findViewById(R.id.skins_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View shareLogsButton = view.findViewById(R.id.share_logs_button);
        View installJarButton = view.findViewById(R.id.install_jar_button);
        View settingsButton = view.findViewById(R.id.settings_button);
        View openFilesButton = view.findViewById(R.id.open_files_button);

        // Version Selector & Edit Profile
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);

        // ---- SETUP LISTENERS ----

        // 1. Settings (Top Right)
        if (settingsTopButton != null) {
            settingsTopButton.setOnClickListener(v -> {
                // Open settings fragment
                // You can replace with actual settings navigation
                Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment.class, null, null);
            });
        }

        // 2. Play Button
        if (playButton != null) {
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }

        // 3. Installations
        if (installationsButton != null) {
            installationsButton.setOnClickListener(v -> {
                // Navigate to profile/installations
                // For example, open ProfileTypeSelectFragment or similar
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null);
            });
        }

        // 4. Mods
        if (modsButton != null) {
            modsButton.setOnClickListener(v -> {
                // Navigate to mod search fragment
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
            });
        }

        // 5. Skins
        if (skinsButton != null) {
            skinsButton.setOnClickListener(v -> {
                // Open skin management or account settings
                // Could open the account selector or a skin picker
                // Placeholder: open account spinner dropdown
                // You can also launch an intent to a skin activity if exists
                Toast.makeText(requireContext(), "Skins feature coming soon!", Toast.LENGTH_SHORT).show();
            });
        }

        // 6. Custom Controls
        if (customControlButton != null) {
            customControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        // 7. Share Logs
        if (shareLogsButton != null) {
            shareLogsButton.setOnClickListener(v -> Tools.shareLog(requireContext()));
        }

        // 8. Execute JAR
        if (installJarButton != null) {
            installJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        }

        // 9. Settings (from Quick Actions)
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment.class, null, null);
            });
        }

        // 10. Open Directory
        if (openFilesButton != null) {
            openFilesButton.setOnClickListener(v -> openGameDirectory(requireContext()));
        }

        // 11. Edit Profile (from version selector)
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }

        // 12. Account Avatar – optionally open account switcher
        if (accountAvatar != null) {
            accountAvatar.setOnClickListener(v -> {
                // Open account selection or dropdown
                // For now, just a placeholder
                Toast.makeText(requireContext(), "Account switcher coming soon!", Toast.LENGTH_SHORT).show();
            });
        }

        // 13. Account Name – same as avatar
        if (accountName != null) {
            accountName.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Account switcher coming soon!", Toast.LENGTH_SHORT).show();
            });
        }

        // (Optional) Set version text dynamically
        if (versionText != null) {
            // You can set from selected instance version
            // Example: versionText.setText("1.21.1");
        }
    }

    // ===============================
    // UTILITY METHODS
    // ===============================
    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            Tools.openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            new net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension("jar").launch(null);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }
}
