package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tols.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
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
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclernview.widget.LinearLayoutManager;

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

    private static final String[] CHAT_MESSAGES = {
        "‚èΩ [SYSTEM] Boot sequence initialized successfully.",
        "‚éà [SYSTEM] Active rendering pipeline: FEAR CORE.",
        "‚õã [SYSTEM] Checking hardware specifications: OK.",
        "‚öô [CORE] JRE execution parameters: LOCKED.",
        "‚ö° [CORE] Mesa driver emulation layer: ACTIVE.",
        "üñ¥ [CORE] Memory allocation optimization: LOCKED.",
        "üõ° [GPU] Context wrapper glMemoryBarrier: SAFE.",
        "‚ú¶ [GPU] Nearest-neighbor texture scaling: ACTIVE.",
        "üñß [NET] Local metadata server proxy running on 25599.",
        "üóù [NET] Handshaking Yggdrasil API textures: OK.",
        "üéÆ [INPUT] Virtual touch controller layout: LOADED.",
        "‚áó [INPUT] Mouse pointer acceleration speed: OPTIMIZED.",
        "üì° [SYSTEM] Telemetry services: STANDBY."
    };

    private android.animation.ValueAnimator mHeadRotationAnimator;
    private android.animation.ValueAnimator mSkinRotationAnimator;
    private android.os.Handler mChatBubbleHandler;
    private java.lang.Runnable mChatBubbleRunnable;

    // Custom continuous looping advancement announcements (Step 3)
    private int mAnnouncementIndex = 0;
    private android.os.Handler mAnnouncementHandler;
    private java.lang.Runnable mAnnouncementRunnable;

    private mcVersionSpinner mVersionSpinner;
    private TextView mAccountName;
    private View mRootView;
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
                        mb.setText("LAUNCHING...");
                        mb.setEnabled(false);
                        mb.setAlpha(0.6f);
                    } else {
                        mb.setText("PLAY");
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
        mRootView = view;

        // Auto sync active skin to Minecraft resource packs on boot (Step 2)
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
            syncSkinToMinecraftResourcePack(requireContext(), prefs.getString("active_skin_path", "steve"));
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        View settingsBtnMain     = view.findViewById(R.id.settings_button_main);
        View headerAvatarCard    = view.findViewById(R.id.header_avatar_card);
        View headerNotificationBtn = view.findViewById(R.id.header_notification_btn);
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

        if (settingsBtnMain != null) {
            settingsBtnMain.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                openCommandDashboard();
            });
        }

        if (headerAvatarCard != null) {
            headerAvatarCard.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                openCommandDashboard(); // Open configuration center showing Accounts (or other tabs)
            });
        }

        View headerAccountHubBtn = view.findViewById(R.id.header_account_hub_btn);
        if (headerAccountHubBtn != null) {
            headerAccountHubBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                openAccountManager();
            });
        }

        if (headerNotificationBtn != null) {
            headerNotificationBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                com.kdt.mcgui.ProgressLayout pl = view.findViewById(R.id.progress_layout);
                if (pl != null) {
                    pl.setVisibility(View.VISIBLE);
                    pl.onClick(pl);
                } else {
                    Toast.makeText(requireContext(), "NO ACTIVE DOWNLOAD TASKS.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Sliding Drawer (settings_tray) bindings and trigger logic
        View settingsTray = view.findViewById(R.id.settings_tray);
        if (hamburgerBtn != null && settingsTray != null) {
            hamburgerBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();

                if (settingsTray.getVisibility() != View.VISIBLE) {
                    settingsTray.setVisibility(View.VISIBLE);
                    Animation slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_in);
                    settingsTray.startAnimation(slideIn);
                    bindPerformanceStats(view);
                } else {
                    collapseTray(settingsTray);
                }
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
                com.kdt.mcgui.ProgressLayout plD = view.findViewById(R.id.progress_layout);
                if (plD != null) {
                    plD.setVisibility(View.VISIBLE);
                    plD.onClick(plD);
                } else {
                    Toast.makeText(requireContext(), "No active background downloads.", Toast.LENGTH_SHORT).show();
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
                bundle.putString("initial_category", "mods");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }

        View trayResourcePacks = view.findViewById(R.id.tray_resource_packs_btn);
        if (trayResourcePacks != null) {
            trayResourcePacks.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                Bundle bundle = new Bundle();
                bundle.putString("mode", "addon");
                bundle.putString("initial_category", "resourcepacks");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }

        View trayShaderPacks = view.findViewById(R.id.tray_shader_packs_btn);
        if (trayShaderPacks != null) {
            trayShaderPacks.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                Bundle bundle = new Bundle();
                bundle.putString("mode", "addon");
                bundle.putString("initial_category", "shaders");
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
            });
        }

        View traySkin = view.findViewById(R.id.tray_skin_btn);
        if (traySkin != null) {
            traySkin.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                openCommandDashboard("skin");
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

        // Open our Creators Info Dialog from Tray Info button
        View trayInfo = view.findViewById(R.id.tray_info_btn);
        if (trayInfo != null) {
            trayInfo.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                openCreatorsInfoDialog();
            });
        }

        // Setup custom looping advancement announcements (Step 3)
        mAnnouncementHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mAnnouncementRunnable = new java.lang.Runnable() {
            @Override
            public void run() {
                showAdvancementAnnouncement();
                mAnnouncementHandler.postDelayed(this, 20000); // Trigger every 20 seconds
            }
        };
        // Trigger first announcement after 5 seconds
        mAnnouncementHandler.postDelayed(mAnnouncementRunnable, 5000);

        // Apply theme color accents instantly upon creation (Step 3)
        applyThemeColors(view);

        // Monitor background tasks to update the Play button states
        ProgressKeeper.addTaskCountListener(mPlayStateListener, true);
        // Wire the in-flow ProgressLayout (moved from LauncherActivity into the fragment layout)
        com.kdt.mcgui.ProgressLayout plWire = view.findViewById(R.id.progress_layout);
        if (plWire != null) {
            ProgressKeeper.addTaskCountListener(plWire);
            plWire.observe(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT);
            plWire.observe(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME);
            plWire.observe(com.kdt.mcgui.ProgressLayout.INSTALL_MODPACK);
            plWire.observe(com.kdt.mcgui.ProgressLayout.AUTHENTICATE);
            plWire.observe(com.kdt.mcgui.ProgressLayout.DOWNLOAD_VERSION_LIST);
            plWire.observe(com.kdt.mcgui.ProgressLayout.INSTANCE_INSTALL);
        }
    }

    private void playChallengeSound() {
        try {
            android.net.Uri notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(requireContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAdvancementAnnouncement() {
        if (getView() == null) return;
        View advToast = getView().findViewById(R.id.advancement_toast_layout);
        TextView advMsg = getView().findViewById(R.id.advancement_message);
        android.widget.ImageView advIcon = getView().findViewById(R.id.advancement_icon);
        if (advToast == null || advMsg == null) return;

        // Modulo 4 cycle of announcements (YouTube Twicefear, YouTube Hellzior, Discord Twicefear, Discord Hellzior)
        final int state = mAnnouncementIndex % 4;
        mAnnouncementIndex++;

        final String text;
        final String url;
        final boolean isDiscord;

        switch (state) {
            case 0:
                text = "Subscribe to twicefear";
                url = "https://youtube.com/@twicefear3?si=kg3P4rhdTFennJf_";
                isDiscord = false;
                break;
            case 1:
                text = "Subscribe to hellzior";
                url = "https://youtube.com/@hellzior01?si=EFIdj3J2JATCyP2k";
                isDiscord = false;
                break;
            case 2:
                text = "Join Twicefear's Discord";
                url = "https://discord.gg/NGMjxn9a7";
                isDiscord = true;
                break;
            case 3:
            default:
                text = "Join Hellzior's Discord";
                url = "https://discord.gg/bsGtVV5sk";
                isDiscord = true;
                break;
        }

        advMsg.setText(text);
        if (advIcon != null) {
            advIcon.setImageResource(isDiscord ? R.drawable.ic_discord : R.drawable.ic_youtube_logo);
        }

        // Position it completely off-screen to start BEFORE making it visible (prevents static flicker)
        float startX = advToast.getWidth() > 0 ? advToast.getWidth() + 200f : 1000f;
        advToast.setTranslationX(startX);
        advToast.setVisibility(View.VISIBLE);

        // Slide in animation with OvershootInterpolator for premium bouncy touch
        advToast.animate()
                .translationX(0f)
                .setDuration(800)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .withEndAction(() -> {
                    // Silent display as requested - no notification sound trigger

                    // Set click listener to open the correct YouTube channel or Discord link
                    advToast.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                            startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    // Slide out after 5 seconds
                    advToast.postDelayed(() -> {
                        if (getView() == null) return;
                        advToast.animate()
                                .translationX(startX)
                                .setDuration(600)
                                 .withEndAction(() -> advToast.setVisibility(View.GONE))
                                .start();
                    }, 5000);
                })
                .start();
    }

    private void updateMineButtonsInView(View view, int primaryColor, int themeIndex) {
        if (view instanceof com.kdt.mcgui.MineButton) {
            com.kdt.mcgui.MineButton btn = (com.kdt.mcgui.MineButton) view;
            if (btn.getBackground() != null) {
                if (themeIndex == 0) {
                    btn.getBackground().clearColorFilter();
                } else {
                    btn.getBackground().setColorFilter(new android.graphics.PorterDuffColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_ATOP));
                }
            }
        } else if (view != null && view.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
            // Programmatically update borders/strokes of any container panel, dialog card, or edit field to theme accents (Step 4)
            android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) view.getBackground();
            gd.setStroke((int)view.getResources().getDimension(R.dimen._1sdp), primaryColor);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                updateMineButtonsInView(vg.getChildAt(i), primaryColor, themeIndex);
            }
        }
    }

    private int getDarkerShade(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] += 0.45f; // Reduce value/brightness to make a perfect secondary dark gradient counterpart
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void applyThemeColors(View view) {
        if (view == null || getContext() == null) return;

        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        // Default to beautiful custom red/cyber-red ARGB color value (#FFFF003C)
        int primaryColor = prefs.getInt("launcher_theme_color_argb", 0xFFFF003C);
        int secondaryColor = getDarkerShade(primaryColor);
        int bgAnimType = prefs.getInt("launcher_bg_animation", 0);

        // 1. Tint BackgroundAnimationView and apply the correct animation mode (from the 15 Intense styles)
        com.kdt.mcgui.BackgroundAnimationView animBgView = view.findViewById(R.id.background_animation_view);
        if (animBgView != null) {
            animBgView.setAnimationType(bgAnimType);
            animBgView.setThemeColors(primaryColor, secondaryColor);
        }

        // 2. Play button keeps its XML glass/shadow background (no programmatic tint)

        // 3. Advancement toast keeps its XML background (no programmatic stroke)

        // 4. Red tint filters and forced red strokes removed: UI uses glass + shadow styling from XML
    }

    private void openThemeCustomizerDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_customize_theme)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            com.kdt.mcgui.ColorWheelView colorWheel = dialog.findViewById(R.id.theme_color_wheel);

            View tabSelectColour = dialog.findViewById(R.id.tab_select_colour);
            View tabSelectAnimation = dialog.findViewById(R.id.tab_select_animation);
            View panelColorWorkspace = dialog.findViewById(R.id.panel_color_workspace);
            View panelAnimationWorkspace = dialog.findViewById(R.id.panel_animation_workspace);

            View btnClose = dialog.findViewById(R.id.btn_close_customizer);

            android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());

            // Bind Color Wheel drag/touch listener to instantly update and skin launcher in real-time (Step 3)
            if (colorWheel != null) {
                colorWheel.setOnColorSelectedListener(color -> {
                    prefs.edit().putInt("launcher_theme_color_argb", color).apply();
                    prefs.edit().putInt("launcher_theme_color", 1).apply(); // non-zero trigger for MineButton tints
                    applyThemeColors(mRootView);
                });
            }

            // Tab Switching Navigation Logic (Step 3)
            if (tabSelectColour != null && tabSelectAnimation != null && panelColorWorkspace != null && panelAnimationWorkspace != null) {
                tabSelectColour.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    panelColorWorkspace.setVisibility(View.VISIBLE);
                    panelAnimationWorkspace.setVisibility(View.GONE);
                });

                tabSelectAnimation.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    panelColorWorkspace.setVisibility(View.GONE);
                    panelAnimationWorkspace.setVisibility(View.VISIBLE);
                });
            }

            // Bind the 15 Intense Background Animation Options (Step 4)
            int[] animIds = {
                R.id.anim_opt_0, R.id.anim_opt_1, R.id.anim_opt_2, R.id.anim_opt_3, R.id.anim_opt_4,
                R.id.anim_opt_5, R.id.anim_opt_6, R.id.anim_opt_7, R.id.anim_opt_8, R.id.anim_opt_9,
                R.id.anim_opt_10, R.id.anim_opt_11, R.id.anim_opt_12, R.id.anim_opt_13, R.id.anim_opt_14
            };

            for (int i = 0; i < animIds.length; i++) {
                final int animIdx = i;
                View animOpt = dialog.findViewById(animIds[i]);
                if (animOpt != null) {
                    animOpt.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        prefs.edit().putInt("launcher_bg_animation", animIdx).apply();
                        applyThemeColors(mRootView);
                        Toast.makeText(requireContext(), "INTENSE ANIMATION ACTIVATED", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    dialog.dismiss();
                });
            }
        });

        dialog.show();
    }

    private void openCreatorsInfoDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_creators_info)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            View btnTwicefear = dialog.findViewById(R.id.btn_yt_twicefear);
            View btnHellzior = dialog.findViewById(R.id.btn_yt_hellzior);
            View btnDiscordTwicefear = dialog.findViewById(R.id.btn_discord_twicefear);
            View btnDiscordHellzior = dialog.findViewById(R.id.btn_discord_hellzior);

            if (btnTwicefear != null) {
                btnTwicefear.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@twicefear3?si=kg3P4rhdTFennJf_"));
                            startActivity(intent);
                    } catch (Exception e) {
                             e.printStackTrace();
                    }
                });
            }

            if (btnHellzior != null) {
                btnHellzior.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@hellzior01?si=EFIdj3J2JATCyP2k"));
                             startActivity(intent);
                    } catch (Exception e) {
                             e.printStackTrace();
                    }
                });
            }

            if (btnDiscordTwicefear != null) {
                if (btnDiscordTwicefear != null) {
                    btnDiscordTwicefear.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.gg/NGMjxn9a7"));
                            startActivity(intent);
                    } catch (Exception e) {
                             e.printStackTrace();
                    }
                });
            }

            if (btnDiscordHellzior != null) {
                btnDiscordHellzior.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.gg/bsGtVV5sk"));
                               startActivity(intent);
                    } catch (Exception e) {
                                    e.printStackTrace();
                        }
                    }
                });
            }
        });

        dialog.show();
    }

    private void syncSkinToMinecraftResourcePack(Context context, String skinPath) {
        if (context == null || skinPath == null) return;
        try {
            // Persist the active skin path and model formatting to preferences
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString("active_skin_path", skinPath).apply();
            if ("steve".equalsIgnoreCase(skinPath)) {
                prefs.edit().putBoolean("active_skin_is_alex", false).apply();
            } else if ("alex".equalsIgnoreCase(skinPath)) {
                prefs.edit().putBoolean("active_skin_is_alex", true).apply();
            }

            // Synchronize skin to both standard and active instance directories
            java.util.List<File> targetDirs = new java.util.ArrayList<>();
            targetDirs.add(new File(Tools.DIR_GAME_HOME));
            try {
                Instance activeInstance = Instances.loadSelectedInstance();
                if (activeInstance != null) {
                    File instDir = activeInstance.getGameDirectory();
                    if (instDir != null && !instDir.equals(new File(Tools.DIR_GAME_HOME))) {
                        targetDirs.add(instDir);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (File baseDir : targetDirs) {
                File packDir = new File(baseDir, "resourcepacks/FEAR_Skin_Pack");
                File entityDir = new File(packDir, "assets/minecraft/textures/entity");
                entityDir.mkdirs();

                File stegePng = new File(entityDir, "steve.png");
                File alexPng = new File(entityDir, "alex.png");

                if (skinPath.equals("steve") || skinPath.equals("alex")) {
                    if (stevePng.exists()) stevePng.delete();
                    if (alexPng.exists()) alexPng.delete();
                } else {
                    File srcFile = new File(skinPath);
                    if (srcFile.exists()) {
                        copyFileStream(srcFile, stevePng);
                        copyFileStream(srcFile, alexPng);
                    }
                }

                // Write pack.mcmeta
                File mcmeta = new File(packDir, "pack.mcmeta");
                String mcmetaContent = "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"FEAR Skin Pack - Automatically Synced Skin\"\n  }\n}";
                writeStringToFile(mcmeta, mcmetaContent);

                // Automatically enable the skin pack in options.txt
                File optionsFile = new File(baseDir, "options.txt");
                if (optionsFile.exists()) {
                    String optionsContent = readStringFromFile(optionsFile);
                    if (optionsContent != null && !optionsContent.contains("FEAR_Skin_Pack")) {
                        if (optionsContent.contains("resourcePacks:[")) {
                            optionsContent = optionsContent.replace("resourcePacks:[", "resourcePacks:[\"file/FEAR_Skin_Pack\",");
                            writeStringToFile(optionsFile, optionsContent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFileStream(File src, File dst) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[1025];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void writeStringToFile(File file, String str) throws java.io.IOException {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private String readStringFromFile(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int readBytes = fis.read(data);
            return new String(data, 0, readBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
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
        // System telemetry removed entirely as requested.
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
        openCommandDashboard(null);
    }

    private void openCommandDashboard(String defaultTab) {
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
        Button navInfo = dialog.findViewById(R.id.dash_nav_info);

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
            if (navInfo != null) {
                navInfo.setBackgroundResource(R.drawable.premium_glass_black_bg);
                navInfo.setTextColor(0xFFFFFFFF);
            }
        };

        // SPLIT-PANE 1: SETTINGS ENGINE
        navSettings.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navSettings.setBackgroundResource(R.drawable.premium_button_bg);
            navSettings.setTextColor(0xFFFFFFFF);

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
            navExecute.setTextColor(0xFFFFFFFF);

            rightPane.removeAllViews();
            LinearLayout execLayout = new LinearLayout(requireContext());
            execLayout.setOrientation(LinearLayout.VERTICAL);
            execLayout.setPadding(16, 16, 16, 16);
            execLayout.setGravity(android.view.Gravity.CENTER);

            Button launchBtn = new Button(requireContext());
            launchBtn.setText("LAUNCH INSTALLER (.JAR)");
            launchBtn.setBackgroundResource(R.drawable.premium_button_bg);
            launchBtn.setTextColor(0xFFFFFFFF);
            launchBtn.setPadding(24, 12, 24, 12);
            launchBtn.setOnClickListener(vLaunch -> {
                dialog.dismiss();
                runInstallerWithConfirmation();
            });

            execLayout.addView(launchBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rightPane.addView(execLayout);
        });

        // SPLIT-PANE 3: SKIN CUSTOMIZER (Zalith & Premium Adapter)
        navSkin.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navSkin.setBackgroundResource(R.drawable.premium_button_bg);
            navSkin.setTextColor(0xFFFFFFFF);

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

                // Auto sync selected skin to Minecraft game textures folder (Step 2)
                syncSkinToMinecraftResourcePack(requireContext(), activeSkinPath);

                currentViewer.loadSkin(activeSkinPath, isAlex);

                // Feather /Lunar style automatic 360-degree continuous rotatable loop animation
                if (mSkinRotationAnimator != null) {
                    mSkinRotationAnimator.cancel();
                }
                mSkinRotationAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
                mSkinRotationAnimator.setDuration(12000); // Elegant 12 seconds full rotation
                mSkinRotationAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                mSkinRotationAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                mSkinRotationAnimator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    currentViewer.setRotationAngles(val, 0f);
                });
                mSkinRotationAnimator.start();

                java.lang.Runnable updateModelButtonsUI = () -> {
                    boolean currentIsAlex = prefs.getBoolean("active_skin_is_alex", false);
                    if (currentIsAlex) {
                        btnAlexModel.setBackgroundResource(R.drawable.premiumbutton_bg);
                          btnAlexModel.setTextColor(Color.WHITE);
                        btnSteveModel.setBackgroundResource(R.drawable.premiumglass_black_bg);
                        btnSteveModel.setTextColor(Color.WHITE);
                    } else {
                        btnSteveModel.setBackgroundResource(R.drawable.premiumbutton_bg);
                          btnSteveModel.setTextColor(Color.WHITE);
                        btnAlexModel.setBackgroundResource(R.drawable.premiumglass_black_bg);
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

                btnAlexModel.setOnClickListener(vA  -> {
                    vA#playSoundEffect(android.view.SoundEffectConstants.CLICK);
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
                    steveTitle.setTextColor(Color.WHITE);
                }
                steveCard.setOnClickListener(vSteve -> {
                    vsteve.playSoundEffect(android.view.SoundEffectConstants.CLICK);
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
                    alexTitle.setTextColor(Color.WHITE);
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
                                customTitle.setTextColor(Color.WHITE);
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

        // SPLIT-PANE 4: ACCOUNT HUB (microsoft & local offline login panels side-by-side with profile management)
        navAccount.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navAccount.setBackgroundResource(R.drawable.premium_button_bg);
            navAccount.setTextColor(0xFFFFFFFF);

            rightPane.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View accountHubView = inflater.inflate(R.layout.premium_account_hub_pane, rightPane, false);

            RecyclerView recyclerView = accountHubView.findViewById(R.id.account_list);
            View emptyState = accountHubView.findViewById(R.id.empty_account_state);
            EditText inputUsername = accountHubView.findViewById(R.id.local_username_input);
            TextView errorText = accountHubView.findViewById(R.id.local_error_text);
            View cardCMmojang = accountHubView.findViewById(R.id.card_type_mojang);
            View cardCLocal = accountHubView.findViewById(R.id.card_type_local);
            Button btnAddAccount = accountHubView.findViewById(R.id.btn_add_account);
            Button btnSwitchAccount = accountHubView.findViewById(R.id.btn_switch_account);
            View troubleLink = accountHubView.findViewById(R.id.trouble_logging_in);

            if (troubleLink != null) {
                troubleLink.setOnClickListener(v -> {
                    Toast.makeText(requireContext(), "Microsoft account migration is required for online play.", Toast.LENGTH_LONG).show();
                });
            }

            final java.util.List<net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount>[] accountListWrapper = new java.util.List[] { new java.util.ArrayList<>() };
            final net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount[] selectedAccWrapper = new net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount[] { null };
            final net.kdt.pojavlaunch.authenticator.AuthType[] selectedAuthType = new net.kdt.pojavlaunch.authenticator.AuthType[] { net.kdt.pojavlaunch.authenticator.AuthType.MICROSOFT };

            java.lang.Runnable loadAccountsList = () -> {
                accountListWrapper[0].clear();
                try {
                    accountListWrapper[0].addAll(net.kdt.pojavlaunch.authenticator.accounts.Accounts.load().accounts);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (emptyState != null) {
                    emptyState.setVisibility(accountListWrapper[0].isEmpty() ? View.ViSIBLE : View.GONE);
                }
            };

            loadAccountsList.run();
            net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount currentActive = net.kdt.pojavlaunch.authenticator.accounts.Accounts.getCurrent();
            selectedAccWrapper[0] = currentActive;

            class HubAdapter extends RecyclerView.Adapter<HubAdapter.VH> {
                @NonNull
                @Override
                public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    return new VH(inflater.inflate(R.layout.item_account, parent, false));
                }

                @˜fW'&ñFP¢V&∆ñ2fˆñBˆ‰&ñÊEfñWtÜˆ∆FW"ÑÊˆ‰ÁV∆¬dÇÇ¬ñÁB˜6óFñˆ‚í∞¢ÊWBÊ∂GBÁˆ¶f∆VÊ6ÇÊWFÜVÁFñ6F˜"Ê66˜VÁG2‰÷ñÊV7&gD66˜VÁB62“66˜VÁD∆ó7Ew&W%≥“ÊvWG˜6óFñˆ‚ì∞¢ÇÁW6W&Ê÷RÁ6WEFWáBÜ62ÁW6W&Ê÷Rì∞†¢7G&ñÊrGóT∆&V¬“$∆ˆ6¬#∞¢ñbÜ62ÊWFÖGóR“ÁV∆¬í∞¢7vóF6ÇÜ62ÊWFÖGóRí∞¢66R‘î5$ı4ÙeC¢GóT∆&V¬“$÷ñ7&˜6ˆgB#≤'&V≥∞¢66R5$eEîÂÙ‘3ßGóT∆&V¬“$7&gGñ‰‘2#≤'&V≥∞¢FVfV«C¢GóT∆&V¬“$∆ˆ6¬#≤'&V≥∞¢–¢–¢ÇÁGóRÁ6WEFWáBáGóT∆&V¬ì∞†¢&ˆˆ∆V‚ó4∆ó7E6V∆V7FVB“6V∆V7FVD65w&W%≥““ÁV∆¬bb6V∆V7FVD65w&W%≥“Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb62Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb6V∆V7FVD65w&W%≥“Ê’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíÊWV«2Ü62Ê’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíì∞†¢ñbÜó4∆ó7E6V∆V7FVBí∞¢ÇÊóFV’fñWrÁ6WD&6∂w&˜VÊE&W6˜W&6RÖ"ÊG&v&∆RÁ&V÷óV’ˆWFÖ˜GóUˆ6&Eˆ&rì∞¢“V«6R∞¢ÇÊóFV’fñWrÁ6WD&6∂w&˜VÊE&W6˜W&6RÖ"ÊG&v&∆RÁ&V÷óV’ˆv∆75ˆ&∆6µˆ&rì∞¢–†¢&ˆˆ∆V‚ó47W'&VÁD7FófR“7W'&VÁD7FófR“ÁV∆¬bb7W'&VÁD7FófRÊ’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb62Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb7W'&VÁD7FófRÊ’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíÊWV«2Ü7W'&VÁD7FófRÊ’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíì≤ÚÚfóÇ6ˆ◊&R«vó2G'VR¬vR6ˆ◊&R62FÚ7FófR†¢ó47W'&VÁD7FófR“7W'&VÁD7FófR“ÁV∆¬bb7W'&VÁD7FófRÊ’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb62Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb7W'&VÁD7FófRÊ’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíÊWV«2Ü62Ê’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíì∞†¢ñbÜó47W'&VÁD7FófRí∞¢ÇÁ7FGW5FWáBÁ6WEFWáBÇ$7FófR"ì∞¢ÇÁ7FGW5FWáBÁ6WEFWáD6ˆ∆˜"Ñ6ˆ∆˜"Á'6T6ˆ∆˜"Ç"4dcDCDB"íì∞¢ÇÁ7FGW4F˜BÁ6WD&6∂w&˜VÊD6ˆ∆˜"Ñ6ˆ∆˜"Á'6T6ˆ∆˜"Ç"4dcDCDB"íì∞¢“V«6R∞¢ÇÁ7FGW5FWáBÁ6WEFWáBáGóT∆&V¬ì∞¢ÇÁ7FGW5FWáBÁ6WEFWáD6ˆ∆˜"Ñ6ˆ∆˜"Á'6T6ˆ∆˜"Ç"3Édddddb"íì∞¢ÇÁ7FGW4F˜BÁ6WD&6∂w&˜VÊD6ˆ∆˜"Ñ6ˆ∆˜"Á'6T6ˆ∆˜"Ç"3Édddddb"íì∞¢–†¢ÇÊóFV’fñWrÁ6WDˆ‰6∆ñ6¥∆ó7FVÊW"áb”‚∞¢6V∆V7FVD65w&W%≥““63∞¢Ê˜FñgîFF6WD6ÜÊvVBÇì∞¢“ì∞†¢ÇÊFV∆WFT'F‚Á6WDˆ‰6∆ñ6¥∆ó7FVÊW"áb”‚∞¢˜W÷VÁR˜W“ÊWr˜W÷VÁRábÊvWD6ˆÁFWáBÇí¬ÇÊFV∆WFT'F‚ì∞¢˜WÊvWD÷VÁRÇíÊFBÇ$FV∆WFR"ì∞¢˜WÁ6WDˆ‰÷VÁTóFV‘6∆ñ6¥∆ó7FVÊW"ÜóFV“”‚∞¢ñbÇ$FV∆WFR"ÊWV«2ÜóFV“ÊvWEFóF∆RÇííí∞¢G'í∞¢ÊWBÊ∂GBÁˆ¶f∆VÊ6ÇÊWFÜVÁFñ6F˜"Ê66˜VÁG2‰66˜VÁG2ÊFV∆WFRÜ62ì∞¢∆ˆD66˜VÁG4∆ó7BÁ'V‚Çì∞¢Ê˜FñgîFF6WD6ÜÊvVBÇì∞¢ñbá6V∆V7FVD65w&W%≥““ÁV∆¬bb6V∆V7FVD65w&W%≥“Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb62Ê’6fT∆ˆ6Fñˆ‚“ÁV∆¿¢bb6V∆V7FVD65w&W%≥“Ê’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇíÊWV«2Ü62Ê’6fT∆ˆ6Fñˆ‚ÊvWDÊ÷RÇííí∞¢6V∆V7FVD65w&W%≥““ÁV∆√∞¢–¢&Vg&W6Ñ66˜VÁETíÇì∞¢Fˆ7BÊ÷∂UFWáBá&WVó&T6ˆÁFWáBÇí¬$66˜VÁB&V÷˜fVB"¬Fˆ7B‰ƒT‰uDÖı4Ñı%BíÁ6Ü˜rÇì∞¢“6F6ÇÑWÜ6WFñˆ‚Rí∞¢RÁ&ñÁE7F6µG&6RÇì∞¢–¢&WGW&‚G'VS∞¢“ì∞¢˜WÁ6Ü˜rÇì∞¢“ì∞¢–†¢verride public int getItemCount() { return accountListWrapper[0].size(); }

                class VH^[ô»ôXﬁX€\ïöY]ÀïöY]“€\à¬à^öY]»\Ÿ\õò[YK\K›]\’^¬àöY]»›]\—›[]Pùé¬àäõ€ìù[öY]»äH¬à›\\ääN¬à\Ÿ\õò[YHHãôö[ôöY]–ûRY
ãöYòXÿ€›[ù›\Ÿ\õò[YJN¬à\HHãôö[ôöY]–ûRY
ãöYòXÿ€›[ù›\JN¬à›]\’^Hãôö[ôöY]–ûRY
ãöYòXÿ€›[ù‹›]\◊›^
N¬à›]\—›Hãôö[ôöY]–ûRY
ãöYòXÿ€›[ù‹›]\◊Ÿ›
N¬à[]PùàHãôö[ôöY]–ûRY
ãöYòXÿ€›[ùŸ[]WÿùäN¬àBàBàBÇàXêY\\àY\\àHô]»XêY\\ä
N¬àYà
ôXﬁX€\ïöY]»OHù[
H¬àôXﬁX€\ïöY]ÀúŸ]^[›]X[òYŸ\äô]»[ôX\ì^[›]X[òYŸ\äô\]Z\ôP€€ù^

JJN¬àôXﬁX€\ïöY]ÀúŸ]Y\\äY\\äN¬àBÇàò]òKõ[ôÀîù[õòXõH\]P]]RHH

HOà¬àYà
ÿ\ô[⁄ò[ô»OHù[
Hÿ\ô[⁄ò[ôÀúŸ]Ÿ[X›Y
Ÿ[X›Y]]\VÃHOHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\KìRP‘ì‘”—ï
N¬àYà
ÿ\ôÿÿ[OHù[
Hÿ\ôÿÿ[úŸ]Ÿ[X›Y
Ÿ[X›Y]]\VÃHOHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\Kê‘êQïSó”P N¬ÇàYà
[ú]\Ÿ\õò[YHOHù[
H¬àYà
Ÿ[X›Y]]\VÃHOHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\Kì––S
H¬à[ú]\Ÿ\õò[YKúŸ][òXõY
ùYJN¬à[ú]\Ÿ\õò[YKúŸ][JKåäN¬àH[ŸH¬à[ú]\Ÿ\õò[YKúŸ][òXõY
ò[ŸJN¬à[ú]\Ÿ\õò[YKúŸ][JçäN¬à[ú]\Ÿ\õò[YKúŸ]^
àäN¬àBàBàYà
\úõ‹ï^OHù[
H\úõ‹ï^úŸ]ö\⁄Xö[]JöY]Àë””ëJN¬àN¬Çà\]P]]RKúù[ä
N¬ÇàYà
ÿ\ô[⁄ò[ô»OHù[
Hÿ\ô[⁄ò[ôÀúŸ]€ê€X⁄”\›[ô\äàOà»Ÿ[X›Y]]\VÃHHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\KìRP‘ì‘”—ï»\]P]]RKúù[ä
N»JN¬àYà
ÿ\ôÿÿ[OHù[
Hÿ\ôÿÿ[úŸ]€ê€X⁄”\›[ô\äàOà»Ÿ[X›Y]]\VÃHHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\Kê‘êQïSó”PŒ»\]P]]RKúù[ä
N»JN¬ÇàYà
ùî›⁄]⁄Xÿ€›[ùOHù[
H¬àùî›⁄]⁄Xÿ€›[ùúŸ]€ê€X⁄”\›[ô\äàOà¬àYà
Ÿ[X›YXÿ’‹ò\\ñÃHOHù[
H¬àô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãòXÿ€›[ùÀêXÿ€›[ùÀúŸ]›\úô[ù
Ÿ[X›YXÿ’‹ò\\ñÃJN¬àôYúô\⁄Xÿ€›[ùRJ
N¬àÿ\›õXZŸU^
ô\]Z\ôP€€ù^

Kî›⁄]⁄Y»à
»Ÿ[X›YXÿ’‹ò\\ñÃKù\Ÿ\õò[YKÿ\›ìSë’‘“‘ï
Kú⁄› 
N¬àX[ŸÀô\€Z\‹ 
N¬àH[ŸH¬àÿ\›õXZŸU^
ô\]Z\ôP€€ù^

KîX\ŸHŸ[X›[àXÿ€›[ùö\ú›ãÿ\›ìSë’‘“‘ï
Kú⁄› 
N¬àBàJN¬àBÇàYà
ùêYXÿ€›[ùOHù[
H¬àùêYXÿ€›[ùúŸ]€ê€X⁄”\›[ô\äàOà¬àYà
Ÿ[X›Y]]\VÃHOHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\KìRP‘ì‘”—ï
H¬àX[ŸÀô\€Z\‹ 
N¬à€€Àú›ÿ\úòY€Y[ù
ô\]Z\ôPX›]ö]J
KZX‹õ‹€ŸùŸ⁄[ëúòY€Y[ùò€\‹ÀZX‹õ‹€ŸùŸ⁄[ëúòY€Y[ùïQÀù[
N¬àô]\õé¬àBàYà
Ÿ[X›Y]]\VÃHOHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\Kê‘êQïSó”P H¬àX[ŸÀô\€Z\‹ 
N¬à€€Àú›ÿ\úòY€Y[ù
ô\]Z\ôPX›]ö]J
K‹òYù[ìŸ⁄[ëúòY€Y[ùò€\‹À‹òYù[ìŸ⁄[ëúòY€Y[ùïQÀù[
N¬àô]\õé¬àBàYà
[ú]\Ÿ\õò[YHOHù[
Hô]\õé¬à›ö[ô»\Ÿ\õò[YHH[ú]\Ÿ\õò[YKôŸ]^

Kù‘›ö[ô 
Kùö[J
N¬ÇàYà
[ôõ⁄Yù^ï^][Àö\—[\J\Ÿ\õò[YJJH¬àYà
\úõ‹ï^OHù[
H»\úõ‹ï^úŸ]^
ï\Ÿ\õò[YHÿ[õõ›ôH[\HäN»\úõ‹ï^úŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN»Bàô]\õé¬àBàYà
\Ÿ\õò[YKõ[ô›

H H¬àYà
\úõ‹ï^OHù[
H»\úõ‹ï^úŸ]^
ï\Ÿ\õò[YH]\›ôH]X\›»⁄\òX›\ú»äN»\úõ‹ï^úŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN»Bàô]\õé¬àBàYà
\Ÿ\õò[YKõ[ô›

HàMäH¬àYà
\úõ‹ï^OHù[
H»\úõ‹ï^úŸ]^
ï\Ÿ\õò[YH]\›ôHMà⁄\òX›\ú»‹à\‹»äN»\úõ‹ï^úŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN»Bàô]\õé¬àBàYà
]\Ÿ\õò[YKõX]⁄\ ñÿK^êKVåNW◊J»äJH¬àYà
\úõ‹ï^OHù[
H»\úõ‹ï^úŸ]^
ì€õH]\úÀù[Xô\ú»[ô»[›ŸYäN»\úõ‹ï^úŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN»Bàô]\õé¬àBÇàYà
\úõ‹ï^OHù[
H\úõ‹ï^úŸ]ö\⁄Xö[]JöY]Àë””ëJN¬ÇàûH¬àô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãòXÿ€›[ù”Z[ôX‹òYùXÿ€›[ùXÿ€›[ùHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãòXÿ€›[ùÀêXÿ€›[ùÀò‹ôX]JXÿ»Oà¬àXÿÀù\Ÿ\õò[YHH\Ÿ\õò[YN¬àXÿÀò]]\HHô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãê]]\Kì––S¬àXÿÀòXÿŸ\‹’⁄Ÿ[àHåé¬àXÿÀúõŸö[RYHåLLLLé¬àXÿÀúôYúô\⁄⁄Ÿ[àHåé¬àJN¬àô]öŸú⁄ò]õ][ò⁄ò]][ùXÿ]‹ãòXÿ€›[ùÀêXÿ€›[ùÀúŸ]›\úô[ù
Xÿ€›[ù
N¬àôYúô\⁄Xÿ€›[ùRJ
N¬àÿ\›õXZŸU^
ô\]Z\ôP€€ù^

KêXÿ€›[ù	»à
»\Ÿ\õò[YH
»â»‹ôX]YHãÿ\›ìSë’‘“‘ï
Kú⁄› 
N¬àX[ŸÀô\€Z\‹ 
N¬àHÿ]⁄
^Ÿ\[€àJH¬àYà
\úõ‹ï^OHù[
H»\úõ‹ï^úŸ]^
ëòZ[Yàà
»KôŸ]Y\‹ÿYŸJ
JN»\úõ‹ï^úŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN»BàBàJN¬àBÇàöY⁄[ôKòYöY] Xÿ€›[ùXïöY] N¬àJN¬ÇàÀ»‘UTSëHNàSîUPTSë»⁄]⁄⁄[úÀ»⁄Y\ú¬àò]ì[ŸX⁄‹ÀúŸ]€ê€X⁄”\›[ô\äåàOà¬àåãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬àô\Ÿ]ò]êù]€úÀúù[ä
N¬àò]ì[ŸX⁄‹ÀúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àò]ì[ŸX⁄‹ÀúŸ]^€€‹äëëëëëëëäN¬ÇàöY⁄[ôKúô[[›ôP[öY]‹ 
N¬àù]€àX\ùàHô]»ù]€äô\]Z\ôP€€ù^

JN¬àX\ùãúŸ]^
ì‘Sà’T’”H””ïì”»PTSë»⁄]⁄⁄[úÀ»⁄Y\ú»äN¬àX\ùãúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àX\ùãúŸ]^€€‹äëëëëëëëäN¬àX\ùãúŸ]€ê€X⁄”\›[ô\äìX\Oà¬àX[ŸÀô\€Z\‹ 
N¬à›\ùX›]ö]Jô]»[ù[ù
ô\]Z\ôP€€ù^

K›\›€P€€ùõ€–X›]ö]Kò€\‹ JN¬àJN¬àöY⁄[ôKòYöY] X\ùäN¬àJN¬ÇàÀ»‘UTSëHéàS—P“»Së“SëH
S—4“‘ BàYà
ò]ì[ŸX⁄‹»OHù[
H¬àò]ì[ŸX⁄‹ÀúŸ]€ê€X⁄”\›[ô\äåàOà¬àåãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬àô\Ÿ]ò]êù]€úÀúù[ä
N¬àò]ì[ŸX⁄‹ÀúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àò]ì[ŸX⁄‹ÀúŸ]^€€‹äëëëëëëëäN¬ÇàöY⁄[ôKúô[[›ôP[öY]‹ 
N¬àX[ŸÀô\€Z\‹ 
N¬àù[ôHù[ôHHô]»ù[ôJ
N¬àù[ôKú]›ö[ô õ[ŸHãõ[ŸX⁄»äN¬à€€Àú›ÿ\úòY€Y[ù
ô\]Z\ôPX›]ö]J
KŸX\ò⁄[ŸúòY€Y[ùò€\‹ÀŸX\ò⁄[ŸúòY€Y[ùïQÀù[ôJN¬àJN¬àBÇàÀ»‘UTSëHŒàQ”àSî’STà
[ŸÀ⁄Y\úÀô\€›\òŸHX⁄‹ BàYà
ò]êY€ú»OHù[
H¬àò]êY€úÀúŸ]€ê€X⁄”\›[ô\äåàOà¬àåãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬àô\Ÿ]ò]êù]€úÀúù[ä
N¬àò]êY€úÀúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àò]êY€úÀúŸ]^€€‹äëëëëëëëäN¬ÇàöY⁄[ôKúô[[›ôP[öY]‹ 
N¬àX[ŸÀô\€Z\‹ 
N¬àù[ôHù[ôHHô]»ù[ôJ
N¬àù[ôKú]›ö[ô õ[ŸHãòY€àäN¬à€€Àú›ÿ\úòY€Y[ù
ô\]Z\ôPX›]ö]J
KŸX\ò⁄[ŸúòY€Y[ùò€\‹ÀŸX\ò⁄[ŸúòY€Y[ùïQÀù[ôJN¬àJN¬àBÇÇàÀ»‘UTSëHàSSQUñH—‘¬àò]ìŸ‹ÀúŸ]€ê€X⁄”\›[ô\äåàOà¬àåãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬àô\Ÿ]ò]êù]€úÀúù[ä
N¬àò]ìŸ‹ÀúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àò]ìŸ‹ÀúŸ]^€€‹äëëëëëëëäN¬ÇàöY⁄[ôKúô[[›ôP[öY]‹ 
N¬àù]€à⁄\ôPùàHô]»ù]€äô\]Z\ôP€€ù^

JN¬à⁄\ôPùãúŸ]^
ëV‘ï÷T’ST»—‘»SSQUñHäN¬à⁄\ôPùãúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬à⁄\ôPùãúŸ]^€€‹äëëëëëëëäN¬à⁄\ôPùãúŸ]€ê€X⁄”\›[ô\äî⁄\ôHOà¬àX[ŸÀô\€Z\‹ 
N¬à⁄\ôSŸ ô\]Z\ôP€€ù^

JN¬àJN¬àöY⁄[ôKòYöY] ⁄\ôPùäN¬àJN¬ÇàÀ»‘UTSëHNà‘ëPU‘î»	àSëì¬àYà
ò]í[ôõ»OHù[
H¬àò]í[ôõÀúŸ]€ê€X⁄”\›[ô\äåàOà¬àåãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬àô\Ÿ]ò]êù]€úÀúù[ä
N¬àò]í[ôõÀúŸ]òX⁄Ÿ‹õ›[ôô\€›\òŸJãôò]ÿXõKúô[Z][Wÿù]€óÿô N¬àò]í[ôõÀúŸ]^€€‹äëëëëëëëäN¬ÇàöY⁄[ôKúô[[›ôP[öY]‹ 
N¬àöY]»[ôõ’öY]»HX[ŸÀôŸ]^[›][ôõ]\ä
Kö[ôõ]Jãõ^[›]ôX[Ÿ◊ÿ‹ôX]‹ú◊⁄[ôõÀöY⁄[ôKò[ŸJN¬ÇàÀ»YHòX⁄Ÿ‹õ›[ôŸà[ôõ’öY]»[ú⁄YHúò[YS^[›]»õ[ôŸX[[\‹€Bà[ôõ’öY]ÀúŸ]òX⁄Ÿ‹õ›[ô
ù[
N¬ÇàöY]»ùï⁄XŸYôX\àH[ôõ’öY]Àôö[ôöY]–ûRY
ãöYòùóﬁ]›⁄XŸYôX\äN¬àöY]»ùí[ö[‹àH[ôõ’öY]Àôö[ôöY]–ûRY
ãöYòùóﬁ]⁄[ö[‹äN¬ÇàYà
ùï⁄XŸYôX\àOHù[
H¬àùï⁄XŸYôX\ãúŸ]€ê€X⁄”\›[ô\äàOà¬àãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬à[ù[ù[ù[ùHô]»[ù[ù
[ù[ùêP’S”ó’íQUÀ[ôõ⁄Yõô]ï\öKú\úŸJöŒãÀﬁ[›]XôKò€€K–⁄XŸYôX\åœ‹⁄OQ€]QöòﬁùUçå›»äJN¬à›\ùX›]ö]J[ù[ù
N¬àJN¬àBÇàYà
ùí[ö[‹àOHù[
H¬àùí[ö[‹ãúŸ]€ê€X⁄”\›[ô\äàOà¬àãú^T€›[ôYôôX›
[ôõ⁄YùöY]Àî€›[ôYôôX›€€ú›[ùÀê”P“ N¬àô]öŸú⁄ò]õ][ò⁄î€›[ôX[òYŸ\ãú^P€X⁄ 
N¬à[ù[ù[ù[ùHô]»[ù[ù
[ù[ùêP’S”ó’íQUÀ[ôõ⁄Yõô]ï\öKú\úŸJöŒãÀﬁ[›]XôKò€€K–[ö[‹åO‹⁄O]÷XŒìR’\HäJN¬à›\ùX›]ö]J[ù[ù
N¬àJN¬àBÇàöY⁄[ôKòYöY] [ôõ’öY] N¬àJN¬àBÇàÀ»Yò][Ÿ[X›[€ÇàYà
ú⁄⁄[àãô\]X[ Yò][XäJH¬àò]î⁄⁄[ãú\ôõ‹õP€X⁄ 
N¬àH[ŸHYà
òXÿ€›[ùãô\]X[ Yò][XäJH¬àò]êXÿ€›[ùú\ôõ‹õP€X⁄ 
N¬àH[ŸH¬àò]îŸ][ô‹Àú\ôõ‹õP€X⁄ 
N¬àBÇàX[ŸÀú⁄› 
N¬àBÇàö]ò]Hõ⁄Y[ö[X]R][\‘Ÿ\]Y[ùX[JöY]—‹õ›\€€ùZ[ô\ãõ€€X[à⁄› H¬à[ù€›[ùH€€ùZ[ô\ãôŸ]⁄[€›[ù

N¬àõ‹à
[ùHH»H€›[ù»J  H¬àö[ò[öY]»⁄[H€€ùZ[ô\ãôŸ]⁄[]
JN¬àYà
⁄[[ú›[òŸ[Ÿà€€KöŸõXŸ›ZKì][ò⁄\ìY[ùPù]€à⁄[[ú›[òŸ[Ÿà^öY]»⁄[[ú›[òŸ[Ÿà[ôX\ì^[›]
H¬à[ö[X][€à[ö[HH[ö[X][€ï][ÀõÿY[ö[X][€äô\]Z\ôP€€ù^

K⁄›»»ãò[ö[Kö][WŸòYW⁄[ààãò[ö[Kö][WŸòYW€›]
N¬à[ö[KúŸ]›\ùŸôúŸ]
H
àL
N¬à⁄[ú›\ù[ö[X][€ä[ö[JN¬àBàBàBÇàö]ò]Hõ⁄Y‹[êXÿ€›[ùX[òYŸ\ä
H¬àXÿ€›[ùX[òYŸ\ëúòY€Y[ù⁄Y]Hô]»Xÿ€›[ùX[òYŸ\ëúòY€Y[ù

N¬à⁄Y]úŸ]€êXÿ€›[ùŸ[X›Y\›[ô\äXÿ€›[ùOà¬àXÿ€›[ùÀúŸ]›\úô[ù
Xÿ€›[ù
N¬àôYúô\⁄Xÿ€›[ùRJ
N¬àJN¬à⁄Y]ú⁄› Ÿ]⁄[úòY€Y[ùX[òYŸ\ä
KXÿ€›[ùX[òYŸ\ëúòY€Y[ùïQ N¬àBÇàXõX»õ⁄YôYúô\⁄Xÿ€›[ùRJ
H¬àZ[ôX‹òYùXÿ€›[ù›\úô[ùHXÿ€›[ùÀôŸ]›\úô[ù

N¬à›ö[ô»\Ÿ\õò[YHHêYXÿ€›[ùé¬à›ö[ô»\SXô[Hï\»X[òYŸHé¬ÇàYà
›\úô[ùOHù[	âà›\úô[ùù\Ÿ\õò[YHOHù[à	âàX›\úô[ùù\Ÿ\õò[YKö\—[\J
H	âàX›\úô[ùù\Ÿ\õò[YKô\]X[ åäJH¬à\Ÿ\õò[YHH›\úô[ùù\Ÿ\õò[YN¬àYà
›\úô[ùò]]\HOHù[
H¬à›⁄]⁄
›\úô[ùò]]\JH¬àÿ\ŸHRP‘ì‘”—ïà\SXô[HìZX‹õ‹€ŸùXÿ€›[ùé»úôXZŒ¬àÿ\ŸH‘êQïSó”PŒù\SXô[Hê‹òYù[ìP»Xÿ€›[ùé»úôXZŒ¬àYò][à\SXô[Hìÿÿ[Xÿ€›[ùé»úôXZŒ¬àBàBàBÇàYà
PXÿ€›[ùò[YHOHù[
HPXÿ€›[ùò[YKúŸ]^
\Ÿ\õò[YJN¬àYà
PXÿ€›[ù\SXô[OHù[
HPXÿ€›[ù\SXô[úŸ]^
\SXô[
N¬ÇàYà
PXÿ€›[ùò[YQ\‹^HOHù[
HPXÿ€›[ùò[YQ\‹^KúŸ]^
\Ÿ\õò[YJN¬ÇàYà
Tõ€›öY]»OHù[
H¬àôYúô\⁄⁄⁄[íXY\‹^JTõ€›öY] N¬àBàBÇàö]ò]Hõ⁄YôYúô\⁄⁄⁄[íXY\‹^JöY]»öY] H¬à€€KöŸõXŸ›ZKìZ[ôX‹òYù⁄⁄[ïöY]»⁄⁄[ïöY]»HöY]Àôö[ôöY]–ûRY
ãöYö€Y\YŸW‹⁄⁄[ó⁄XY
N¬àYà
⁄⁄[ïöY]»OHù[
Hô]\õé¬Çà[ôõ⁄Yò€€ù[ùî⁄\ôYôYô\ô[òŸ\»ôYú»H[ôõ⁄YúôYô\ô[òŸKîôYô\ô[òŸSX[òYŸ\ãôŸ]Yò][⁄\ôYôYô\ô[òŸ\ ô\]Z\ôP€€ù^

JN¬à›ö[ô»X›]ôT⁄⁄[î]HôYúÀôŸ]›ö[ô òX›]ôW‹⁄⁄[ó‹]ãú›]ôHäN¬àõ€€X[àX›]ôT⁄⁄[í\–[^HôYúÀôŸ]õ€€X[äòX›]ôW‹⁄⁄[ó⁄\◊ÿ[^ãò[ŸJN¬Çà⁄⁄[ïöY]ÀúŸ]⁄›“XY€õJùYJN¬à⁄⁄[ïöY]ÀõÿY⁄⁄[äX›]ôT⁄⁄[î]X›]ôT⁄⁄[í\–[^
N¬ÇàÀ»]]À\õ›][€à[ö[X][€à€‹õ‹à—⁄⁄[àXY
ÕåY‹ôY\»€€ù[ù[›\À€⁄⁄[ô»›òZY⁄
BàYà
RXYõ›][€ê[ö[X]‹àOHù[
H¬àRXYõ›][€ê[ö[X]‹ãòÿ[òŸ[

N¬àBàRXYõ›][€ê[ö[X]‹àH[ôõ⁄Yò[ö[X][€ãïò[YP[ö[X]‹ãõŸëõÿ]
ãÕåäN¬àRXYõ›][€ê[ö[X]‹ãúŸ]\ò][€äå
N»À»àŸX€€ô»õ‹àHù[€[€›Õåõ›][€ÇàRXYõ›][€ê[ö[X]‹ãúŸ]ô\X]€›[ù
[ôõ⁄Yò[ö[X][€ãïò[YP[ö[X]‹ãíSëíSíUJN¬àRXYõ›][€ê[ö[X]‹ãúŸ][ù\ú€]‹äô]»[ôõ⁄YùöY]Àò[ö[X][€ãì[ôX\í[ù\ú€]‹ä
JN¬àRXYõ›][€ê[ö[X]‹ãòY\]S\›[ô\ä[ö[X][€àOà¬àõÿ]ò[H
õÿ]
H[ö[X][€ãôŸ][ö[X]Yò[YJ
N¬à⁄⁄[ïöY]ÀúŸ]õ›][€ê[ô€\ ò[äN¬àJN¬àRXYõ›][€ê[ö[X]‹ãú›\ù

N¬ÇàÀ»Ÿ]\⁄]ùXòõH\Y‹ôY][ô‹»€‹à^öY]»⁄]ùXòõHHöY]Àôö[ôöY]–ûRY
ãöYö€Y\YŸWÿ⁄]ÿùXòõJN¬àYà
⁄]ùXòõHOHù[
H¬àYà
P⁄]ùXòõR[ô\àOHù[
H¬àP⁄]ùXòõR[ô\àHô]»[ôõ⁄Yõ‹Àí[ô\ä[ôõ⁄Yõ‹Àì€‹\ãôŸ]XZ[ì€‹\ä
JN¬àH[ŸH¬àYà
P⁄]ùXòõTù[õòXõHOHù[
H¬àP⁄]ùXòõR[ô\ãúô[[›ôPÿ[òX⁄‹ P⁄]ùXòõTù[õòXõJN¬àBàBÇàP⁄]ùXòõTù[õòXõHHô]»ò]òKõ[ôÀîù[õòXõJ
H¬àö]ò]Hö[ò[ò]òKù][îò[ô€Hò[ô€HHô]»ò]òKù][îò[ô€J
N¬à›ô\úöYBàXõX»õ⁄Yù[ä
H¬à›ö[ô»\Ÿ»H“U”QT‘–Q—T÷‹ò[ô€Kõô^[ù
“U”QT‘–Q—TÀõ[ô›
WN¬à⁄]ùXòõKúŸ]^
\Ÿ N¬à⁄]ùXòõKúŸ]ö\⁄Xö[]JöY]ÀïíT“PìJN¬à⁄]ùXòõKúŸ][JäN¬à⁄]ùXòõKúŸ]ÿÿ[V
äN¬à⁄]ùXòõKúŸ]ÿÿ[VJäN¬ÇàÀ»[ö[X]Hÿÿ[H\	àòYH[¢
                    chatBubble.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                           .setDuration(400)
                           .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                            .withEndAction(() -> {
                                // Keep visible for 4 seconds, then fade out
                                chatBubble.postDelayed(() -> {
                                   chatBubble.animate()
                                             .alpha(0f)
                                             .scaleX(0.5f)
                                              .scaleY(0.5f)
                                            .setDuration(300)
                                             .withEndAction(() -> chatBubble.setVisibility(View.GONE))
                                             .start();
                               }, 4000);
                           })
                            .start();

                    // Re-run every 15 seconds
                    mChatBubbleHandler.postDelayed(this, 15000);
                }
            };

            // Start loop with a slight initial delay of 1 second
            mChatBubbleHandler.postDelayed(mChatBubbleRunnable, 1000);
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
            E        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        refreshAccountUI();
        updateVersionText();
    }

    Override
    public void onDestroyView() {
        if (mHeadRotationAnimator != null) {
            mHeadRotationAnimator.cancel();
        }
        if (mSkinRotationAnimator != null) {
            mSkinRotationAnimator.cancel();
        }
        if (mChatBubbleHandler != null && mChatBubbleRunnable != null) {
            mChatBubbleHandler.removeCallbacks(mChatBubbleRunnable);
        }
        if (mAnnouncementHandler != null && mAnnouncementRunnable != null) {
            mAnnouncementHandler.removeCallbacks(mAnnouncementRunnable);
        }
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mPlayStateListener);
        com.kdt.mcgui.ProgressLayout plCleanup = getView() != null ? getView().findViewById(R.id.progress_layout) : null;
        if (plCleanup != null) {
            plCleanup.clearUpObservers();
            ProgressKeeper.removeTaskCountListener(plCleanup);
        }
    }
}
