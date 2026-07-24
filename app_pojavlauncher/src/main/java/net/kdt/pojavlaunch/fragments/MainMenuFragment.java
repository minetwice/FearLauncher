package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
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
import androidx.recyclerview.widget.LinearLayoutManager;

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
        "💬 What's the plan today?", "⚔️ Ready for another adventure?", "💎 Let's mine some diamonds!", "🚀 FEAR Launcher ready to boot!",
        "✨ Play premium. Play smooth.", "🍕 Time to craft some food?", "🛠️ Customize your custom controls!", "🔋 Performance is fully optimized!",
        "🎮 Keep building, keep playing!", "🪵 Punching trees is the key to start!", "🧟 Beware of the zombies tonight!", "🏹 Skeletons have perfect aim!",
        "🕸️ Spiders climb walls quite easily!", "🧨 Shhh... SSSS... boom!", "🧱 Build a sturdy obsidian vault!", "🪓 Chopping wood with style!",
        "🧭 Follow the compass to home base!", "🔔 Raid protection activated!", "🧪 Brewing some strength potions!", "📦 Organize your storage chest!",
        "🌾 Farming wheat for delicious bread!", "🥚 Egg-cellent poultry farms!", "🐷 Ride a pig with a carrot on a stick!", "🐴 Horse taming in progress!",
        "🐺 Loyal wolves will protect you!", "🐱 Cats ward off pesky creepers!", "🦜 Parrots imitate nearby mobs!", "🦙 Llama caravans carry your loot!",
        "🐼 Bamboo eating pandas are cute!", "🦊 Foxes sleep under sweet berry bushes!", "🐸 Frogs jump in mangrove swamps!", "🦦 Otters swimming in rivers!",
        "🐝 Sweet honey blocks look delicious!", "🐑 Shear sheep for colorful wool!", "🐮 Milking cows with iron buckets!", "🐔 Chicken farms produce feathers!",
        "🗺️ Map out your entire kingdom!", "🛖 Cozy wood cabins are best!", "🏰 Build a majestic stone castle!", "🌋 Watch out for flowing lava!",
        "🌊 Diving deep into the ocean!", "🐬 Dolphins lead to buried treasure!", "🐋 Whale watching in cold biomes!", "🐢 Turtle shells make great helmets!",
        "🐟 Fishing under a starry night!", "🐡 Pufferfish are highly poisonous!", "🪵 Spruce wood matches dark builds!", "🪵 Birch wood for clean interiors!",
        "🪵 Jungle trees grow extremely high!", "🪵 Acacia wood makes cool orange builds!", "🪵 Dark oak doors look premium!", "🪵 Mangrove wood has rich red tones!",
        "🪵 Cherry wood adds pastel beauty!", "🌾 Harvest beetroot for pink dye!", "🥕 Golden carrots restore major hunger!", "🍎 Golden apples grant absorption!",
        "🍉 Glistering melon for brewing!", "🎃 Jack o'lanterns light up paths!", "🕯️ Scented candles add cozy vibes!", "💡 Glowstone lamps under water!",
        "🧊 Ice skating on frozen lakes!", "🏔️ Climbing snowy mountain peaks!", "🪵 Crimson stems grow in the Nether!", "🪵 Warped stems look extraterrestrial!",
        "🔥 Blue soul fire burns brightly!", "🐷 Piglins love gold ingots!", "🐗 Watch out for angry hoglins!", "👻 Ghasts cry in the nether sky!",
        "💀 Wither skeletons drop rare skulls!", "😈 Blazes spin in nether fortresses!", "🧗 Climbing up winding cave vines!", "💎 Finding raw iron in veins!",
        "💎 Raw copper turns into green blocks!", "💎 Raw gold deep in badlands!", "⛏️ Diamond pickaxe with Fortune III!", "⛏️ Netherite pickaxe is indestructible!",
        "🛡️ Shield up to deflect arrows!", "🗡️ Sharpness V on your netherite sword!", "🏹 Infinity bow never runs out of ammo!", "🔱 Riptide trident in rainstorms!",
        "🧼 Clean your armor with cauldrons!", "🚪 Secret doors behind paintings!", "🪜 Ladders climb to the sky limit!", "🐾 Sniffers sniffing out ancient seeds!",
        "🐫 Camel riding through vast deserts!", "🌵 Watch out for prickly cacti!", "🏜️ Pyramids hold secret TNT traps!", "🏝️ Warm ocean coral reefs look gorgeous!",
        "🧜 Conduit power activates underwater breathing!", "🏠 Set your spawn point with beds!", "🛏️ Respawn anchors set nether spawns!", "🔮 Enchanting books on tables!",
        "📚 Bookshelves boost enchantment levels!", "🧪 Brewing splash potion of healing!", "🧿 Eye of ender guides to portals!", "🏰 Strongholds hide deep underground!",
        "👾 Silverfish hide inside stone blocks!", "🚪 Unlock the End Portal frame!", "🐉 Defeat the mighty Ender Dragon!", "🥚 Collect the rare Dragon Egg!",
        "🦅 Elytra wings let you fly freely!", "🚀 Fireworks boost Elytra flight!", "🐚 Shulker boxes act as portable chests!", "🏙️ End cities hide majestic loot!",
        "🌲 Tall taiga forests are majestic!", "🪵 Dark oak forests grow thick!", "🍄 Giant mushrooms look mystical!", "🧙 Witches brew inside swamp huts!",
        "🦑 Glow squids light up dark water!", "💎 Amethyst geodes ring beautifully!", "🧪 Copper bulbs oxidize over time!", "⚔️ Trial chambers hold trials!",
        "🦁 Armadillos drop scutes for wolf armor!", "🪵 Breeze rods craft wind charges!", "🌬️ Wind charges launch you high!", "🗝️ Trial keys unlock vaults!",
        "💎 Heavy cores craft the Mace!", "🔨 Mace smash attacks deal huge damage!", "💎 Netherite upgrade templates are rare!", "🎨 Trim your armor with styles!"
    };

    private android.animation.ValueAnimator mHeadRotationAnimator;
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
                if (requireActivity() instanceof net.kdt.pojavlaunch.LauncherActivity) {
                    com.kdt.mcgui.ProgressLayout pl = ((net.kdt.pojavlaunch.LauncherActivity) requireActivity()).getProgressLayout();
                    if (pl != null) {
                        pl.setVisibility(View.VISIBLE);
                        pl.onClick(pl);
                    }
                } else {
                    Toast.makeText(requireContext(), "NO NEW NOTIFICATIONS ACTIVE.", Toast.LENGTH_SHORT).show();
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

        // Open our Theme Customizer panel from Tray DESIGN & CUSTOMIZE button
        View trayCustomize = view.findViewById(R.id.tray_customize_btn);
        if (trayCustomize != null) {
            trayCustomize.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                collapseTray(settingsTray);
                openThemeCustomizerDialog();
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
        advToast.setVisibility(View.VISIBLE);

        // Position it completely off-screen to start (slide in from right)
        float startX = advToast.getWidth() > 0 ? advToast.getWidth() + 200f : 1000f;
        advToast.setTranslationX(startX);

        // Slide in animation with OvershootInterpolator for premium bouncy touch
        advToast.animate()
                .translationX(0f)
                .setDuration(800)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .withEndAction(() -> {
                    playChallengeSound();

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

    private void applyThemeColors(View view) {
        if (view == null || getContext() == null) return;

        // Retrieve chosen theme color index from SharedPreferences
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        int themeIndex = prefs.getInt("launcher_theme_color", 0);
        int bgAnimType = prefs.getInt("launcher_bg_animation", 0);

        int primaryColor;
        int secondaryColor;

        switch (themeIndex) {
            case 1: // Molten Lava Red
                primaryColor = 0xFFFF3D00;
                secondaryColor = 0xFF990000;
                break;
            case 2: // Toxic Lime Green
                primaryColor = 0xFF00FF66;
                secondaryColor = 0xFF006600;
                break;
            case 3: // Mystical Purple
                primaryColor = 0xFFD000FF;
                secondaryColor = 0xFF520066;
                break;
            case 4: // Dragon Gold
                primaryColor = 0xFFFFC400;
                secondaryColor = 0xFF995500;
                break;
            case 5: // Glacial Turquoise (Rare Color preset)
                primaryColor = 0xFF00E5FF;
                secondaryColor = 0xFF004B57;
                break;
            case 6: // Vampire Crimson Rose (Rare Color preset)
                primaryColor = 0xFFFF003C;
                secondaryColor = 0xFF57000F;
                break;
            case 7: // Emerald Elixir (Rare Color preset)
                primaryColor = 0xFF00FF88;
                secondaryColor = 0xFF005022;
                break;
            case 8: // Cosmic Nebula Pink (Rare Color preset)
                primaryColor = 0xFFFF00C4;
                secondaryColor = 0xFF5E004B;
                break;
            case 9: // Sunset Horizon Orange (Rare Color preset)
                primaryColor = 0xFFFF7F00;
                secondaryColor = 0xFF591D00;
                break;
            case 0: // Cyber Neon Blue (Default)
            default:
                primaryColor = 0xFF00F0FF;
                secondaryColor = 0xFF005BFF;
                break;
        }

        // 1. Tint BackgroundAnimationView and apply the correct animation mode
        com.kdt.mcgui.BackgroundAnimationView animBgView = view.findViewById(R.id.background_animation_view);
        if (animBgView != null) {
            animBgView.setAnimationType(bgAnimType);
            animBgView.setThemeColors(primaryColor, secondaryColor);
        }

        // 2. Tint hero Play Button with the active gradient
        View playBtn = view.findViewById(R.id.play_button);
        if (playBtn != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { secondaryColor, primaryColor }
            );
            gd.setCornerRadius(view.getResources().getDimension(R.dimen._8sdp));
            gd.setStroke((int)view.getResources().getDimension(R.dimen._1sdp), primaryColor);
            playBtn.setBackground(gd);
        }

        // 3. Tint the advancement board border programmatically if it's visible
        View advToast = view.findViewById(R.id.advancement_toast_layout);
        if (advToast != null && advToast.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable advBg = (android.graphics.drawable.GradientDrawable) advToast.getBackground();
            advBg.setStroke((int)view.getResources().getDimension(R.dimen._1sdp), primaryColor);
        }

        // 4. Recursively scan the active view hierarchy and apply theme colors instantly to all MineButtons (Step 2)
        updateMineButtonsInView(view, primaryColor, themeIndex);
    }

    private void openThemeCustomizerDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_customize_theme)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            View optBlue = dialog.findViewById(R.id.theme_option_blue);
            View optRed = dialog.findViewById(R.id.theme_option_red);
            View optGreen = dialog.findViewById(R.id.theme_option_green);
            View optPurple = dialog.findViewById(R.id.theme_option_purple);
            View optGold = dialog.findViewById(R.id.theme_option_gold);
            View optTurquoise = dialog.findViewById(R.id.theme_option_turquoise);
            View optCrimson = dialog.findViewById(R.id.theme_option_crimson);
            View optEmerald = dialog.findViewById(R.id.theme_option_emerald);
            View optPink = dialog.findViewById(R.id.theme_option_pink);
            View optOrange = dialog.findViewById(R.id.theme_option_orange);

            View tabSelectColour = dialog.findViewById(R.id.tab_select_colour);
            View tabSelectAnimation = dialog.findViewById(R.id.tab_select_animation);
            View panelColorWorkspace = dialog.findViewById(R.id.panel_color_workspace);
            View panelAnimationWorkspace = dialog.findViewById(R.id.panel_animation_workspace);

            View btnClose = dialog.findViewById(R.id.btn_close_customizer);

            android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());

            // Color Themes mapping and binding (10 Presets - Step 4)
            java.util.HashMap<View, Integer> themeMap = new java.util.HashMap<>();
            themeMap.put(optBlue, 0);
            themeMap.put(optRed, 1);
            themeMap.put(optGreen, 2);
            themeMap.put(optPurple, 3);
            themeMap.put(optGold, 4);
            themeMap.put(optTurquoise, 5);
            themeMap.put(optCrimson, 6);
            themeMap.put(optEmerald, 7);
            themeMap.put(optPink, 8);
            themeMap.put(optOrange, 9);

            for (java.util.Map.Entry<View, Integer> entry : themeMap.entrySet()) {
                View card = entry.getKey();
                int idx = entry.getValue();
                if (card != null) {
                    card.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        prefs.edit().putInt("launcher_theme_color", idx).apply();
                        applyThemeColors(mRootView);
                        Toast.makeText(requireContext(), "THEME ACCENT CHANGED SUCCESSFULLY!", Toast.LENGTH_SHORT).show();
                    });
                }
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

            // Bind the 20 Background Animation Options (Classic + Anime - Step 4)
            int[] animIds = {
                R.id.anim_opt_0, R.id.anim_opt_1, R.id.anim_opt_2, R.id.anim_opt_3, R.id.anim_opt_4,
                R.id.anim_opt_5, R.id.anim_opt_6, R.id.anim_opt_7, R.id.anim_opt_8, R.id.anim_opt_9,
                R.id.anim_opt_10, R.id.anim_opt_11, R.id.anim_opt_12, R.id.anim_opt_13, R.id.anim_opt_14,
                R.id.anim_opt_15, R.id.anim_opt_16, R.id.anim_opt_17, R.id.anim_opt_18, R.id.anim_opt_19
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
                        Toast.makeText(requireContext(), "BACKGROUND ANIMATION SWAPPED SUCCESSFULLY!", Toast.LENGTH_SHORT).show();
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
                });
            }
        });

        dialog.show();
    }

    private void syncSkinToMinecraftResourcePack(Context context, String skinPath) {
        if (context == null || skinPath == null) return;
        try {
            File packDir = new File(Tools.DIR_GAME_HOME, "resourcepacks/FEAR_Skin_Pack");
            File entityDir = new File(packDir, "assets/minecraft/textures/entity");
            entityDir.mkdirs();

            File stevePng = new File(entityDir, "steve.png");
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
            File optionsFile = new File(Tools.DIR_GAME_HOME, "options.txt");
            if (optionsFile.exists()) {
                String optionsContent = readStringFromFile(optionsFile);
                if (optionsContent != null && !optionsContent.contains("FEAR_Skin_Pack")) {
                    if (optionsContent.contains("resourcePacks:[")) {
                        optionsContent = optionsContent.replace("resourcePacks:[", "resourcePacks:[\"file/FEAR_Skin_Pack\",");
                        writeStringToFile(optionsFile, optionsContent);
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
            byte[] buf = new byte[1024];
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

        // SPLIT-PANE 3: SKIN CUSTOMIZER (Zalith & Premium Adaptive)
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

                java.lang.Runnable updateModelButtonsUI = () -> {
                    boolean currentIsAlex = prefs.getBoolean("active_skin_is_alex", false);
                    if (currentIsAlex) {
                        btnAlexModel.setBackgroundResource(R.drawable.premium_button_bg);
                        btnAlexModel.setTextColor(Color.WHITE);
                        btnSteveModel.setBackgroundResource(R.drawable.premium_glass_black_bg);
                        btnSteveModel.setTextColor(Color.WHITE);
                    } else {
                        btnSteveModel.setBackgroundResource(R.drawable.premium_button_bg);
                        btnSteveModel.setTextColor(Color.WHITE);
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
                    steveTitle.setTextColor(Color.WHITE);
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

        // SPLIT-PANE 4: ACCOUNT HUB (Microsoft & Local Offline login panels side-by-side with profile management)
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
            View cardMs = accountHubView.findViewById(R.id.card_type_ms);
            View cardMojang = accountHubView.findViewById(R.id.card_type_mojang);
            View cardLocal = accountHubView.findViewById(R.id.card_type_local);
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
                    emptyState.setVisibility(accountListWrapper[0].isEmpty() ? View.VISIBLE : View.GONE);
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

                @Override
                public void onBindViewHolder(@NonNull VH h, int position) {
                    net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount acc = accountListWrapper[0].get(position);
                    h.username.setText(acc.username);

                    String typeLabel = "Local";
                    if (acc.authType != null) {
                        switch (acc.authType) {
                            case MICROSOFT: typeLabel = "Microsoft"; break;
                            case ELY_BY:    typeLabel = "Ely.by";    break;
                            default:        typeLabel = "Local";     break;
                        }
                    }
                    h.type.setText(typeLabel);

                    boolean isListSelected = selectedAccWrapper[0] != null && selectedAccWrapper[0].mSaveLocation != null
                            && acc.mSaveLocation != null
                            && selectedAccWrapper[0].mSaveLocation.getName().equals(acc.mSaveLocation.getName());

                    if (isListSelected) {
                        h.itemView.setBackgroundResource(R.drawable.premium_auth_type_card_bg);
                    } else {
                        h.itemView.setBackgroundResource(R.drawable.premium_glass_black_bg);
                    }

                    boolean isCurrentActive = currentActive != null && currentActive.mSaveLocation != null
                            && acc.mSaveLocation != null
                            && currentActive.mSaveLocation.getName().equals(currentActive.mSaveLocation.getName()); // fix compare always true, we compare acc to active!

                    isCurrentActive = currentActive != null && currentActive.mSaveLocation != null
                            && acc.mSaveLocation != null
                            && currentActive.mSaveLocation.getName().equals(acc.mSaveLocation.getName());

                    if (isCurrentActive) {
                        h.statusText.setText("Active");
                        h.statusText.setTextColor(Color.parseColor("#FF4D4D"));
                        h.statusDot.setBackgroundColor(Color.parseColor("#FF4D4D"));
                    } else {
                        h.statusText.setText(typeLabel);
                        h.statusText.setTextColor(Color.parseColor("#80FFFFFF"));
                        h.statusDot.setBackgroundColor(Color.parseColor("#80FFFFFF"));
                    }

                    h.itemView.setOnClickListener(v -> {
                        selectedAccWrapper[0] = acc;
                        notifyDataSetChanged();
                    });

                    h.deleteBtn.setOnClickListener(v -> {
                        PopupMenu popup = new PopupMenu(v.getContext(), h.deleteBtn);
                        popup.getMenu().add("Delete");
                        popup.setOnMenuItemClickListener(item -> {
                            if ("Delete".equals(item.getTitle())) {
                                try {
                                    net.kdt.pojavlaunch.authenticator.accounts.Accounts.delete(acc);
                                    loadAccountsList.run();
                                    notifyDataSetChanged();
                                    if (selectedAccWrapper[0] != null && selectedAccWrapper[0].mSaveLocation != null
                                            && acc.mSaveLocation != null
                                            && selectedAccWrapper[0].mSaveLocation.getName().equals(acc.mSaveLocation.getName())) {
                                        selectedAccWrapper[0] = null;
                                    }
                                    refreshAccountUI();
                                    Toast.makeText(requireContext(), "Account removed", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            return true;
                        });
                        popup.show();
                    });
                }

                @Override public int getItemCount() { return accountListWrapper[0].size(); }

                class VH extends RecyclerView.ViewHolder {
                    TextView username, type, statusText;
                    View statusDot, deleteBtn;
                    VH(@NonNull View v) {
                        super(v);
                        username   = v.findViewById(R.id.account_username);
                        type       = v.findViewById(R.id.account_type);
                        statusText = v.findViewById(R.id.account_status_text);
                        statusDot  = v.findViewById(R.id.account_status_dot);
                        deleteBtn  = v.findViewById(R.id.account_delete_btn);
                    }
                }
            }

            HubAdapter adapter = new HubAdapter();
            if (recyclerView != null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
                recyclerView.setAdapter(adapter);
            }

            java.lang.Runnable updateAuthUI = () -> {
                if (cardMs != null) cardMs.setSelected(selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.MICROSOFT);
                if (cardMojang != null) cardMojang.setSelected(selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.ELY_BY);
                if (cardLocal != null) cardLocal.setSelected(selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.LOCAL);

                if (inputUsername != null) {
                    if (selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.LOCAL) {
                        inputUsername.setEnabled(true);
                        inputUsername.setAlpha(1.0f);
                    } else {
                        inputUsername.setEnabled(false);
                        inputUsername.setAlpha(0.4f);
                        inputUsername.setText("");
                    }
                }
                if (errorText != null) errorText.setVisibility(View.GONE);
            };

            updateAuthUI.run();

            if (cardMs != null) cardMs.setOnClickListener(v -> { selectedAuthType[0] = net.kdt.pojavlaunch.authenticator.AuthType.MICROSOFT; updateAuthUI.run(); });
            if (cardMojang != null) cardMojang.setOnClickListener(v -> { selectedAuthType[0] = net.kdt.pojavlaunch.authenticator.AuthType.ELY_BY; updateAuthUI.run(); });
            if (cardLocal != null) cardLocal.setOnClickListener(v -> { selectedAuthType[0] = net.kdt.pojavlaunch.authenticator.AuthType.LOCAL; updateAuthUI.run(); });

            if (btnSwitchAccount != null) {
                btnSwitchAccount.setOnClickListener(v -> {
                    if (selectedAccWrapper[0] != null) {
                        net.kdt.pojavlaunch.authenticator.accounts.Accounts.setCurrent(selectedAccWrapper[0]);
                        refreshAccountUI();
                        Toast.makeText(requireContext(), "Switched to " + selectedAccWrapper[0].username, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(requireContext(), "Please select an account first", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (btnAddAccount != null) {
                btnAddAccount.setOnClickListener(v -> {
                    if (selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.MICROSOFT) {
                        dialog.dismiss();
                        Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
                        return;
                    }
                    if (selectedAuthType[0] == net.kdt.pojavlaunch.authenticator.AuthType.ELY_BY) {
                        Toast.makeText(requireContext(), "Mojang Account login is migrated to Microsoft. Please select Microsoft Account.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (inputUsername == null) return;
                    String username = inputUsername.getText().toString().trim();

                    if (android.text.TextUtils.isEmpty(username)) {
                        if (errorText != null) { errorText.setText("Username cannot be empty"); errorText.setVisibility(View.VISIBLE); }
                        return;
                    }
                    if (username.length() < 3) {
                        if (errorText != null) { errorText.setText("Username must be at least 3 characters"); errorText.setVisibility(View.VISIBLE); }
                        return;
                    }
                    if (username.length() > 16) {
                        if (errorText != null) { errorText.setText("Username must be 16 characters or less"); errorText.setVisibility(View.VISIBLE); }
                        return;
                    }
                    if (!username.matches("[a-zA-Z0-9_]+")) {
                        if (errorText != null) { errorText.setText("Only letters, numbers and _ allowed"); errorText.setVisibility(View.VISIBLE); }
                        return;
                    }

                    if (errorText != null) errorText.setVisibility(View.GONE);

                    try {
                        net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount account = net.kdt.pojavlaunch.authenticator.accounts.Accounts.create(acc -> {
                            acc.username    = username;
                            acc.authType    = net.kdt.pojavlaunch.authenticator.AuthType.LOCAL;
                            acc.accessToken = "0";
                            acc.profileId   = "00000000-0000-0000-0000-000000000000";
                            acc.refreshToken = "0";
                        });
                        net.kdt.pojavlaunch.authenticator.accounts.Accounts.setCurrent(account);
                        refreshAccountUI();
                        Toast.makeText(requireContext(), "Account '" + username + "' created!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } catch (Exception e) {
                        if (errorText != null) { errorText.setText("Failed: " + e.getMessage()); errorText.setVisibility(View.VISIBLE); }
                    }
                });
            }

            rightPane.addView(accountHubView);
        });

        // SPLIT-PANE 5: INPUT MAPPING
        navControls.setOnClickListener(v2 -> {
            v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            resetNavButtons.run();
            navControls.setBackgroundResource(R.drawable.premium_button_bg);
            navControls.setTextColor(0xFFFFFFFF);

            rightPane.removeAllViews();
            Button mapBtn = new Button(requireContext());
            mapBtn.setText("OPEN CUSTOM CONTROLS MAPPING");
            mapBtn.setBackgroundResource(R.drawable.premium_button_bg);
            mapBtn.setTextColor(0xFFFFFFFF);
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
                navModpacks.setTextColor(0xFFFFFFFF);

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
                navAddons.setTextColor(0xFFFFFFFF);

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
            navLogs.setTextColor(0xFFFFFFFF);

            rightPane.removeAllViews();
            Button shareBtn = new Button(requireContext());
            shareBtn.setText("EXPORT SYSTEMS LOGS TELEMETRY");
            shareBtn.setBackgroundResource(R.drawable.premium_button_bg);
            shareBtn.setTextColor(0xFFFFFFFF);
            shareBtn.setOnClickListener(vShare -> {
                dialog.dismiss();
                shareLog(requireContext());
            });
            rightPane.addView(shareBtn);
        });

        // SPLIT-PANE 9: CREATORS & INFO
        if (navInfo != null) {
            navInfo.setOnClickListener(v2 -> {
                v2.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                resetNavButtons.run();
                navInfo.setBackgroundResource(R.drawable.premium_button_bg);
                navInfo.setTextColor(0xFFFFFFFF);

                rightPane.removeAllViews();
                View infoView = dialog.getLayoutInflater().inflate(R.layout.dialog_creators_info, rightPane, false);

                // Hide background of infoView inside FrameLayout to blend seamlessly
                infoView.setBackground(null);

                View btnTwicefear = infoView.findViewById(R.id.btn_yt_twicefear);
                View btnHellzior = infoView.findViewById(R.id.btn_yt_hellzior);

                if (btnTwicefear != null) {
                    btnTwicefear.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@twicefear3?si=GlmuDrjczuTf63tg"));
                        startActivity(intent);
                    });
                }

                if (btnHellzior != null) {
                    btnHellzior.setOnClickListener(v -> {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        net.kdt.pojavlaunch.SoundManager.playClick();
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@hellzior01?si=wYc8fMKTlddEpPlQ"));
                        startActivity(intent);
                    });
                }

                rightPane.addView(infoView);
            });
        }

        // Default selection
        if ("skin".equals(defaultTab)) {
            navSkin.performClick();
        } else if ("account".equals(defaultTab)) {
            navAccount.performClick();
        } else {
            navSettings.performClick();
        }

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

        if (mRootView != null) {
            refreshSkinHeadDisplay(mRootView);
        }
    }

    private void refreshSkinHeadDisplay(View view) {
        com.kdt.mcgui.MinecraftSkinView skinView = view.findViewById(R.id.homepage_skin_head);
        if (skinView == null) return;

        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String activeSkinPath = prefs.getString("active_skin_path", "steve");
        boolean activeSkinIsAlex = prefs.getBoolean("active_skin_is_alex", false);

        skinView.setShowHeadOnly(true);
        skinView.loadSkin(activeSkinPath, activeSkinIsAlex);

        // Auto-rotation animation loop for 3D skin head (360 degrees continuous, looking straight)
        if (mHeadRotationAnimator != null) {
            mHeadRotationAnimator.cancel();
        }
        mHeadRotationAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f);
        mHeadRotationAnimator.setDuration(6000); // 6 seconds for a full smooth 360 rotation
        mHeadRotationAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mHeadRotationAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        mHeadRotationAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            skinView.setRotationAngles(val, 0f);
        });
        mHeadRotationAnimator.start();

        // Setup Chat Bubble typed greetings loop
        TextView chatBubble = view.findViewById(R.id.homepage_chat_bubble);
        if (chatBubble != null) {
            if (mChatBubbleHandler == null) {
                mChatBubbleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            } else {
                if (mChatBubbleRunnable != null) {
                    mChatBubbleHandler.removeCallbacks(mChatBubbleRunnable);
                }
            }

            mChatBubbleRunnable = new java.lang.Runnable() {
                private final java.util.Random random = new java.util.Random();
                @Override
                public void run() {
                    String msg = CHAT_MESSAGES[random.nextInt(CHAT_MESSAGES.length)];
                    chatBubble.setText(msg);
                    chatBubble.setVisibility(View.VISIBLE);
                    chatBubble.setAlpha(0f);
                    chatBubble.setScaleX(0f);
                    chatBubble.setScaleY(0f);

                    // Animate scale up & fade in
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
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        refreshAccountUI();
        updateVersionText();
    }

    @Override
    public void onDestroyView() {
        if (mHeadRotationAnimator != null) {
            mHeadRotationAnimator.cancel();
        }
        if (mChatBubbleHandler != null && mChatBubbleRunnable != null) {
            mChatBubbleHandler.removeCallbacks(mChatBubbleRunnable);
        }
        if (mAnnouncementHandler != null && mAnnouncementRunnable != null) {
            mAnnouncementHandler.removeCallbacks(mAnnouncementRunnable);
        }
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mPlayStateListener);
    }
}