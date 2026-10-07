package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.fearlauncher.fear.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FearPerformanceMode;
import net.kdt.pojavlaunch.utils.MCOptionUtils;

import java.io.File;
import java.util.Locale;

/**
 * The performance dashboard.
 *
 * Every row here writes a real setting - either a key in the game's own options.txt, which
 * the game reads on its next start, or a launcher preference that decides how the game is
 * launched. Nothing is decorative: each one is a lever that measurably moves frame time,
 * and the order runs from the cheapest visual change to the most expensive.
 *
 * The values are written into the SELECTED INSTANCE's options.txt, so they apply to the
 * game the player is actually about to launch and survive a reinstall of the launcher.
 */
public class PerformanceFragment extends Fragment {

    public static final String TAG = "PERFORMANCE_FRAGMENT";

    /** Persisted flag: the Smooth PvP profile is currently applied. */
    private static final String KEY_PVP = "smooth_pvp_profile";
    /** Snapshot keys holding the values the profile overwrote, for a clean restore. */
    private static final String KEY_PVP_RENDERER = "pvp_prev_renderer";
    private static final String KEY_PVP_FORCE_VSYNC = "pvp_prev_force_vsync";
    private static final String KEY_PVP_VSYNC_IN_ZINK = "pvp_prev_vsync_in_zink";
    /** Snapshot keys for the affinity, sustained-performance and render-scale the profile sets. */
    private static final String KEY_PVP_AFFINITY = "pvp_prev_big_core_affinity";
    private static final String KEY_PVP_SUSTAINED = "pvp_prev_sustained";
    private static final String KEY_PVP_SCALE = "pvp_prev_resolution_ratio";
    private static final String KEY_PVP_OPTION_PREFIX = "pvp_prev_option_";
    /** Sentinel for an options.txt key that did not exist before the profile was enabled. */
    private static final String PVP_OPTION_ABSENT = "__pvp_absent__";
    /** The options.txt keys the Smooth PvP profile writes. */
    private static final String[] PVP_OPTION_KEYS = {
            "enableVsync", "maxFps", "entityDistanceScaling",
            "particles", "graphicsMode", "entityShadows", "menuBackgroundBlurriness"
    };

    /** Snapshot prefix for the values the graphics-quality tiers overwrite, for a clean reset. */
    private static final String KEY_QUALITY_OPTION_PREFIX = "graphics_prev_option_";
    /** Sentinel for an options.txt key that did not exist before a tier was chosen. */
    private static final String QUALITY_OPTION_ABSENT = "__graphics_absent__";

    private File mGameDir;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_performance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Instance instance = Instances.loadSelectedInstance();
        mGameDir = instance != null ? instance.getGameDirectory() : null;
        if (mGameDir != null) MCOptionUtils.load(mGameDir.getAbsolutePath());

