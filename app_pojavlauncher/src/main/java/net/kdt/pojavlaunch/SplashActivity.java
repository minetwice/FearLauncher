package net.kdt.pojavlaunch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.kdt.mcgui.AspectVideoView;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

/**
 * FearLauncher startup.
 *
 * Plays the FearLauncher intro clip full-screen, then hands over to the
 * launcher. All of the mandatory boot work runs first so the clip costs no
 * extra boot time, and there is a hard timeout plus tap-to-skip so a video that
 * never reports back can never trap the user on the intro.
 */
public class SplashActivity extends AppCompatActivity {
    /** Hard ceiling on the intro, whatever the media does. */
    private static final long INTRO_TIMEOUT_MS = 12000L;

    private boolean mLauncherStarted = false;

    /** Intro only after the launcher has sat idle this long. */
    private static final long IDLE_BEFORE_INTRO_MS = 25L * 60L * 1000L;
    private static final String PREF_LAST_LAUNCH = "fear_last_launch_time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            finish();
            return;
        }
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        if (shouldPlayIntro()) {
            setContentView(R.layout.activity_intro);
            playIntro();
        } else {
            // Opened recently: go straight in, no clip.
            startLauncher();
        }
    }

    /**
     * The intro is a "welcome back" - it only plays when the launcher has not
     * been opened for a while. Launch it again within 25 minutes and you land
     * straight on the interface.
     */
    private boolean shouldPlayIntro() {
        try {
            android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            long last = prefs.getLong(PREF_LAST_LAUNCH, 0L);
            long now = System.currentTimeMillis();
            prefs.edit().putLong(PREF_LAST_LAUNCH, now).apply();
            return last == 0L || (now - last) > IDLE_BEFORE_INTRO_MS;
        } catch (Throwable t) {
            return true;
        }
    }

    private void playIntro() {
        final AspectVideoView video = findViewById(R.id.intro_video);
        if (video == null) {
            startLauncher();
            return;
        }

        try {
            video.setVideoURI(Uri.parse(
                    "android.resource://" + getPackageName() + "/" + R.raw.fear_intro));

            video.setOnPreparedListener(mp -> {
                if (mp.getVideoHeight() > 0) {
                    // Keep the real aspect so the clip is never stretched.
                    video.setAspect(mp.getVideoWidth() / (float) mp.getVideoHeight());
                }
                mp.setLooping(false);
                video.start();
            });
            video.setOnCompletionListener(mp -> startLauncher());
            video.setOnErrorListener((mp, what, extra) -> {
                startLauncher();
                return true;
            });
            // Tap anywhere to skip.
            video.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startLauncher();
                    return true;
                }
                return false;
            });
        } catch (Throwable t) {
            startLauncher();
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::startLauncher, INTRO_TIMEOUT_MS);
    }

    private void startLauncher() {
        if (mLauncherStarted) return;
        mLauncherStarted = true;
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
