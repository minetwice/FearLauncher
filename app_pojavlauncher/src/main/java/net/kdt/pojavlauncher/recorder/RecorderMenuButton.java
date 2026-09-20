package net.kdt.pojavlauncher.recorder;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.SoundEffectConstants;

import androidx.annotation.Nullable;

import com.kdt.mcgui.LauncherMenuButton;

import git.artdeell.mojo.R;

/**
 * Left sidebar entry for the built-in screen recorder.
 * Self-contained: opens the recorder dashboard on click and collapses
 * the launcher tray, without needing any wiring inside MainMenuFragment.
 */
public class RecorderMenuButton extends LauncherMenuButton {

    public RecorderMenuButton(Context context) {
        super(context);
        init();
    }

    public RecorderMenuButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOnClickListener(v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            // collapse the launcher tray the same way the close button does
            try {
                View root = getRootView();
                View trayClose = root != null ? root.findViewById(R.id.tray_close) : null;
                if (trayClose != null) {
                    trayClose.performClick();
                } else {
                    View tray = root != null ? root.findViewById(R.id.settings_tray) : null;
                    if (tray != null) tray.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
            }
            RecorderDashboard.show(getContext());
        });
    }
}
