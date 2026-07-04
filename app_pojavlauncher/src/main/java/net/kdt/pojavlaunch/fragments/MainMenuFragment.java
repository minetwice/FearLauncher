package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    // Display views for the new layout
    private TextView mAccountNameDisplay;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Logic bindings (invisible dummies)
        mAccountName      = view.findViewById(R.id.account_name);
        mAccountTypeLabel = view.findViewById(R.id.account_type_label);
        mVersionText      = view.findViewById(R.id.version_text);

        // Display bindings
        mAccountNameDisplay = view.findViewById(R.id.account_name_display);

        // Buttons
        View playButton          = view.findViewById(R.id.play_button);
        View hamburgerBtn        = view.findViewById(R.id.hamburger_menu_icon);
        View accountSectionMain  = view.findViewById(R.id.account_section_main);
        mVersionSpinner          = view.findViewById(R.id.mc_version_spinner);

        // Sidebar Items
        View traySettingsBtn = view.findViewById(R.id.tray_settings_btn);
        View trayExecuteJarBtn = view.findViewById(R.id.tray_execute_jar_btn);
        View traySkinBtn = view.findViewById(R.id.tray_skin_btn);
        View trayAccountBtn = view.findViewById(R.id.tray_account_btn);
        View moreSettingsToggle = view.findViewById(R.id.more_settings_toggle);
        LinearLayout moreSettingsLayout = view.findViewById(R.id.more_settings_layout);

        // More Sidebar Sub-items
        View trayControlsBtn = view.findViewById(R.id.tray_controls_btn);
        View trayModsBtn = view.findViewById(R.id.tray_mods_btn);
        View trayLogsBtn = view.findViewById(R.id.tray_logs_btn);
        View trayOpenDirBtn = view.findViewById(R.id.tray_open_dir_btn);

        // Refresh UI
        refreshAccountUI();
        updateVersionText();

        if (playButton != null) {
            playButton.setOnClickListener(v -> handlePlayButton());
        }

        if (accountSectionMain != null) {
            accountSectionMain.setOnClickListener(v -> openAccountManager());
        }

        // Sidebar Actions
        if (traySettingsBtn != null) {
            traySettingsBtn.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), CustomSettingsFragment.class, CustomSettingsFragment.TAG, null));
        }

        if (trayExecuteJarBtn != null) {
            trayExecuteJarBtn.setOnClickListener(v -> runInstallerWithConfirmation());
        }

        if (traySkinBtn != null || trayAccountBtn != null) {
            View.OnClickListener accountListener = v -> openAccountManager();
            if (traySkinBtn != null) traySkinBtn.setOnClickListener(accountListener);
            if (trayAccountBtn != null) trayAccountBtn.setOnClickListener(accountListener);
        }

        if (trayControlsBtn != null) {
            trayControlsBtn.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        if (trayModsBtn != null) {
            trayModsBtn.setOnClickListener(v -> Toast.makeText(getContext(), "Mods Menu Coming Soon", Toast.LENGTH_SHORT).show());
        }

        if (trayLogsBtn != null) {
            trayLogsBtn.setOnClickListener(v -> shareLog(requireContext()));
        }

        if (trayOpenDirBtn != null) {
            trayOpenDirBtn.setOnClickListener(v -> openGameDirectory(v.getContext()));
        }

        if (moreSettingsToggle != null && moreSettingsLayout != null) {
            moreSettingsToggle.setOnClickListener(v -> {
                if (moreSettingsLayout.getVisibility() == View.VISIBLE) {
                    moreSettingsLayout.setVisibility(View.GONE);
                } else {
                    moreSettingsLayout.setVisibility(View.VISIBLE);
                    animateItemsSequentially(moreSettingsLayout, R.anim.item_fade_in);
                }
            });
        }

        // TRAY SLIDE LOGIC (Right to Left)
        final View settingsTray = view.findViewById(R.id.settings_tray);
        if (hamburgerBtn != null && settingsTray != null) {
            hamburgerBtn.setOnClickListener(v -> {
                if (settingsTray.getVisibility() == View.VISIBLE) {
                    // Close animation: Down to Up
                    animateItemsSequentially((ViewGroup) ((ViewGroup) settingsTray).getChildAt(0), R.anim.item_slide_out_up);
                    settingsTray.animate()
                            .translationX(settingsTray.getWidth())
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> settingsTray.setVisibility(View.GONE))
                            .start();
                } else {
                    // Open animation: Right to Left
                    settingsTray.setVisibility(View.VISIBLE);
                    settingsTray.setAlpha(0f);
                    settingsTray.setTranslationX(settingsTray.getWidth() > 0 ? settingsTray.getWidth() : 1000f);
                    settingsTray.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(400)
                            .withEndAction(() -> {
                                ViewGroup container = (ViewGroup) ((ViewGroup) settingsTray).getChildAt(0);
                                animateItemsSequentially(container, R.anim.item_fade_in);
                            })
                            .start();
                }
            });

            view.findViewById(R.id.tray_close).setOnClickListener(v -> {
                 animateItemsSequentially((ViewGroup) ((ViewGroup) settingsTray).getChildAt(0), R.anim.item_slide_out_up);
                 settingsTray.animate()
                        .translationX(settingsTray.getWidth())
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> settingsTray.setVisibility(View.GONE))
                        .start();
            });
        }
    }

    private void animateItemsSequentially(ViewGroup container, int animRes) {
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child instanceof com.kdt.mcgui.LauncherMenuButton || child instanceof TextView) {
                Animation anim = AnimationUtils.loadAnimation(requireContext(), animRes);
                anim.setStartOffset(i * 50L);
                child.startAnimation(anim);
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
        MinecraftAccount current = Accounts.getCurrent();
        String username = "ADD ACCOUNT";
        String typeLabel = "Tap to manage";

        if (current != null && current.username != null
                && !current.username.isEmpty() && !current.username.equals("0")) {
            username = current.username.toUpperCase();
            if (current.authType != null) {
                switch (current.authType) {
                    case MICROSOFT: typeLabel = "Microsoft Account"; break;
                    case ELY_BY:    typeLabel = "Ely.by Account";    break;
                    default:        typeLabel = "Local Account";     break;
                }
            }
        }

        if (mAccountName != null) mAccountName.setText(username);
        if (mAccountTypeLabel != null) mAccountTypeLabel.setText(typeLabel);

        if (mAccountNameDisplay != null) mAccountNameDisplay.setText(username);
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
        Instance instance = Instances.loadSelectedInstance();
        String version = "1.21.1";
        if (instance != null && instance.versionId != null && !instance.versionId.isEmpty()) {
            version = instance.versionId;
        }

        if (mVersionText != null) mVersionText.setText(version);
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
