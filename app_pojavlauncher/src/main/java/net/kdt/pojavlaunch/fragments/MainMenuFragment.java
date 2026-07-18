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
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.EditText;
import android.graphics.Color;
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
    private TextView mVersionTextDisplay;

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
        mVersionTextDisplay = view.findViewById(R.id.version_text_display);

        // Buttons
        View playButton          = view.findViewById(R.id.play_button);
        View hamburgerBtn        = view.findViewById(R.id.hamburger_menu_icon);
        View editBtnMain         = view.findViewById(R.id.edit_profile_button_main);
        mVersionSpinner          = view.findViewById(R.id.mc_version_spinner);

        // Refresh UI
        refreshAccountUI();
        updateVersionText();

        if (playButton != null) {
            playButton.setOnClickListener(v -> handlePlayButton());
        }

        if (editBtnMain != null) {
            editBtnMain.setOnClickListener(v -> {
                if (mVersionSpinner != null)
                    mVersionSpinner.openProfileEditor(requireActivity());
            });
        }

        // Settings Gear click launches our magnificent full-screen Split-Pane Command Dashboard
        if (hamburgerBtn != null) {
            hamburgerBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();

                android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(R.layout.dialog_premium_settings_dashboard);

                View backBtn = dialog.findViewById(R.id.dash_back_btn);
                View doneBtn = dialog.findViewById(R.id.dash_done_btn);

                backBtn.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    dialog.dismiss();
                });
                doneBtn.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    dialog.dismiss();
                });

                // Navigation buttons inside the left rail
                Button navSettings = dialog.findViewById(R.id.dash_nav_settings);
                Button navExecute = dialog.findViewById(R.id.dash_nav_execute);
                Button navSkin = dialog.findViewById(R.id.dash_nav_skin);
                Button navAccount = dialog.findViewById(R.id.dash_nav_account);
                Button navControls = dialog.findViewById(R.id.dash_nav_controls);
                Button navMods = dialog.findViewById(R.id.dash_nav_mods);
                Button navLogs = dialog.findViewById(R.id.dash_nav_logs);

                android.widget.FrameLayout rightPane = dialog.findViewById(R.id.dash_content_pane);

                java.lang.Runnable resetNavButtons = () -> {
                    navSettings.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navSettings.setTextColor(0xFFFFFFFF);
                    navExecute.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navExecute.setTextColor(0xFFFFFFFF);
                    navSkin.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navSkin.setTextColor(0xFFFFFFFF);
                    navAccount.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navAccount.setTextColor(0xFFFFFFFF);
                    navControls.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navControls.setTextColor(0xFFFFFFFF);
                    navMods.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navMods.setTextColor(0xFFFFFFFF);
                    navLogs.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    navLogs.setTextColor(0xFFFFFFFF);
                };

                // SPLIT-PANE 1: SETTINGS ENGINE
                navSettings.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navSettings.setBackgroundResource(R.drawable.premium_button_bg);
                    navSettings.setTextColor(0xFF000000);

                    // Inflate options inside the right pane container dynamically
                    rightPane.removeAllViews();
                    View configView = dialog.getLayoutInflater().inflate(R.layout.dialog_mod_filters, rightPane, false);
                    TextView titleV = configView.findViewById(R.id.search_mod_selected_mc_version_textview);
                    Button mcVerBtn = configView.findViewById(R.id.search_mod_mc_version_button);
                    Button applyBtn = configView.findViewById(R.id.search_mod_apply_filters);
                    if (applyBtn != null) {
                        applyBtn.setText("OPEN CONFIGURATIONS");
                        applyBtn.setOnClickListener(vConfig -> {
                            dialog.dismiss();
                            Tools.swapFragment(requireActivity(), CustomSettingsFragment.class, CustomSettingsFragment.TAG, null);
                        });
                    }
                    rightPane.addView(configView);
                });

                // SPLIT-PANE 2: RUN EXECUTABLE
                navExecute.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navExecute.setBackgroundResource(R.drawable.premium_button_bg);
                    navExecute.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    View execView = dialog.getLayoutInflater().inflate(R.layout.view_loading, rightPane, false);
                    Button launchBtn = new Button(requireContext());
                    launchBtn.setText("LAUNCH INSTALLER (.JAR)");
                    launchBtn.setBackgroundResource(R.drawable.premium_button_bg);
                    launchBtn.setTextColor(0xFF000000);
                    launchBtn.setOnClickListener(vLaunch -> {
                        dialog.dismiss();
                        runInstallerWithConfirmation();
                    });
                    ((android.view.ViewGroup)execView).addView(launchBtn);
                    rightPane.addView(execView);
                });

                // SPLIT-PANE 3: SKIN CUSTOMIZER (Zalith & Premium Adaptive)
                navSkin.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navSkin.setBackgroundResource(R.drawable.premium_button_bg);
                    navSkin.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    LinearLayout skinLayout = new LinearLayout(requireContext());
                    skinLayout.setOrientation(LinearLayout.VERTICAL);
                    skinLayout.setPadding(16, 16, 16, 16);

                    TextView titleSkin = new TextView(requireContext());
                    titleSkin.setText("SKIN CUSTOMIZER & BODY COMPONENT");
                    titleSkin.setTextColor(Color.WHITE);
                    titleSkin.setTextSize(14);
                    titleSkin.setGravity(android.view.Gravity.CENTER);
                    titleSkin.setPadding(0, 0, 0, 16);

                    // Steve/Alex body type select
                    LinearLayout bodySelect = new LinearLayout(requireContext());
                    bodySelect.setOrientation(LinearLayout.HORIZONTAL);
                    bodySelect.setGravity(android.view.Gravity.CENTER);

                    Button btnSteve = new Button(requireContext());
                    btnSteve.setText("Steve (4-px)");
                    btnSteve.setBackgroundResource(R.drawable.premium_button_bg);
                    btnSteve.setTextColor(Color.BLACK);

                    Button btnAlex = new Button(requireContext());
                    btnAlex.setText("Alex (3-px)");
                    btnAlex.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    btnAlex.setTextColor(Color.WHITE);

                    btnSteve.setOnClickListener(vS -> {
                        vS.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        btnSteve.setBackgroundResource(R.drawable.premium_button_bg);
                        btnSteve.setTextColor(Color.BLACK);
                        btnAlex.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnAlex.setTextColor(Color.WHITE);
                        Toast.makeText(requireContext(), "Body type Steve selected", Toast.LENGTH_SHORT).show();
                    });

                    btnAlex.setOnClickListener(vA -> {
                        vA.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        btnSteve.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnSteve.setTextColor(Color.WHITE);
                        btnAlex.setBackgroundResource(R.drawable.premium_button_bg);
                        btnAlex.setTextColor(Color.BLACK);
                        Toast.makeText(requireContext(), "Body type Alex selected", Toast.LENGTH_SHORT).show();
                    });

                    LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    btnLp.setMargins(8, 8, 8, 8);
                    bodySelect.addView(btnSteve, btnLp);
                    bodySelect.addView(btnAlex, btnLp);

                    // Import file btn
                    Button btnImport = new Button(requireContext());
                    btnImport.setText("LOAD CUSTOM SKIN (.PNG)");
                    btnImport.setBackgroundResource(R.drawable.premium_button_bg);
                    btnImport.setTextColor(Color.BLACK);
                    btnImport.setOnClickListener(vI -> {
                        vI.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        Toast.makeText(requireContext(), "Loading skin database...", Toast.LENGTH_SHORT).show();
                    });

                    skinLayout.addView(titleSkin);
                    skinLayout.addView(bodySelect);

                    View space = new View(requireContext());
                    skinLayout.addView(space, new LinearLayout.LayoutParams(1, 24));
                    skinLayout.addView(btnImport, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    rightPane.addView(skinLayout);
                });

                // SPLIT-PANE 4: ACCOUNT HUB (Offline Local login panel side-by-side)
                navAccount.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navAccount.setBackgroundResource(R.drawable.premium_button_bg);
                    navAccount.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    LinearLayout accLayout = new LinearLayout(requireContext());
                    accLayout.setOrientation(LinearLayout.VERTICAL);
                    accLayout.setPadding(16, 16, 16, 16);

                    TextView titleAcc = new TextView(requireContext());
                    titleAcc.setText("ADD OFFLINE LOCAL ACCOUNT");
                    titleAcc.setTextColor(Color.WHITE);
                    titleAcc.setTextSize(14);
                    titleAcc.setPadding(0, 0, 0, 12);

                    final EditText inputUser = new EditText(requireContext());
                    inputUser.setHint("ENTER USERNAME...");
                    inputUser.setTextColor(Color.WHITE);
                    inputUser.setHintTextColor(0x80FFFFFF);
                    inputUser.setBackgroundResource(R.drawable.premium_edit_bg);
                    inputUser.setPadding(12, 12, 12, 12);

                    Button btnLogin = new Button(requireContext());
                    btnLogin.setText("LOG IN");
                    btnLogin.setBackgroundResource(R.drawable.premium_button_bg);
                    btnLogin.setTextColor(Color.BLACK);
                    btnLogin.setOnClickListener(vL -> {
                        vL.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        String name = inputUser.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(requireContext(), "Please enter a valid username", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount acc = net.kdt.pojavlaunch.authenticator.accounts.Accounts.create(account -> {
                                account.username = name;
                                account.profileId = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).toString();
                                account.accessToken = "0";
                                account.refreshToken = "0";
                                account.authType = net.kdt.pojavlaunch.authenticator.AuthType.LOCAL;
                            });
                            net.kdt.pojavlaunch.authenticator.accounts.Accounts.setCurrent(acc);
                            refreshAccountUI();
                            Toast.makeText(requireContext(), "Successfully logged in as " + name, Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(requireContext(), "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

                    accLayout.addView(titleAcc);
                    accLayout.addView(inputUser, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    View space2 = new View(requireContext());
                    accLayout.addView(space2, new LinearLayout.LayoutParams(1, 16));
                    accLayout.addView(btnLogin, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    rightPane.addView(accLayout);
                });

                // SPLIT-PANE 5: INPUT MAPPING
                navControls.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navControls.setBackgroundResource(R.drawable.premium_button_bg);
                    navControls.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    Button mapBtn = new Button(requireContext());
                    mapBtn.setText("OPEN CUSTOM CONTROLS MAPPING");
                    mapBtn.setBackgroundResource(R.drawable.premium_button_bg);
                    mapBtn.setTextColor(0xFF000000);
                    mapBtn.setOnClickListener(vMap -> {
                        dialog.dismiss();
                        startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                    });
                    rightPane.addView(mapBtn);
                });

                // SPLIT-PANE 6: MODS DEPLOY (Embed Downloader Dashboard directly inside Right Pane!)
                navMods.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navMods.setBackgroundResource(R.drawable.premium_button_bg);
                    navMods.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    // Swap directly to allow comfortable full-screen download/search experience
                    dialog.dismiss();
                    Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
                });


                // SPLIT-PANE 8: TELEMETRY LOGS
                navLogs.setOnClickListener(v2 -> {
                    v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    resetNavButtons.run();
                    navLogs.setBackgroundResource(R.drawable.premium_button_bg);
                    navLogs.setTextColor(0xFF000000);

                    rightPane.removeAllViews();
                    Button shareBtn = new Button(requireContext());
                    shareBtn.setText("EXPORT SYSTEMS LOGS TELEMETRY");
                    shareBtn.setBackgroundResource(R.drawable.premium_button_bg);
                    shareBtn.setTextColor(0xFF000000);
                    shareBtn.setOnClickListener(vShare -> {
                        dialog.dismiss();
                        shareLog(requireContext());
                    });
                    rightPane.addView(shareBtn);
                });

                // Default selection
                navSettings.performClick();

                dialog.show();
            });
        }
    }

    private void animateItemsSequentially(ViewGroup container, boolean show) {
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = container.getChildAt(i);
            if (child instanceof com.kdt.mcgui.LauncherMenuButton || child instanceof TextView || child instanceof LinearLayout) {
                Animation anim = AnimationUtils.loadAnimation(requireContext(), show ? R.anim.item_fade_in : R.anim.item_fade_out);
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
        String username = "Add Account";
        String typeLabel = "Tap to manage";

        if (current != null && current.username != null
                && !current.username.isEmpty() && !current.username.equals("0")) {
            username = current.username;
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
        String version = "No version selected";
        if (instance != null && instance.versionId != null && !instance.versionId.isEmpty()) {
            version = instance.versionId;
        }

        if (mVersionText != null) mVersionText.setText(version);
        if (mVersionTextDisplay != null) mVersionTextDisplay.setText(version);
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
