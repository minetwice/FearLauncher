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

        // ---- renderer ---------------------------------------------------------------
        // MobileGlues now ships inside the APK, so this is a one-tap switch rather
        // than a download. The row only appears when the native library is actually
        // present - RendererCompatUtil hides fear_v1 otherwise.
        if (net.kdt.pojavlaunch.utils.RendererCompatUtil
                .getCompatibleRenderers(requireContext()).rendererIds.contains("fear_v1")) {
            addSwitch(rows, R.string.perf_renderer_mobileglues, R.string.perf_renderer_mobileglues_desc,
                    () -> "fear_v1".equals(LauncherPreferences.PREF_RENDERER),
                    on -> {
                        if (on) {
                            String current = LauncherPreferences.PREF_RENDERER;
                            if (!"fear_v1".equals(current)) {
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString("renderer_before_mobileglues", current).apply();
                            }
                            LauncherPreferences.PREF_RENDERER = "fear_v1";
                            LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString("renderer", "fear_v1").apply();
                        } else {
                            String restore = LauncherPreferences.DEFAULT_PREF
                                    .getString("renderer_before_mobileglues", "opengles3_ltw");
                            LauncherPreferences.PREF_RENDERER = restore;
                            LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString("renderer", restore).apply();
                        }
                    });
        }

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
                () -> "false".equals(MCOptionUtils.get("enableVsync")),
                on -> {
                    setOption("enableVsync", on ? "false" : "true");
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("force_vsync", !on).apply();
                    LauncherPreferences.PREF_FORCE_VSYNC = !on;
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
