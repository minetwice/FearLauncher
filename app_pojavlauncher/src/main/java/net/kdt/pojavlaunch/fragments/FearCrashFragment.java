package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.FearToggle;

import com.fearlauncher.fear.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FearCrashGuard;

import java.io.File;

/**
 * The board shown when the previous launch died.
 *
 * It says what the game left behind, and when a mod can be worked out from that it
 * puts the mod in front of you with a switch. Turning it off renames the jar with a
 * .disabled suffix - the loader stops seeing it - and launches again.
 */
public class FearCrashFragment extends Fragment {

    public static final String TAG = "FearCrashFragment";

    private static final String DISABLED = ".disabled";

    private File mCulprit;

    public FearCrashFragment() {
        super(R.layout.fragment_fear_crash);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final FearCrashGuard.Report report = FearCrashGuard.detect(requireContext());
        if (report == null) {
            Tools.backToMainMenu(requireActivity());
            return;
        }

        TextView title = view.findViewById(R.id.crash_title);
        TextView detail = view.findViewById(R.id.crash_detail);
        if (title != null) title.setText(report.title);
        if (detail != null) {
            detail.setText(report.detail == null || report.detail.isEmpty()
                    ? "The game closed before it could report anything. The log output has the full story."
                    : report.detail);
        }

        View modRow = view.findViewById(R.id.crash_mod_row);
        TextView modName = view.findViewById(R.id.crash_mod_name);
        FearToggle modToggle = view.findViewById(R.id.crash_mod_toggle);
        View disableBtn = view.findViewById(R.id.crash_disable_btn);

        mCulprit = report.culprit;
        if (mCulprit != null) {
            if (modRow != null) modRow.setVisibility(View.VISIBLE);
            if (modName != null) {
                modName.setText(mCulprit.getName());
                modName.setTextColor(Color.WHITE);
            }
            if (modToggle != null) modToggle.setChecked(true, false);
        } else {
            if (modRow != null) modRow.setVisibility(View.GONE);
            if (disableBtn != null) disableBtn.setVisibility(View.GONE);
        }

        if (disableBtn != null) {
            disableBtn.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                boolean off = modToggle == null || modToggle.isChecked();
                applyModState(off);
                FearCrashGuard.clear(requireContext());
                relaunch();
            });
        }

        View switchRenderer = view.findViewById(R.id.crash_switch_renderer_btn);
        if (switchRenderer != null) {
            // Offer the switch when the crash is inside the vendor driver (the run was not
            // already on a renderer that avoids it), or when LTW's shader wrapper gave up on
            // a core shader - that crash cannot be avoided on LTW at all.
            boolean offerSwitch = (report.vendorDriverCrash && isOnVendorDriver())
                    || report.ltwShaderCrash;
            if (offerSwitch) {
                String target = report.ltwShaderCrash ? nonLtwRenderer() : "turnip_zink";
                if (switchRenderer instanceof android.widget.TextView) {
                    ((android.widget.TextView) switchRenderer).setText(
                            getString(R.string.crash_switch_renderer_fmt, target));
                }
                switchRenderer.setVisibility(View.VISIBLE);
                switchRenderer.setOnClickListener(v -> {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    switchRendererTo(target);
                    FearCrashGuard.clear(requireContext());
                    android.widget.Toast.makeText(requireContext(),
                            getString(R.string.crash_renderer_switched_fmt, target),
                            android.widget.Toast.LENGTH_LONG).show();
                    relaunch();
                });
            } else {
                switchRenderer.setVisibility(View.GONE);
            }
        }

        View skip = view.findViewById(R.id.crash_skip_btn);
        if (skip != null) {
            skip.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                FearCrashGuard.clear(requireContext());
                Tools.backToMainMenu(requireActivity());
            });
        }
    }

    /** true = turn the mod off, false = leave it on. */
    private void applyModState(boolean off) {
        if (mCulprit == null) return;
        String name = mCulprit.getName();
        boolean alreadyOff = name.endsWith(DISABLED);
        File target;
        if (off && !alreadyOff) {
            target = new File(mCulprit.getParentFile(), name + DISABLED);
        } else if (!off && alreadyOff) {
            target = new File(mCulprit.getParentFile(),
                    name.substring(0, name.length() - DISABLED.length()));
        } else {
            return;
        }
        mCulprit.renameTo(target);
    }

    /** True when the run went straight to the phone's own GL driver. */
    private boolean isOnVendorDriver() {
        try {
            net.kdt.pojavlaunch.instances.Instance instance =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            String renderer = instance != null && instance.renderer != null
                    ? instance.renderer
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
            return renderer != null && renderer.contains("ltw");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The non-LTW renderer this device can actually run. Turnip/Vulkan-Zink are
     * Adreno-only, so on Mali (and any other non-Adreno GPU) the Mali Zink path is
     * used instead - the same rule GameRunner applies when it picks a fallback.
     */
    private String nonLtwRenderer() {
        try {
            if (!net.kdt.pojavlaunch.utils.GLInfoUtils.getGlInfo().isAdreno()) {
                return "holy_zink_kopper";
            }
        } catch (Throwable ignored) { }
        return "turnip_zink";
    }

    /**
     * Moves the instance onto the chosen renderer. The user tapped the button, so this
     * is never a silent switch; it only writes the instance's renderer, which the next
     * launch reads.
     */
    private void switchRendererTo(String target) {
        try {
            net.kdt.pojavlaunch.instances.Instance instance =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (instance != null) {
                instance.renderer = target;
                instance.write();
            }
        } catch (Throwable ignored) { }
        // The launcher reads the selected renderer from the "renderer" preference key
        // (LauncherPreferences.PREF_RENDERER), so keep it in step with the instance as well.
        try {
            net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER = target;
            net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.edit()
                    .putString("renderer", target).apply();
        } catch (Throwable ignored) { }
    }

    private void relaunch() {
        Tools.backToMainMenu(requireActivity());
    }
}
