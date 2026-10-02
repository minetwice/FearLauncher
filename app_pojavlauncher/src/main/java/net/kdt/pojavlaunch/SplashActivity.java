package net.kdt.pojavlaunch;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

/**
 * FearLauncher intro.
 *
 * The logo is the animated artwork: it plays on its own (the red spikes move)
 * and we dress it with a reveal - the mark drops in oversized and settles with
 * a small overshoot, a glow breathes behind it and a shine sweeps across it.
 *
 * All of the real startup work (storage check, preferences, asset unpacking) is
 * kicked off before the intro starts, so the animation costs no extra boot time.
 */
public class SplashActivity extends AppCompatActivity {
    /** How long the intro is on screen before we hand over to the launcher. */
    private static final long INTRO_DURATION = 3000L;

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

    /** Swap the static fallback for the animated artwork, if we can decode it. */
    private void loadAnimatedLogo(ImageView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(getResources(), R.raw.fear_logo_anim);
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            view.setImageDrawable(drawable);
            if (drawable instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) drawable).setRepeatCount(
                        AnimatedImageDrawable.REPEAT_INFINITE);
                ((AnimatedImageDrawable) drawable).start();
            }
        } catch (Throwable t) {
            // Any decode problem: the static PNG the layout already set stays.
        }
    }

    /** The reveal itself. Everything is best-effort: a missing view is skipped. */
    private void playIntro() {
        final ImageView logo = findViewById(R.id.splash_logo);
        final View glow = findViewById(R.id.glow_aura);
        final View shine = findViewById(R.id.shine_line);
        final View logoShine = findViewById(R.id.logo_shine);
        if (logo == null) return;

        loadAnimatedLogo(logo);

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
            glowPulse.setDuration(1400L);
            glowPulse.setInterpolator(new DecelerateInterpolator());
        }

        AnimatorSet intro = new AnimatorSet();
        intro.playTogether(logoReveal, glowPulse);
        intro.start();

        // A light sweep across the whole screen.
        if (shine != null) {
            ObjectAnimator sweep = ObjectAnimator.ofFloat(shine, View.TRANSLATION_X, -900f, 1700f);
            sweep.setDuration(1600L);
            sweep.setStartDelay(250L);
            sweep.setInterpolator(new AccelerateDecelerateInterpolator());
            sweep.start();
        }

        // The full shine that runs across the logo itself, twice.
        playLogoShine(logoShine, logo);
    }

    private void playLogoShine(final View logoShine, final ImageView logo) {
        if (logoShine == null) return;
        logoShine.post(() -> {
            float width = logo.getWidth();
            if (width <= 0f) width = 640f;
            logoShine.setAlpha(0.9f);
            ObjectAnimator pass = ObjectAnimator.ofFloat(
                    logoShine, View.TRANSLATION_X, -220f, width + 220f);
            pass.setDuration(1200L);
            pass.setStartDelay(650L);
            pass.setInterpolator(new AccelerateDecelerateInterpolator());
            pass.start();
        });
    }

    private void startLauncher() {
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
