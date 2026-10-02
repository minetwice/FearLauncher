package net.kdt.pojavlaunch;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

/**
 * FearLauncher intro.
 *
 * Plays a short logo reveal - the way the Minecraft logo is revealed: the mark
 * drops in oversized and settles with a small overshoot, a glow pulses behind
 * it, the wordmark fades up underneath and a light sweeps across the screen.
 *
 * All of the real startup work (storage check, preferences, asset unpacking) is
 * kicked off before the intro starts, so the animation does not cost any extra
 * boot time.
 */
public class SplashActivity extends AppCompatActivity {
    /** How long the intro is on screen before we hand over to the launcher. */
    private static final long INTRO_DURATION = 2200L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do the real startup work first - it runs while the intro plays.
        if (!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            finish();
            return;
        }
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        setContentView(R.layout.activity_splash);
        playIntro();
        new Handler(Looper.getMainLooper()).postDelayed(this::startLauncher, INTRO_DURATION);
    }

    /** The reveal itself. Everything is best-effort: a missing view is skipped. */
    private void playIntro() {
        final View logo = findViewById(R.id.splash_logo);
        final View wordmark = findViewById(R.id.splash_text);
        final View glow = findViewById(R.id.glow_aura);
        final View shine = findViewById(R.id.shine_line);
        if (logo == null) {
            // Layout changed underneath us - just hand over immediately.
            return;
        }

        // The mark: oversized and transparent -> full size, with a small
        // overshoot, exactly like the Minecraft logo dropping into place.
        logo.setAlpha(0f);
        logo.setScaleX(1.7f);
        logo.setScaleY(1.7f);
        AnimatorSet logoReveal = new AnimatorSet();
        logoReveal.playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 1.7f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1.7f, 1f));
        logoReveal.setDuration(900L);
        logoReveal.setInterpolator(new OvershootInterpolator(1.4f));

        // A soft glow that breathes behind the mark.
        AnimatorSet glowPulse = new AnimatorSet();
        if (glow != null) {
            glow.setAlpha(0f);
            glowPulse.playTogether(
                    ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 0.55f, 0.25f),
                    ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.7f, 1.15f),
                    ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.7f, 1.15f));
            glowPulse.setDuration(1200L);
            glowPulse.setInterpolator(new DecelerateInterpolator());
        }

        // The wordmark rises in under the mark.
        AnimatorSet wordmarkReveal = new AnimatorSet();
        if (wordmark != null) {
            wordmark.setAlpha(0f);
            wordmark.setTranslationY(26f);
            wordmarkReveal.playTogether(
                    ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(wordmark, View.TRANSLATION_Y, 26f, 0f));
            wordmarkReveal.setStartDelay(520L);
            wordmarkReveal.setDuration(700L);
            wordmarkReveal.setInterpolator(new DecelerateInterpolator());
        }

        AnimatorSet intro = new AnimatorSet();
        intro.playTogether(logoReveal, glowPulse, wordmarkReveal);
        intro.start();

        // A light sweep across the screen.
        if (shine != null) {
            ObjectAnimator sweep = ObjectAnimator.ofFloat(shine, View.TRANSLATION_X, -900f, 1700f);
            sweep.setDuration(1500L);
            sweep.setStartDelay(250L);
            sweep.setInterpolator(new AccelerateDecelerateInterpolator());
            sweep.start();
        }
    }

    private void startLauncher() {
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