        view.findViewById(R.id.perf_back).setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            Tools.swapFragment(requireActivity(),
                    net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment.class,
                    net.kdt.pojavlaunch.LauncherActivity.SETTING_FRAGMENT_TAG, null);
        });

        TextView summary = view.findViewById(R.id.perf_summary);
        summary.setText(R.string.perf_summary);

        LinearLayout rows = view.findViewById(R.id.perf_rows);

        // ---- graphics quality ------------------------------------------------------
        // ONE selector, three tiers. It writes the game's own graphics options as a coherent
        // bundle into THIS instance's options.txt, so the world can look good without touching
        // the frame-rate keys (maxFps/enableVsync stay owned by the always-write path). It does
        // not list the individual vanilla options, and it snapshots everything it sets so
        // "reset" puts the player back to what they had. The values take effect on the next
        // launch, which the subtitle and the toast both say.
        addGraphicsQualityRow(rows, R.string.perf_graphics_quality,
                R.string.perf_quality_low, R.string.perf_quality_balanced,
                R.string.perf_quality_high, R.string.perf_quality_reset,
                graphicsQualityDescription());

        // ---- smooth PvP profile -----------------------------------------------------
        // One switch that applies the whole smooth-PvP recipe at once. It snapshots
        // every value it overwrites, so switching it off restores exactly what the
        // player had before - nothing here is a one-way door. The options.txt changes
        // take effect on the next launch, which the subtitle and toast both say.
        addSwitch(rows, R.string.perf_pvp_profile, R.string.perf_pvp_profile_desc,
                () -> LauncherPreferences.DEFAULT_PREF.getBoolean(KEY_PVP, false),
                this::setSmoothPvpProfile);

        // ---- camera -----------------------------------------------------------------
        addSwitch(rows, R.string.perf_smooth_camera, R.string.perf_smooth_camera_desc,
                () -> "true".equals(MCOptionUtils.get("smoothCamera")),
                on -> setOption("smoothCamera", on ? "true" : "false"));

        // ---- frame rate -------------------------------------------------------------
        addSwitch(rows, R.string.perf_uncap_fps, R.string.perf_uncap_fps_desc,
                () -> {
                    String v = MCOptionUtils.get("maxFps");
                    return v != null && Integer.parseInt(safeInt(v)) >= 200;
                },
                on -> setOption("maxFps", on ? "260" : "120"));
        addSwitch(rows, R.string.perf_vsync, R.string.perf_vsync_desc,
                // Reads as ON only when the game option AND both launcher switches
                // are off: FORCE_VSYNC alone is not enough, FEAR_VSYNC_IN_ZINK forces
                // the swap interval independently and would cap the frame rate.
                () -> "false".equals(MCOptionUtils.get("enableVsync"))
                        && !LauncherPreferences.PREF_FORCE_VSYNC
                        && !LauncherPreferences.PREF_VSYNC_IN_ZINK,
                on -> {
                    setOption("enableVsync", on ? "false" : "true");
                    LauncherPreferences.PREF_FORCE_VSYNC = !on;
                    LauncherPreferences.PREF_VSYNC_IN_ZINK = !on;
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("force_vsync", !on)
                            .putBoolean("vsync_in_zink", !on).apply();
                });

        // ---- the levers the game cannot offer ---------------------------------------
        // The game's own video settings already cover render distance, graphics mode and
        // the rest, so those are deliberately NOT repeated here. These change something the
        // game has no setting for: how many pixels the launcher asks it to draw, how much of
        // the phone's CPU it is allowed to use, and how Android clocks it under load.
        addSlider(rows, R.string.perf_resolution, R.string.perf_resolution_desc,
                30, 100, () -> Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f),
                value -> {
                    LauncherPreferences.PREF_SCALE_FACTOR = value / 100f;
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putInt("resolutionRatio", value).apply();
                });
        addSwitch(rows, R.string.perf_affinity, R.string.perf_affinity_desc,
                () -> LauncherPreferences.PREF_BIG_CORE_AFFINITY,
                on -> {
                    LauncherPreferences.PREF_BIG_CORE_AFFINITY = on;
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("bigCoreAffinity", on).apply();
                });
        addSwitch(rows, R.string.perf_sustained, R.string.perf_sustained_desc,
                () -> LauncherPreferences.PREF_SUSTAINED_PERFORMANCE,
                on -> {
                    LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = on;
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("sustainedPerformance", on).apply();
                });

        addSwitch(rows, R.string.perf_menu_blur, R.string.perf_menu_blur_desc,
                () -> "0".equals(MCOptionUtils.get("menuBackgroundBlurriness")),
                on -> setOption("menuBackgroundBlurriness", on ? "0" : "1"));

        // ---- the launcher's own smoothness ------------------------------------------
        addSwitch(rows, R.string.perf_smooth_launcher, R.string.perf_smooth_launcher_desc,
                () -> LauncherPreferences.DEFAULT_PREF
                        .getBoolean("smooth_launcher_ui", false),
                on -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("smooth_launcher_ui", on).apply();
                    LauncherPreferences.PREF_SMOOTH_LAUNCHER_UI = on;
                });

        // The recorder's output. Tapping it shares the report, which is the whole point:
        // the numbers in it are what a fix has to be built from.
        addSwitch(rows, R.string.perf_lag_report, R.string.perf_lag_report_desc,
                () -> net.kdt.pojavlaunch.utils.LagWatch.reportFile(mGameDir) != null,
                on -> {
                    java.io.File report = net.kdt.pojavlaunch.utils.LagWatch.reportFile(mGameDir);
                    if (report == null) {
                        Toast.makeText(getContext(), R.string.perf_lag_report_none,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        android.content.Intent share = new android.content.Intent(
                                android.content.Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(android.content.Intent.EXTRA_STREAM,
                                androidx.core.content.FileProvider.getUriForFile(requireContext(),
                                        requireContext().getPackageName() + ".fileprovider", report));
                        share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(android.content.Intent.createChooser(share,
                                getString(R.string.perf_lag_report)));
                    } catch (Throwable t) {
                        Toast.makeText(getContext(), report.getAbsolutePath(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Applies or reverts the Smooth PvP profile.
     *
     * <p>Enabling it snapshots the renderer, the seven options.txt keys it touches, the VSync
     * preference, the big-core-affinity and sustained-performance switches and the render scale,
     * then applies the recipe; disabling it writes the snapshot back. The snapshot lives in the
     * default preferences under {@code pvp_prev_*} keys, so it survives the fragment being
     * recreated. Game options only take effect on the next launch, which the row's subtitle and
     * toast both say.</p>
     */
    private void setSmoothPvpProfile(boolean on) {
        android.content.SharedPreferences pref = LauncherPreferences.DEFAULT_PREF;
        if (on) {
            // Snapshot everything we are about to overwrite, once, so OFF restores it.
            if (!pref.getBoolean(KEY_PVP, false)) {
                android.content.SharedPreferences.Editor snapshot = pref.edit();
                snapshot.putString(KEY_PVP_RENDERER, LauncherPreferences.PREF_RENDERER);
                snapshot.putBoolean(KEY_PVP_FORCE_VSYNC, LauncherPreferences.PREF_FORCE_VSYNC);
                snapshot.putBoolean(KEY_PVP_VSYNC_IN_ZINK, LauncherPreferences.PREF_VSYNC_IN_ZINK);
                snapshot.putBoolean(KEY_PVP_AFFINITY, LauncherPreferences.PREF_BIG_CORE_AFFINITY);
                snapshot.putBoolean(KEY_PVP_SUSTAINED, LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
                snapshot.putInt(KEY_PVP_SCALE, pref.getInt("resolutionRatio",
                        Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f)));
                for (String key : PVP_OPTION_KEYS) {
                    String value = MCOptionUtils.get(key);
                    snapshot.putString(KEY_PVP_OPTION_PREFIX + key,
                            value == null ? PVP_OPTION_ABSENT : value);
                }
                snapshot.putBoolean(KEY_PVP, true).apply();
            }

            // Force the LTW renderer (the launcher default).
            LauncherPreferences.PREF_RENDERER = "opengles3_ltw";
            pref.edit().putString("renderer", "opengles3_ltw").apply();

            // Game options, written through the launcher's own options.txt helper.
            setOption("enableVsync", "false");
            setOption("maxFps", "260");
            setOption("entityDistanceScaling", "0.8");
            setOption("particles", "2");
            setOption("graphicsMode", "0");
            setOption("entityShadows", "false");
            setOption("menuBackgroundBlurriness", "0");

            // Keep the dashboard's VSync row and its backing preference consistent.
            LauncherPreferences.PREF_FORCE_VSYNC = false;
            pref.edit().putBoolean("force_vsync", false).apply();
            // FEAR_VSYNC_IN_ZINK forces the swap interval on its own, so the profile
            // must clear it too or the "uncapped" profile would still be capped.
            LauncherPreferences.PREF_VSYNC_IN_ZINK = false;
            pref.edit().putBoolean("vsync_in_zink", false).apply();

            // The profile is a complete one-tap setup: it also turns on the big-core affinity
            // and sustained-performance rows, and sets the render scale to the value the
            // auto-tuner last chose (never a hardcoded one), so it rides the tuned scale
            // instead of fighting it.
            LauncherPreferences.PREF_BIG_CORE_AFFINITY = true;
            pref.edit().putBoolean("bigCoreAffinity", true).apply();
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = true;
            pref.edit().putBoolean("sustainedPerformance", true).apply();
            int tunedScale = pref.getInt("fear_auto_scale_last",
                    Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f));
            tunedScale = Math.max(50, Math.min(100, tunedScale));
            LauncherPreferences.PREF_SCALE_FACTOR = tunedScale / 100f;
            pref.edit().putInt("resolutionRatio", tunedScale).apply();

            // The profile owns the graphics keys from now on, so it survives the next launch:
            // applyGraphicsQuality() re-applies its graphics subset last. This is the "last
            // profile picked wins" rule - the tier and this profile can never fight.
            pref.edit().putString(FearPerformanceMode.PREF_QUALITY_OWNER, "pvp").apply();

            Toast.makeText(getContext(), R.string.perf_pvp_applied, Toast.LENGTH_LONG).show();
        } else {
            // Restore the renderer.
            String renderer = pref.getString(KEY_PVP_RENDERER, LauncherPreferences.PREF_RENDERER);
            LauncherPreferences.PREF_RENDERER = renderer;
            pref.edit().putString("renderer", renderer).apply();

            // Restore the game options that had a value before the profile was enabled.
            for (String key : PVP_OPTION_KEYS) {
                String previous = pref.getString(KEY_PVP_OPTION_PREFIX + key, PVP_OPTION_ABSENT);
                if (!PVP_OPTION_ABSENT.equals(previous)) setOption(key, previous);
            }

            // Restore the VSync preference.
            boolean forceVsync = pref.getBoolean(KEY_PVP_FORCE_VSYNC,
                    LauncherPreferences.PREF_FORCE_VSYNC);
            LauncherPreferences.PREF_FORCE_VSYNC = forceVsync;
            pref.edit().putBoolean("force_vsync", forceVsync).apply();
            boolean vsyncInZink = pref.getBoolean(KEY_PVP_VSYNC_IN_ZINK,
                    LauncherPreferences.PREF_VSYNC_IN_ZINK);
            LauncherPreferences.PREF_VSYNC_IN_ZINK = vsyncInZink;
            pref.edit().putBoolean("vsync_in_zink", vsyncInZink).apply();

            // Restore the affinity, sustained-performance and render-scale the profile set.
            boolean affinity = pref.getBoolean(KEY_PVP_AFFINITY,
                    LauncherPreferences.PREF_BIG_CORE_AFFINITY);
            LauncherPreferences.PREF_BIG_CORE_AFFINITY = affinity;
            pref.edit().putBoolean("bigCoreAffinity", affinity).apply();
            boolean sustained = pref.getBoolean(KEY_PVP_SUSTAINED,
                    LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = sustained;
            pref.edit().putBoolean("sustainedPerformance", sustained).apply();
            int scale = pref.getInt(KEY_PVP_SCALE,
                    Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f));
            LauncherPreferences.PREF_SCALE_FACTOR = scale / 100f;
            pref.edit().putInt("resolutionRatio", scale).apply();

            // Drop the snapshot and clear the flag.
            android.content.SharedPreferences.Editor cleanup = pref.edit();
            cleanup.remove(KEY_PVP_RENDERER);
            cleanup.remove(KEY_PVP_FORCE_VSYNC);
            cleanup.remove(KEY_PVP_VSYNC_IN_ZINK);
            cleanup.remove(KEY_PVP_AFFINITY);
            cleanup.remove(KEY_PVP_SUSTAINED);
            cleanup.remove(KEY_PVP_SCALE);
            for (String key : PVP_OPTION_KEYS) cleanup.remove(KEY_PVP_OPTION_PREFIX + key);
            // Hand the graphics keys back to the chosen tier, if there is one, so the tier (not
            // the profile) is what gets re-applied on the next launch.
            cleanup.putString(FearPerformanceMode.PREF_QUALITY_OWNER,
                    pref.getString(FearPerformanceMode.PREF_QUALITY_TIER, "").isEmpty()
                            ? "" : "tier");
            cleanup.putBoolean(KEY_PVP, false).apply();
        }
    }

    // ---- graphics quality -------------------------------------------------------------

    /**
     * One titled row with a three-way Low/Balanced/High selector and a reset link.
     *
     * <p>Choosing a tier writes that tier's whole bundle into the instance's options.txt and
     * remembers the choice, so the dashboard shows it selected again when rebuilt. The first
     * time any tier is chosen it snapshots every key the tiers touch; "reset" writes those
     * values back, so the player can always return to their own settings. Nothing here writes
     * {@code maxFps} or {@code enableVsync}.</p>
     */
    private void addGraphicsQualityRow(LinearLayout parent, int titleRes, int lowRes,
                                       int balancedRes, int highRes, int resetRes,
                                       CharSequence desc) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_perf_choice, parent, false);
        ((TextView) row.findViewById(R.id.perf_row_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.perf_row_desc)).setText(desc);
        TextView low = row.findViewById(R.id.perf_choice_low);
        TextView balanced = row.findViewById(R.id.perf_choice_balanced);
        TextView high = row.findViewById(R.id.perf_choice_high);
        TextView reset = row.findViewById(R.id.perf_choice_reset);
        low.setText(lowRes);
        balanced.setText(balancedRes);
        high.setText(highRes);
        reset.setText(resetRes);

        final TextView[] segments = {low, balanced, high};
        final String[] tiers = {
                FearPerformanceMode.TIER_LOW,
                FearPerformanceMode.TIER_BALANCED,
                FearPerformanceMode.TIER_HIGH
        };
        styleQualitySegments(segments, tiers);
        for (int i = 0; i < segments.length; i++) {
            final String tier = tiers[i];
            segments[i].setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                setGraphicsQuality(tier);
                styleQualitySegments(segments, tiers);
            });
        }
        reset.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            resetGraphicsQuality();
            styleQualitySegments(segments, tiers);
        });
        parent.addView(row);
    }

    /** Marks the currently chosen tier, so the selector reads correctly after a rebuild. */
    private void styleQualitySegments(TextView[] segments, String[] tiers) {
        String current = LauncherPreferences.DEFAULT_PREF
                .getString(FearPerformanceMode.PREF_QUALITY_TIER, "");
        for (int i = 0; i < segments.length; i++) {
            boolean on = tiers[i].equals(current);
            segments[i].setBackgroundResource(on
                    ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
            segments[i].setTextColor(on
                    ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
        }
    }

    /**
     * Applies a graphics-quality tier. Snapshots the values it overwrites the first time any
     * tier is chosen, writes the tier's bundle, and remembers the choice. Game options apply
     * on the next launch.
     */
    private void setGraphicsQuality(String tier) {
        android.content.SharedPreferences pref = LauncherPreferences.DEFAULT_PREF;
        if (tier.equals(pref.getString(FearPerformanceMode.PREF_QUALITY_TIER, ""))) return;
        String[][] bundle = FearPerformanceMode.bundleForTier(tier);
        if (bundle == null) return;

        // Snapshot once, before the very first tier write, so a reset restores the player's own
        // values rather than another tier's.
        if (pref.getString(FearPerformanceMode.PREF_QUALITY_TIER, "").isEmpty()) {
            android.content.SharedPreferences.Editor snapshot = pref.edit();
            for (String key : FearPerformanceMode.QUALITY_KEYS) {
                String value = MCOptionUtils.get(key);
                snapshot.putString(KEY_QUALITY_OPTION_PREFIX + key,
                        value == null ? QUALITY_OPTION_ABSENT : value);
            }
            snapshot.apply();
        }

        for (String[] entry : bundle) setOption(entry[0], entry[1]);

        // The tier is now the most recently picked graphics profile, so it wins over the Smooth
        // PvP profile on the next launch.
        pref.edit()
                .putString(FearPerformanceMode.PREF_QUALITY_TIER, tier)
                .putString(FearPerformanceMode.PREF_QUALITY_OWNER, "tier")
                .apply();
        Toast.makeText(getContext(), R.string.perf_graphics_quality_applied,
                Toast.LENGTH_LONG).show();
    }

    /**
     * Restores the values the tiers overwrote and clears the choice, so the player is back to
     * their own settings. Applies on the next launch.
     */
    private void resetGraphicsQuality() {
        android.content.SharedPreferences pref = LauncherPreferences.DEFAULT_PREF;
        if (pref.getString(FearPerformanceMode.PREF_QUALITY_TIER, "").isEmpty()) return;

        for (String key : FearPerformanceMode.QUALITY_KEYS) {
            String previous = pref.getString(KEY_QUALITY_OPTION_PREFIX + key, QUALITY_OPTION_ABSENT);
            if (!QUALITY_OPTION_ABSENT.equals(previous)) setOption(key, previous);
        }

        android.content.SharedPreferences.Editor cleanup = pref.edit();
        for (String key : FearPerformanceMode.QUALITY_KEYS) {
            cleanup.remove(KEY_QUALITY_OPTION_PREFIX + key);
        }
        cleanup.putString(FearPerformanceMode.PREF_QUALITY_TIER, "");
        // If the Smooth PvP profile is on it stays the graphics owner; otherwise nothing is.
        cleanup.putString(FearPerformanceMode.PREF_QUALITY_OWNER,
                pref.getBoolean(KEY_PVP, false) ? "pvp" : "");
        cleanup.apply();
        Toast.makeText(getContext(), R.string.perf_graphics_quality_reset_done,
                Toast.LENGTH_LONG).show();
    }

    /**
     * The row description. If the instance has both Iris and a non-empty shaderpacks folder, it
     * points the player at the tiers that suit shaders; otherwise it explains the selector.
     */
    private CharSequence graphicsQualityDescription() {
        return getString(hasIrisAndShaderpacks()
                ? R.string.perf_graphics_quality_desc_shaders
                : R.string.perf_graphics_quality_desc);
    }

    /** @return true if the instance looks like it has Iris plus at least one shaderpack. */
    private boolean hasIrisAndShaderpacks() {
        if (mGameDir == null) return false;
        File shaderpacks = new File(mGameDir, "shaderpacks");
        String[] shaders = shaderpacks.isDirectory() ? shaderpacks.list() : null;
        if (shaders == null || shaders.length == 0) return false;
        File mods = new File(mGameDir, "mods");
        String[] modFiles = mods.list();
        if (modFiles == null) return false;
        for (String name : modFiles) {
            if (name.toLowerCase(Locale.ROOT).contains("iris")) return true;
        }
        return false;
    }

    // ---- rows -----------------------------------------------------------------------


    /** One titled row with a switch on the right. */
    private void addSwitch(LinearLayout parent, int titleRes, int descRes,
                           BoolGetter getter, BoolSetter setter) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_perf_switch, parent, false);
        ((TextView) row.findViewById(R.id.perf_row_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.perf_row_desc)).setText(descRes);
        SwitchCompat toggle = row.findViewById(R.id.perf_row_switch);
        toggle.setChecked(getter.get());
        toggle.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            setter.set(on);
            Toast.makeText(getContext(), R.string.perf_applied, Toast.LENGTH_SHORT).show();
        });
        parent.addView(row);
    }

    /** One titled row with a slider under it. */
    private void addSlider(LinearLayout parent, int titleRes, int descRes,
                           int min, int max, IntGetter getter, IntSetter setter) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_perf_slider, parent, false);
        ((TextView) row.findViewById(R.id.perf_row_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.perf_row_desc)).setText(descRes);
        final TextView value = row.findViewById(R.id.perf_row_value);
        SeekBar bar = row.findViewById(R.id.perf_row_seek);
        bar.setMax(max - min);
        int current = Math.max(min, Math.min(max, getter.get()));
        bar.setProgress(current - min);
        value.setText(String.valueOf(current));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(String.valueOf(min + progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setter.set(min + seekBar.getProgress());
            }
        });
        parent.addView(row);
    }

    // ---- writing ---------------------------------------------------------------------

    /** Writes one key into the instance's options.txt and flushes the file. */
    private void setOption(String key, String value) {
        try {
            if (mGameDir == null) return;
            MCOptionUtils.load(mGameDir.getAbsolutePath());
            MCOptionUtils.set(key, value);
            MCOptionUtils.save();
        } catch (Throwable t) {
            Toast.makeText(getContext(), R.string.perf_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static String safeInt(String value) {
        return safeInt(value, 0) == 0 ? "0" : value;
    }

    private static int safeInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean on); }
    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int value); }
}
