package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.FearToggle;

import git.artdeell.mojo.R;
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

    private void relaunch() {
        Tools.backToMainMenu(requireActivity());
    }
}
