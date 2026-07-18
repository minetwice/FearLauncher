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
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
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

    private Runnable mRefreshSkinPaneRunnable = null;

    private final ActivityResultLauncher<String> mSkinPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), (uri) -> {
                if (uri != null) {
                    try {
                        java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                        if (inputStream != null) {
                            File skinsDir = new File(Tools.DIR_GAME_HOME, "skins");
                            if (!skinsDir.exists()) skinsDir.mkdirs();

                            String filename = "custom_skin_" + System.currentTimeMillis() + ".png";
                            File destFile = new File(skinsDir, filename);
                            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile);
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, read);
                            }
                            outputStream.close();
                            inputStream.close();

                            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                            prefs.edit().putString("active_skin_path", destFile.getAbsolutePath()).apply();

                            Toast.makeText(requireContext(), "Skin imported and set as active!", Toast.LENGTH_SHORT).show();

                            if (mRefreshSkinPaneRunnable != null) {
                                mRefreshSkinPaneRunnable.run();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to import skin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final TaskCountListener mPlayStateListener = (taskCount) -> {
        Tools.runOnUiThread(() -> {
            View view = getView();
            if (view != null) {
                View playButton = view.findViewById(R.id.play_button);
                if (playButton instanceof com.kdt.mcgui.MineButton) {
                    com.kdt.mcgui.MineButton mb = (com.kdt.mcgui.MineButton) playButton;
                    if (taskCount > 0) {
                        mb.setText("⏳ LAUNCHING...");
                        mb.setEnabled(false);
                        mb.setAlpha(0.6f);
                    } else {
                        mb.setText("▶  PLAY");
                        mb.setEnabled(true);
                        mb.setAlpha(1.0f);
                    }
                }
            }
        });
        return false;
    };

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
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                if (mVersionSpinner != null)
                    mVersionSpinner.openProfileEditor(requireActivity());
            });
        }

        View accountButton = view.findViewById(R.id.account_button);
        if (accountButton != null) {
            accountButton.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                openAccountManager();
            });
        }

        View notificationButton = view.findViewById(R.id.notification_button);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                int count = ProgressKeeper.getTaskCount();
                Toast.makeText(requireContext(), count == 0 ? "No active notifications." : count + " task(s) running.", Toast.LENGTH_SHORT).show();
            });
        }

        View settingsButton = view.findViewById(R.id.settings_button);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                Tools.swapFragment(requireActivity(), CustomSettingsFragment.class, CustomSettingsFragment.TAG, null);
            });
        }

        // The reference-aligned home no longer exposes a right-side tray; route the menu into the dashboard.
        View settingsTray = view.findViewById(R.id.settings_tray);
        if (hamburgerBtn != null) {
            hamburgerBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                openCommandDashboard();
            });
        }

        View trayClose = view.findViewById(R.id.tray_close);
        if (trayClose != null && settingsTray != null) {
            trayClose.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
            });
        }

        // Drawer Operations Click Listeners
        View trayLogs = view.findViewById(R.id.tray_logs_btn);
        if (trayLogs != null) {
            trayLogs.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                shareLog(requireContext());
            });
        }

        View trayDownloads = view.findViewById(R.id.tray_downloads_btn);
        if (trayDownloads != null) {
            trayDownloads.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                int count = ProgressKeeper.getTaskCount();
                if (count == 0) {
                    Toast.makeText(requireContext(), "No active background downloads.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), count + " background download task(s) active.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View trayNews = view.findViewById(R.id.tray_news_btn);
        if (trayNews != null) {
            trayNews.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                try {
                    android.net.Uri uri = android.net.Uri.parse(getString(R.string.social_media_invite));
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Opening Wiki/Discord invite failed.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View trayMods = view.findViewById(R.id.tray_mods_btn);
        if (trayMods != null) {
            trayMods.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                Bundle bundle = new Bundle();
                bundle.putString("mode", "addon");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }

        View trayResourcePacks = view.findViewById(R.id.tray_resource_packs_btn);
        if (trayResourcePacks != null) {
            trayResourcePacks.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                Instance instance = Instances.loadSelectedInstance();
                if (instance != null) {
                    File rpDir = new File(instance.getGameDirectory(), "resourcepacks");
                    if (rpDir.exists() || rpDir.mkdirs()) {
                        Tools.openPath(requireContext(), rpDir, false);
                    }
                } else {
                    Toast.makeText(requireContext(), "No selected instance to view resource packs.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View trayShaderPacks = view.findViewById(R.id.tray_shader_packs_btn);
        if (trayShaderPacks != null) {
            trayShaderPacks.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                Instance instance = Instances.loadSelectedInstance();
                if (instance != null) {
                    File spDir = new File(instance.getGameDirectory(), "shaderpacks");
                    if (spDir.exists() || spDir.mkdirs()) {
                        Tools.openPath(requireContext(), spDir, false);
                    }
                } else {
                    Toast.makeText(requireContext(), "No selected instance to view shader packs.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View trayControls = view.findViewById(R.id.tray_controls_btn);
        if (trayControls != null) {
            trayControls.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                startActivity(new Intent(requireContext(), CustomControlsActivity.class));
            });
        }

        // Open our Command Dashboard Dialog from Tray Experimental Stuff button
        View traySettings = view.findViewById(R.id.tray_settings_btn);
        if (traySettings != null) {
            traySettings.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                openCommandDashboard();
            });
        }

        // Monitor background tasks to update the Play button states
        ProgressKeeper.addTaskCountListener(mPlayStateListener, true);
    }

    private void collapseTray(View settingsTray) {
        if (settingsTray != null && settingsTray.getVisibility() == View.VISIBLE) {
            Animation slideOut = AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_out);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    settingsTray.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            settingsTray.startAnimation(slideOut);
        }
    }

    private void bindPerformanceStats(View root) {
        TextView fpsTv = root.findViewById(R.id.drawer_perf_fps);
        TextView cpuTv = root.findViewById(R.id.drawer_perf_cpu);
        TextView gpuTv = root.findViewById(R.id.drawer_perf_gpu);
        TextView ramTv = root.findViewById(R.id.drawer_perf_ram);
        TextView memoryTv = root.findViewById(R.id.drawer_perf_memory);
        TextView rendererTv = root.findViewById(R.id.drawer_perf_renderer);

        // FPS
        if (fpsTv != null) {
            try {
                android.view.Display display = requireActivity().getWindowManager().getDefaultDisplay();
                float refreshRate = display.getRefreshRate();
                fpsTv.setText(String.format(java.util.Locale.US, "%.1f FPS", refreshRate));
            } catch (Exception e) {
                fpsTv.setText("60.0 FPS");
            }
        }

        // CPU
        if (cpuTv != null) {
            try {
                String abi = android.os.Build.SUPPORTED_ABIS[0];
                cpuTv.setText(abi.toUpperCase(java.util.Locale.US));
            } catch (Exception e) {
                cpuTv.setText("OCTA-CORE");
            }
        }

        // GPU
        if (gpuTv != null) {
            try {
                net.kdt.pojavlaunch.utils.GLInfoUtils.GLInfo glInfo = net.kdt.pojavlaunch.utils.GLInfoUtils.getGlInfo();
                if (glInfo != null && glInfo.renderer != null && !glInfo.renderer.equals("<Unknown>")) {
                    gpuTv.setText(glInfo.renderer.toUpperCase(java.util.Locale.US));
                } else {
                    gpuTv.setText("ADRENO GPU");
                }
            } catch (Exception e) {
                gpuTv.setText("ADRENO GPU");
            }
        }

        // RAM
        if (ramTv != null) {
            ramTv.setText(LauncherPreferences.PREF_RAM_ALLOCATION + " MB");
        }

        // Memory
        if (memoryTv != null) {
            try {
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                android.app.ActivityManager activityManager = (android.app.ActivityManager) requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(mi);
                double availableGigs = mi.availMem / 1073741824.0; // in GB
                memoryTv.setText(String.format(java.util.Locale.US, "%.1f GB FREE", availableGigs));
            } catch (Exception e) {
                memoryTv.setText("4.2 GB FREE");
            }
        }

        // Renderer
        if (rendererTv != null) {
            try {
                Instance instance = Instances.loadSelectedInstance();
                String rawRenderer = (instance != null) ? instance.getLaunchRenderer() : null;
                rendererTv.setText(getFriendlyRendererName(rawRenderer));
            } catch (Exception e) {
                rendererTv.setText("HOLY GL4ES");
            }
        }
    }

    private String getFriendlyRendererName(String id) {
        if (id == null) return "HOLY GL4ES";
        String idLower = id.toLowerCase(java.util.Locale.US);
        if (idLower.contains("ltw")) return "LTW (GLES 3)";
        if (idLower.contains("fear")) return "FEAR ENGINE";
        if (idLower.contains("vulkan") || idLower.contains("zink")) return "ZINK (VULKAN)";
        if (idLower.contains("freedreno")) return "FREEDRENO (KGSL)";
        if (idLower.contains("angle")) return "ANGLE ENGINE";
        return "HOLY GL4ES";
    }

    private void openCommandDashboard() {
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
        Button navModpacks = dialog.findViewById(R.id.dash_nav_modpacks);
        Button navAddons = dialog.findViewById(R.id.dash_nav_addons);
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
            if (navModpacks != null) {
                navModpacks.setBackgroundResource(R.drawable.premium_glass_black_bg);
                navModpacks.setTextColor(0xFFFFFFFF);
            }
            if (navAddons != null) {
                navAddons.setBackgroundResource(R.drawable.premium_glass_black_bg);
                navAddons.setTextColor(0xFFFFFFFF);
            }
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
            LinearLayout execLayout = new LinearLayout(requireContext());
            execLayout.setOrientation(LinearLayout.VERTICAL);
            execLayout.setPadding(16, 16, 16, 16);
            execLayout.setGravity(android.view.Gravity.CENTER);

            Button launchBtn = new Button(requireContext());
            launchBtn.setText("LAUNCH INSTALLER (.JAR)");
            launchBtn.setBackgroundResource(R.drawable.premium_button_bg);
            launchBtn.setTextColor(0xFF000000);
            launchBtn.setPadding(24, 12, 24, 12);
            launchBtn.setOnClickListener(vLaunch -> {
                dialog.dismiss();
                runInstallerWithConfirmation();
            });

            execLayout.addView(launchBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rightPane.addView(execLayout);
        });

        // SPLIT-PANE 3: SKIN CUSTOMIZER (Zalith & Premium Adaptive)
        navSkin.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navSkin.setBackgroundResource(R.drawable.premium_button_bg);
            navSkin.setTextColor(0xFF000000);

            mRefreshSkinPaneRunnable = () -> {
                rightPane.removeAllViews();
                View skinPane = dialog.getLayoutInflater().inflate(R.layout.premium_skin_customizer_pane, rightPane, false);

                com.kdt.mcgui.MinecraftSkinView currentViewer = skinPane.findViewById(R.id.skin_current_viewer);
                Button btnSteveModel = skinPane.findViewById(R.id.skin_btn_steve_model);
                Button btnAlexModel = skinPane.findViewById(R.id.skin_btn_alex_model);
                LinearLayout libraryContainer = skinPane.findViewById(R.id.skin_library_container);

                android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                final String activeSkinPath = prefs.getString("active_skin_path", "steve");
                final boolean isAlex = prefs.getBoolean("active_skin_is_alex", false);

                currentViewer.loadSkin(activeSkinPath, isAlex);

                java.lang.Runnable updateModelButtonsUI = () -> {
                    boolean currentIsAlex = prefs.getBoolean("active_skin_is_alex", false);
                    if (currentIsAlex) {
                        btnAlexModel.setBackgroundResource(R.drawable.premium_button_bg);
                        btnAlexModel.setTextColor(Color.BLACK);
                        btnSteveModel.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnSteveModel.setTextColor(Color.WHITE);
                    } else {
                        btnSteveModel.setBackgroundResource(R.drawable.premium_button_bg);
                        btnSteveModel.setTextColor(Color.BLACK);
                        btnAlexModel.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnAlexModel.setTextColor(Color.WHITE);
                    }
                };
                updateModelButtonsUI.run();

                btnSteveModel.setOnClickListener(vS -> {
                    vS.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    prefs.edit().putBoolean("active_skin_is_alex", false).apply();
                    updateModelButtonsUI.run();
                    currentViewer.loadSkin(prefs.getString("active_skin_path", "steve"), false);
                });

                btnAlexModel.setOnClickListener(vA -> {
                    vA.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    prefs.edit().putBoolean("active_skin_is_alex", true).apply();
                    updateModelButtonsUI.run();
                    currentViewer.loadSkin(prefs.getString("active_skin_path", "steve"), true);
                });

                libraryContainer.removeAllViews();

                View newSkinCard = dialog.getLayoutInflater().inflate(R.layout.item_premium_library_new_skin, libraryContainer, false);
                newSkinCard.setOnClickListener(vNew -> {
                    vNew.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    mSkinPickerLauncher.launch("image/*");
                });
                libraryContainer.addView(newSkinCard);

                View steveCard = dialog.getLayoutInflater().inflate(R.layout.item_premium_library_skin, libraryContainer, false);
                com.kdt.mcgui.MinecraftSkinView steveViewer = steveCard.findViewById(R.id.item_skin_viewer);
                TextView steveTitle = steveCard.findViewById(R.id.item_skin_title);
                steveTitle.setText("Steve");
                steveViewer.loadSkin("steve", false);
                if ("steve".equalsIgnoreCase(activeSkinPath)) {
                    steveCard.setBackgroundResource(R.drawable.premium_button_bg);
                    steveTitle.setTextColor(Color.BLACK);
                }
                steveCard.setOnClickListener(vSteve -> {
                    vSteve.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    prefs.edit().putString("active_skin_path", "steve").apply();
                    mRefreshSkinPaneRunnable.run();
                });
                libraryContainer.addView(steveCard);

                View alexCard = dialog.getLayoutInflater().inflate(R.layout.item_premium_library_skin, libraryContainer, false);
                com.kdt.mcgui.MinecraftSkinView alexViewer = alexCard.findViewById(R.id.item_skin_viewer);
                TextView alexTitle = alexCard.findViewById(R.id.item_skin_title);
                alexTitle.setText("Alex");
                alexViewer.loadSkin("alex", true);
                if ("alex".equalsIgnoreCase(activeSkinPath)) {
                    alexCard.setBackgroundResource(R.drawable.premium_button_bg);
                    alexTitle.setTextColor(Color.BLACK);
                }
                alexCard.setOnClickListener(vAlex -> {
                    vAlex.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    prefs.edit().putString("active_skin_path", "alex").apply();
                    mRefreshSkinPaneRunnable.run();
                });
                libraryContainer.addView(alexCard);

                File skinsDir = new File(Tools.DIR_GAME_HOME, "skins");
                if (skinsDir.exists() && skinsDir.isDirectory()) {
                    File[] skinFiles = skinsDir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
                    if (skinFiles != null) {
                        for (File f : skinFiles) {
                            View customCard = dialog.getLayoutInflater().inflate(R.layout.item_premium_library_skin, libraryContainer, false);
                            com.kdt.mcgui.MinecraftSkinView customViewer = customCard.findViewById(R.id.item_skin_viewer);
                            TextView customTitle = customCard.findViewById(R.id.item_skin_title);

                            String name = f.getName();
                            if (name.startsWith("custom_skin_")) {
                                name = "<unnamed skin>";
                            } else if (name.endsWith(".png")) {
                                name = name.substring(0, name.length() - 4);
                            }
                            customTitle.setText(name);
                            customViewer.loadSkin(f.getAbsolutePath(), isAlex);

                            if (f.getAbsolutePath().equals(activeSkinPath)) {
                                customCard.setBackgroundResource(R.drawable.premium_button_bg);
                                customTitle.setTextColor(Color.BLACK);
                            }

                            customCard.setOnClickListener(vCust -> {
                                vCust.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                net.kdt.pojavlaunch.SoundManager.playClick();
                                prefs.edit().putString("active_skin_path", f.getAbsolutePath()).apply();
                                mRefreshSkinPaneRunnable.run();
                            });
                            libraryContainer.addView(customCard);
                        }
                    }
                }

                rightPane.addView(skinPane);
            };

            mRefreshSkinPaneRunnable.run();
        });

        // SPLIT-PANE 4: ACCOUNT HUB (Microsoft & Local Offline login panels side-by-side with profile management)
        navAccount.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navAccount.setBackgroundResource(R.drawable.premium_button_bg);
            navAccount.setTextColor(0xFF000000);

            java.lang.Runnable refreshAccountHub = new java.lang.Runnable() {
                private boolean mShowLocalAuth = true;

                @Override
                public void run() {
                    rightPane.removeAllViews();

                    android.widget.ScrollView rootScroll = new android.widget.ScrollView(requireContext());
                    rootScroll.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                    rootScroll.setVerticalScrollBarEnabled(false);

                    LinearLayout accLayout = new LinearLayout(requireContext());
                    accLayout.setOrientation(LinearLayout.VERTICAL);
                    accLayout.setPadding(16, 16, 16, 16);

                    TextView titleAcc = new TextView(requireContext());
                    titleAcc.setText("ACCOUNT COMMAND CENTER");
                    titleAcc.setTextColor(Color.WHITE);
                    titleAcc.setTextSize(14);
                    titleAcc.setTypeface(null, android.graphics.Typeface.BOLD);
                    titleAcc.setPadding(0, 0, 0, 12);
                    accLayout.addView(titleAcc);

                    LinearLayout tabSelector = new LinearLayout(requireContext());
                    tabSelector.setOrientation(LinearLayout.HORIZONTAL);
                    tabSelector.setPadding(0, 0, 0, 16);

                    Button btnLocalTab = new Button(requireContext());
                    btnLocalTab.setText("OFFLINE LOCAL");
                    btnLocalTab.setTextSize(10);

                    Button btnMsTab = new Button(requireContext());
                    btnMsTab.setText("MICROSOFT LOGIN");
                    btnMsTab.setTextSize(10);

                    if (mShowLocalAuth) {
                        btnLocalTab.setBackgroundResource(R.drawable.premium_button_bg);
                        btnLocalTab.setTextColor(Color.BLACK);
                        btnMsTab.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnMsTab.setTextColor(Color.WHITE);
                    } else {
                        btnLocalTab.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnLocalTab.setTextColor(Color.WHITE);
                        btnMsTab.setBackgroundResource(R.drawable.premium_button_bg);
                        btnMsTab.setTextColor(Color.BLACK);
                    }

                    btnLocalTab.setOnClickListener(vLocalTab -> {
                        vLocalTab.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        mShowLocalAuth = true;
                        this.run();
                    });

                    btnMsTab.setOnClickListener(vMsTab -> {
                        vMsTab.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        mShowLocalAuth = false;
                        this.run();
                    });

                    LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    tabLp.setMargins(4, 4, 4, 4);
                    tabSelector.addView(btnLocalTab, tabLp);
                    tabSelector.addView(btnMsTab, tabLp);
                    accLayout.addView(tabSelector);

                    if (mShowLocalAuth) {
                        final EditText inputUser = new EditText(requireContext());
                        inputUser.setHint("ENTER OFFLINE USERNAME...");
                        inputUser.setTextColor(Color.WHITE);
                        inputUser.setHintTextColor(0x80FFFFFF);
                        inputUser.setBackgroundResource(R.drawable.premium_edit_bg);
                        inputUser.setPadding(12, 12, 12, 12);
                        accLayout.addView(inputUser, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                        View space2 = new View(requireContext());
                        accLayout.addView(space2, new LinearLayout.LayoutParams(1, 12));

                        Button btnLocalLogin = new Button(requireContext());
                        btnLocalLogin.setText("LOG IN OFFLINE");
                        btnLocalLogin.setBackgroundResource(R.drawable.premium_button_bg);
                        btnLocalLogin.setTextColor(Color.BLACK);
                        btnLocalLogin.setOnClickListener(vL -> {
                            vL.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                            net.kdt.pojavlaunch.SoundManager.playClick();
                            String name = inputUser.getText().toString().trim();
                            if (name.isEmpty() || name.length() < 3) {
                                Toast.makeText(requireContext(), "Username must be at least 3 characters", Toast.LENGTH_SHORT).show();
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
                                this.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(requireContext(), "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                        accLayout.addView(btnLocalLogin, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    } else {
                        TextView msDesc = new TextView(requireContext());
                        msDesc.setText("Connect your official Microsoft / Xbox Live account securely via the safe web portal.");
                        msDesc.setTextColor(0xCCFFFFFF);
                        msDesc.setTextSize(11);
                        msDesc.setPadding(4, 4, 4, 16);
                        accLayout.addView(msDesc);

                        Button btnMsLogin = new Button(requireContext());
                        btnMsLogin.setText("LOG IN WITH MICROSOFT");
                        btnMsLogin.setBackgroundResource(R.drawable.premium_button_bg);
                        btnMsLogin.setTextColor(Color.BLACK);
                        btnMsLogin.setOnClickListener(vM -> {
                            vM.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                            net.kdt.pojavlaunch.SoundManager.playClick();
                            dialog.dismiss();
                            Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
                        });
                        accLayout.addView(btnMsLogin, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    View space3 = new View(requireContext());
                    accLayout.addView(space3, new LinearLayout.LayoutParams(1, 24));

                    TextView profilesHeader = new TextView(requireContext());
                    profilesHeader.setText("SAVED ACCOUNT PROFILES");
                    profilesHeader.setTextColor(0x80FFFFFF);
                    profilesHeader.setTextSize(10);
                    profilesHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                    profilesHeader.setPadding(0, 0, 0, 8);
                    accLayout.addView(profilesHeader);

                    try {
                        net.kdt.pojavlaunch.authenticator.accounts.Accounts loadedAccounts = net.kdt.pojavlaunch.authenticator.accounts.Accounts.load();
                        java.util.List<net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount> accountList = loadedAccounts.accounts;
                        net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount current = net.kdt.pojavlaunch.authenticator.accounts.Accounts.getCurrent();
                        final String currentName = (current != null) ? current.mSaveLocation.getName() : "";

                        if (accountList.isEmpty()) {
                            TextView emptyTv = new TextView(requireContext());
                            emptyTv.setText("No saved accounts found.");
                            emptyTv.setTextColor(0x60FFFFFF);
                            emptyTv.setTextSize(11);
                            accLayout.addView(emptyTv);
                        } else {
                            for (net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount acc : accountList) {
                                LinearLayout row = new LinearLayout(requireContext());
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                row.setPadding(8, 8, 8, 8);

                                boolean isSelected = acc.mSaveLocation != null && acc.mSaveLocation.getName().equals(currentName);
                                if (isSelected) {
                                    row.setBackgroundResource(R.drawable.premium_button_bg);
                                } else {
                                    row.setBackgroundResource(R.drawable.premium_glass_black_bg);
                                }

                                LinearLayout textCol = new LinearLayout(requireContext());
                                textCol.setOrientation(LinearLayout.VERTICAL);

                                TextView uTv = new TextView(requireContext());
                                uTv.setText(acc.username);
                                uTv.setTextColor(isSelected ? Color.BLACK : Color.WHITE);
                                uTv.setTextSize(12);
                                uTv.setTypeface(null, android.graphics.Typeface.BOLD);

                                TextView tTv = new TextView(requireContext());
                                String type = "Local";
                                if (acc.authType != null) {
                                    switch (acc.authType) {
                                        case MICROSOFT: type = "Microsoft"; break;
                                        case ELY_BY:    type = "Ely.by";    break;
                                        default:        type = "Local";     break;
                                    }
                                }
                                tTv.setText(type);
                                tTv.setTextColor(isSelected ? 0x80000000 : 0x80FFFFFF);
                                tTv.setTextSize(9);

                                textCol.addView(uTv);
                                textCol.addView(tTv);

                                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                                row.addView(textCol, rowLp);

                                ImageButton btnDel = new ImageButton(requireContext());
                                btnDel.setImageResource(R.drawable.ic_px_trash);
                                btnDel.setBackgroundColor(Color.TRANSPARENT);
                                if (isSelected) {
                                    btnDel.setColorFilter(Color.BLACK);
                                } else {
                                    btnDel.setColorFilter(Color.WHITE);
                                }
                                btnDel.setOnClickListener(vDel -> {
                                    vDel.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                    net.kdt.pojavlaunch.SoundManager.playClick();
                                    net.kdt.pojavlaunch.authenticator.accounts.Accounts.delete(acc);
                                    refreshAccountUI();
                                    this.run();
                                    Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show();
                                });
                                row.addView(btnDel);

                                row.setOnClickListener(vRow -> {
                                    vRow.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                    net.kdt.pojavlaunch.SoundManager.playClick();
                                    net.kdt.pojavlaunch.authenticator.accounts.Accounts.setCurrent(acc);
                                    refreshAccountUI();
                                    this.run();
                                });

                                LinearLayout.LayoutParams rowOuterLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                rowOuterLp.setMargins(0, 4, 0, 4);
                                accLayout.addView(row, rowOuterLp);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    rootScroll.addView(accLayout);
                    rightPane.addView(rootScroll);
                }
            };

            refreshAccountHub.run();
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

        // SPLIT-PANE 6: MODPACK ENGINE (MODPACKS)
        if (navModpacks != null) {
            navModpacks.setOnClickListener(v2 -> {
                v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                resetNavButtons.run();
                navModpacks.setBackgroundResource(R.drawable.premium_button_bg);
                navModpacks.setTextColor(0xFF000000);

                rightPane.removeAllViews();
                dialog.dismiss();
                Bundle bundle = new Bundle();
                bundle.putString("mode", "modpack");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }

        // SPLIT-PANE 7: ADDON INSTALLER (MODS, SHADERS, RESOURCE PACKS)
        if (navAddons != null) {
            navAddons.setOnClickListener(v2 -> {
                v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                resetNavButtons.run();
                navAddons.setBackgroundResource(R.drawable.premium_button_bg);
                navAddons.setTextColor(0xFF000000);

                rightPane.removeAllViews();
                dialog.dismiss();
                Bundle bundle = new Bundle();
                bundle.putString("mode", "addon");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }


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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mPlayStateListener);
    }
}