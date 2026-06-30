package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private TextView mAccountName;
    private TextView mAccountTypeLabel;
    private TextView mVersionText;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Account section
        mAccountName      = view.findViewById(R.id.account_name);
        mAccountTypeLabel = view.findViewById(R.id.account_type_label);
        mVersionText      = view.findViewById(R.id.version_text);
        View accountSection = view.findViewById(R.id.account_section);

        // Buttons
        View playButton          = view.findViewById(R.id.play_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View installJarButton    = view.findViewById(R.id.install_jar_button);
        View shareLogsButton     = view.findViewById(R.id.share_logs_button_tray);
        View openFilesButton     = view.findViewById(R.id.open_files_button);
        View hamburgerBtn        = view.findViewById(R.id.hamburger_menu_icon);
        View editBtn             = view.findViewById(R.id.edit_profile_button);
        mVersionSpinner          = view.findViewById(R.id.mc_version_spinner);

        // Refresh UI
        refreshAccountUI();
        updateVersionText();

        if (accountSection != null) {
            accountSection.setOnClickListener(v -> openAccountManager());
        }

        if (playButton != null) {
            playButton.setOnClickListener(v -> handlePlayButton());
        }

        if (customControlButton != null) {
            customControlButton.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        if (installJarButton != null) {
            installJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        }

        if (openFilesButton != null) {
            openFilesButton.setOnClickListener(v -> openGameDirectory(v.getContext()));
        }

        if (editBtn != null) {
            editBtn.setOnClickListener(v -> {
                if (mVersionSpinner != null)
                    mVersionSpinner.openProfileEditor(requireActivity());
            });
        }

        // TRAY LOGIC
        View settingsTray = view.findViewById(R.id.settings_tray);
        if (hamburgerBtn != null && settingsTray != null) {
            hamburgerBtn.setOnClickListener(v -> {
                settingsTray.setVisibility(View.VISIBLE);
                settingsTray.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_in));
            });

            view.findViewById(R.id.tray_close).setOnClickListener(v -> {
                settingsTray.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_out));
                settingsTray.postDelayed(() -> settingsTray.setVisibility(View.GONE), 300);
            });

            view.findViewById(R.id.tray_settings).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), CustomSettingsFragment.class, CustomSettingsFragment.TAG, null));

            view.findViewById(R.id.tray_runtime).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment.class, "java", null));

            view.findViewById(R.id.tray_accounts).setOnClickListener(v -> openAccountManager());

            if (shareLogsButton != null) {
                shareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
            }
        }
    }

    private void openAccountManager() {
        AccountManagerFragment sheet = new AccountManagerFragment();
        sheet.setOnAccountSelectedListener(account -> {
            Accounts.setCurrent(account);
            refreshAccountUI();
        });
        sheet.show(getChildFragmentManager(), AccountManagerFragment.TAG);
    }

    public void refreshAccountUI() {
        if (mAccountName == null) return;
        MinecraftAccount current = Accounts.getCurrent();
        if (current != null && current.username != null
                && !current.username.isEmpty() && !current.username.equals("0")) {
            mAccountName.setText(current.username);
            String typeLabel = "Local Account";
            if (current.authType != null) {
                switch (current.authType) {
                    case MICROSOFT: typeLabel = "Microsoft Account"; break;
                    case ELY_BY:    typeLabel = "Ely.by Account";    break;
                    default:        typeLabel = "Local Account";     break;
                }
            }
            if (mAccountTypeLabel != null) mAccountTypeLabel.setText(typeLabel);
        } else {
            mAccountName.setText("Add Account");
            if (mAccountTypeLabel != null) mAccountTypeLabel.setText("Tap to manage");
        }
    }

    private void handlePlayButton() {
        MinecraftAccount current = Accounts.getCurrent();
        if (current == null) {
            Toast.makeText(requireContext(), "Please add an account first!", Toast.LENGTH_SHORT).show();
            openAccountManager();
            return;
        }
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void updateVersionText() {
        if (mVersionText == null) return;
        Instance instance = Instances.loadSelectedInstance();
        if (instance != null && instance.versionId != null && !instance.versionId.isEmpty()) {
            mVersionText.setText(instance.versionId);
        } else {
            mVersionText.setText("No version selected");
        }
    }

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
        refreshAccountUI();
        updateVersionText();
    }
}