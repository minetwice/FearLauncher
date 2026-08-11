package net.kdt.pojavlaunch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final View root = findViewById(R.id.splash_root);
        final ImageView logo = findViewById(R.id.splash_logo);
        final TextView fearText = findViewById(R.id.splash_text);
        final View shine = findViewById(R.id.shine_line);
        final ImageView aura = findViewById(R.id.glow_aura);
        final View reflection = findViewById(R.id.glass_reflection_bg);
        final View flash = findViewById(R.id.ability_flash);

        // 3-Second Glow & Shine Sequence [Point 3]
        logo.setRotation(-10f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(1200).setInterpolator(new AnticipateInterpolator()).start();
        aura.animate().alpha(0.4f).scaleX(2.0f).scaleY(2.0f).setDuration(2000).start();

        fearText.setTranslationY(30f);
        fearText.animate().alpha(1f).translationY(0f).setDuration(1200).setStartDelay(500).start();

        // Anime Flash Ability Effect
        flash.animate().alpha(0.8f).setDuration(100).setStartDelay(2000).withEndAction(() -> {
            flash.animate().alpha(0f).setDuration(600).start();
        }).start();

        // Continuous Background Reflection
        reflection.animate().alpha(0.4f).setDuration(1500).setUpdateListener(animation -> {
            reflection.setRotation((float)animation.getAnimatedValue() * 5f);
        }).start();

        // 3s Shine Glow Animation
        shine.animate()
                .translationX(root.getWidth() + 1000f)
                .setDuration(3000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    root.animate().alpha(0f).setDuration(500).withEndAction(this::startLauncher).start();
                }).start();
    }

    private void startLauncher() {
        if (!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            finish();
            return;
        }
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        Intent intent = new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
