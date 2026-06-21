package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
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

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // === FIND ALL VIEWS ===
        ImageButton hamburgerMenuButton = view.findViewById(R.id.hamburger_menu_button);
        TextView welcomeUsername = view.findViewById(R.id.welcome_username);
        Button playButton = view.findViewById(R.id.play_button);
        Button newsButton = view.findViewById(R.id.news_button);
        Button discordButton = view.findViewById(R.id.social_media_button);
        Button customControlButton = view.findViewById(R.id.custom_control_button);
        Button shareLogsButton = view.findViewById(R.id.share_logs_button);
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        // =====================================================
        // 1. HAMBURGER MENU (Execute .jar & Open Directory)
        // =====================================================
        if (hamburgerMenuButton != null) {
            hamburgerMenuButton.setOnClickListener(v -> showHamburgerPopup(v));
        }

        // =====================================================
        // 2. WELCOME USERNAME (Dynamic Setup)
        // =====================================================
        if (welcomeUsername != null) {
            // TODO: Replace with actual username from AccountManager
            // String username = AccountManager.getCurrentUsername();
            // welcomeUsername.setText(username);
        }

        // =====================================================
        // 3. PLAY BUTTON
        // =====================================================
        if (playButton != null) {
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }

        // =====================================================
        // 4. WIKI / NEWS BUTTON
        // =====================================================
        if (newsButton != null) {
            newsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
            newsButton.setOnLongClickListener((v) -> {
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }

        // =====================================================
        // 5. DISCORD / SOCIAL BUTTON
        // =====================================================
        if (discordButton != null) {
            discordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        }

        // =====================================================
        // 6. CUSTOM CONTROLS BUTTON
        // =====================================================
        if (customControlButton != null) {
            customControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        // =====================================================
        // 7. SHARE LOGS BUTTON
        // =====================================================
        if (shareLogsButton != null) {
            shareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        }

        // =====================================================
        // 8. EDIT PROFILE BUTTON (Version Spinner)
        // =====================================================
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }
    }

    // =====================================================
    // HAMBURGER POPUP MENU LOGIC
    // =====================================================
    private void showHamburgerPopup(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Execute .jar");
        popup.getMenu().add(0, 2, 1, "Open Game Directory");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                runInstallerWithConfirmation();
                return true;
            } else if (id == 2) {
                openGameDirectory(requireContext());
                return true;
            }
            return false;
        });

        popup.show();
    }

    // =====================================================
    // OPEN GAME DIRECTORY (Copied from old code)
    // =====================================================
    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    // =====================================================
    // INSTALLER LAUNCHER (Copied from old code)
    // =====================================================
    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
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
