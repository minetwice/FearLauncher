package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
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
    private TextView accountName;
    private TextView versionText;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Views
        accountName = view.findViewById(R.id.account_name);
        versionText = view.findViewById(R.id.version_text);

        View playButton           = view.findViewById(R.id.play_button);
        View newsButton           = view.findViewById(R.id.news_button);
        View discordButton        = view.findViewById(R.id.social_media_button);
        View customControlButton  = view.findViewById(R.id.custom_control_button);
        View installJarButton     = view.findViewById(R.id.install_jar_button);
        View shareLogsButton      = view.findViewById(R.id.share_logs_button);
        View openFilesButton      = view.findViewById(R.id.open_files_button);
        ImageButton hamburgerBtn  = view.findViewById(R.id.hamburger_menu_button);
        ImageButton editProfileBtn= view.findViewById(R.id.edit_profile_button);
        mVersionSpinner           = view.findViewById(R.id.mc_version_spinner);

        // Update UI displays
        updateAccountName();
        updateVersionText();

        // ── Play Button ────────────────────────────────────────────────
        if (playButton != null) {
            playButton.setOnClickListener(v -> handlePlayButton());
        }

        // ── News / Wiki ────────────────────────────────────────────────
        if (newsButton != null) {
            newsButton.setOnClickListener(v ->
                    Tools.openURL(requireActivity(), Tools.URL_HOME));
            newsButton.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(),
                        GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }

        // ── Discord ────────────────────────────────────────────────────
        if (discordButton != null) {
            discordButton.setOnClickListener(v ->
                    Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        }

        // ── Custom Controls ────────────────────────────────────────────
        if (customControlButton != null) {
            customControlButton.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        // ── Install JAR ────────────────────────────────────────────────
        if (installJarButton != null) {
            installJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        }

        // ── Share Logs ─────────────────────────────────────────────────
        if (shareLogsButton != null) {
            shareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
        }

        // ── Open Game Directory ────────────────────────────────────────
        if (openFilesButton != null) {
            openFilesButton.setOnClickListener(v -> openGameDirectory(v.getContext()));
        }

        // ── Hamburger Menu ─────────────────────────────────────────────
        if (hamburgerBtn != null) {
            hamburgerBtn.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(),
                            GamepadMapperFragment.class, GamepadMapperFragment.TAG, null));
        }

        // ── Edit Profile (opens version/profile editor) ────────────────
        if (editProfileBtn != null) {
            editProfileBtn.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLAY BUTTON HANDLER — FIX: checks account before launching
    // ─────────────────────────────────────────────────────────────────────────
    private void handlePlayButton() {
        // FIX: Check if account is selected and saved
        MinecraftAccount currentAccount = Accounts.getCurrent();
        if (currentAccount == null) {
            Toast.makeText(requireContext(),
                    R.string.no_account,  // add this string if missing (see note below)
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Check instance is selected
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }

        // All good — launch game
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACCOUNT NAME — reads from Accounts.getCurrent() safely
    // ─────────────────────────────────────────────────────────────────────────
    private void updateAccountName() {
        if (accountName == null) return;

        MinecraftAccount current = Accounts.getCurrent();

        if (current != null && current.username != null && !current.username.isEmpty()
                && !current.username.equals("0")) {
            accountName.setText(current.username);
        } else {
            // No account — show hint to add one
            accountName.setText(R.string.no_account_hint); // "Add Account"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERSION TEXT — reads from selected instance
    // ─────────────────────────────────────────────────────────────────────────
    private void updateVersionText() {
        if (versionText == null) return;

        Instance instance = Instances.loadSelectedInstance();
        if (instance != null && instance.versionId != null && !instance.versionId.isEmpty()) {
            versionText.setText(instance.versionId);
        } else {
            versionText.setText(R.string.no_instance_selected); // "No version selected"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OPEN GAME DIRECTORY
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // JAR INSTALLER
    // ─────────────────────────────────────────────────────────────────────────
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
        // FIX: Refresh everything on resume — account might have been added/removed
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        updateAccountName();
        updateVersionText();
    }
}
