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

        // ---- the big levers ---------------------------------------------------------
        addSlider(rows, R.string.perf_render_distance, R.string.perf_render_distance_desc,
                4, 16, () -> safeInt(MCOptionUtils.get("renderDistance"), 8),
                value -> setOption("renderDistance", String.valueOf(value)));
        addSlider(rows, R.string.perf_sim_distance, R.string.perf_sim_distance_desc,
                4, 12, () -> safeInt(MCOptionUtils.get("simulationDistance"), 8),
                value -> setOption("simulationDistance", String.valueOf(value)));

        // ---- cheap visual wins ------------------------------------------------------
        addSwitch(rows, R.string.perf_fast_graphics, R.string.perf_fast_graphics_desc,
                () -> "0".equals(MCOptionUtils.get("graphicsMode")),
                on -> {
                    setOption("graphicsMode", on ? "0" : "1");
                    setOption("fancyGraphics", on ? "false" : "true");
                });
        addSwitch(rows, R.string.perf_smooth_lighting, R.string.perf_smooth_lighting_desc,
                () -> "false".equals(MCOptionUtils.get("ao")),
                on -> setOption("ao", on ? "false" : "true"));
        addSwitch(rows, R.string.perf_entity_shadows, R.string.perf_entity_shadows_desc,
                () -> "false".equals(MCOptionUtils.get("entityShadows")),
                on -> setOption("entityShadows", on ? "false" : "true"));
        addSwitch(rows, R.string.perf_clouds, R.string.perf_clouds_desc,
                () -> "false".equals(MCOptionUtils.get("renderClouds")),
                on -> {
                    setOption("renderClouds", on ? "false" : "true");
                    setOption("cloudStatus", on ? "false" : "true");
                });
        addSwitch(rows, R.string.perf_particles, R.string.perf_particles_desc,
                () -> "2".equals(MCOptionUtils.get("particles")),
                on -> setOption("particles", on ? "2" : "0"));
        addSwitch(rows, R.string.perf_menu_blur, R.string.perf_menu_blur_desc,
                () -> "0".equals(MCOptionUtils.get("menuBackgroundBlurriness")),
                on -> setOption("menuBackgroundBlurriness", on ? "0" : "1"));
        addSwitch(rows, R.string.perf_biome_blend, R.string.perf_biome_blend_desc,
                () -> "0".equals(MCOptionUtils.get("biomeBlendRadius")),
                on -> setOption("biomeBlendRadius", on ? "0" : "2"));

        // ---- the launcher's own smoothness ------------------------------------------
        addSwitch(rows, R.string.perf_smooth_launcher, R.string.perf_smooth_launcher_desc,
                () -> LauncherPreferences.DEFAULT_PREF
                        .getBoolean("smooth_launcher_ui", false),
                on -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean("smooth_launcher_ui", on).apply();
                    LauncherPreferences.PREF_SMOOTH_LAUNCHER_UI = on;
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
