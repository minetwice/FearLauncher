package net.kdt.pojavlaunch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

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
        final ImageView particles = findViewById(R.id.splash_particles);
        final View shine = findViewById(R.id.shine_line);

        // 1. Particle Round Animation & Pulse
        particles.animate()
                .alpha(1f)
                .rotation(360f)
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // 2. Blast & Logo Show
                    particles.animate().scaleX(5f).scaleY(5f).alpha(0f).setDuration(300).start();
                    logo.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(500)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                // 3. Shine Line Effect
                                shine.animate()
                                        .translationX(root.getWidth() + 500f)
                                        .setDuration(800)
                                        .withEndAction(() -> {
                                            // 4. Blur/Fade out and Start Launcher
                                            root.animate().alpha(0f).setDuration(500).withEndAction(this::startLauncher).start();
                                        }).start();
                            }).start();
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
