package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        View newsButton          = view.findViewById(R.id.news_button);
        View discordButton       = null; // view.findViewById(R.id.social_media_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View installJarButton    = view.findViewById(R.id.install_jar_button);
        View shareLogsButton     = view.findViewById(R.id.share_logs_button);
        View openFilesButton     = view.findViewById(R.id.open_files_button);
        ImageView hamburgerBtn   = view.findViewById(R.id.hamburger_menu_icon);
        ImageButton editBtn      = view.findViewById(R.id.edit_profile_button);
        mVersionSpinner          = view.findViewById(R.id.mc_version_spinner);

        // Refresh UI
        refreshAccountUI();
        updateVersionText();

        // ── Account Section Click → Open Account Manager ───────────────
        if (accountSection != null) {
            accountSection.setOnClickListener(v -> openAccountManager());
        }

        // ── Play ───────────────────────────────────────────────────────
        if (playButton != null) {
            playButton.setOnClickListener(v -> handlePlayButton());
        }

        // ── News ───────────────────────────────────────────────────────
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

        // ── Hamburger ──────────────────────────────────────────────────
        if (hamburgerBtn != null) {
            hamburgerBtn.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(),
                            GamepadMapperFragment.class, GamepadMapperFragment.TAG, null));
        }

        // ── Edit Profile ───────────────────────────────────────────────
        if (editBtn != null) {
            editBtn.setOnClickListener(v -> {
                if (mVersionSpinner != null)
                    mVersionSpinner.openProfileEditor(requireActivity());
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Opens Account Manager bottom sheet
    // ─────────────────────────────────────────────────────────────────────────
    private void openAccountManager() {
        // AccountManagerFragment is a BottomSheetDialogFragment
        // It handles account list, selection, add, delete
        AccountManagerFragment sheet = new AccountManagerFragment();
        sheet.setOnAccountSelectedListener(account -> {
            // When user selects an account, set it as current and refresh UI
            Accounts.setCurrent(account);
            refreshAccountUI();
        });
        sheet.show(getChildFragmentManager(), AccountManagerFragment.TAG);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refreshes account name + type label in the top bar
    // ─────────────────────────────────────────────────────────────────────────
    public void refreshAccountUI() {
        if (mAccountName == null) return;

        MinecraftAccount current = Accounts.getCurrent();

        if (current != null && current.username != null
                && !current.username.isEmpty() && !current.username.equals("0")) {
            mAccountName.setText(current.username);

            // Account type
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

    // ─────────────────────────────────────────────────────────────────────────
    // Play button: checks account + instance before launching
    // ─────────────────────────────────────────────────────────────────────────
    private void handlePlayButton() {
        MinecraftAccount current = Accounts.getCurrent();
        if (current == null) {
            // No account — open account manager instead of just showing toast
            Toast.makeText(requireContext(),
                    "Please add an account first!", Toast.LENGTH_SHORT).show();
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

    // ─────────────────────────────────────────────────────────────────────────
    // Updates version text from selected instance
    // ─────────────────────────────────────────────────────────────────────────
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
