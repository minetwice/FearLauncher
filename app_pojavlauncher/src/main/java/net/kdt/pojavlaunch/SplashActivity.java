package net.kdt.pojavlaunch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
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
        final ImageView flame1 = findViewById(R.id.flame_1);
        final ImageView flame2 = findViewById(R.id.flame_2);
        final View shine = findViewById(R.id.shine_line);

        // Flame Animation Sequence (Rengoku Style)
        flame1.animate().alpha(0.6f).scaleX(1.2f).scaleY(1.2f).rotation(180).setDuration(600).start();
        flame2.animate().alpha(0.8f).scaleX(1.1f).scaleY(1.1f).rotation(-180).setDuration(800).withEndAction(() -> {

            // Flame Burst
            flame1.animate().scaleX(4f).scaleY(4f).alpha(0f).setDuration(400).setInterpolator(new AnticipateInterpolator()).start();
            flame2.animate().scaleX(3.5f).scaleY(3.5f).alpha(0f).setDuration(500).setInterpolator(new AnticipateInterpolator()).start();

            // Logo Reveal
            logo.animate()
                    .alpha(1f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(400)
                    .withEndAction(() -> {
                        logo.animate().scaleX(1f).scaleY(1f).setDuration(200).start();

                        // Shine Sweep
                        shine.animate()
                                .translationX(root.getWidth() + 600f)
                                .setDuration(700)
                                .withEndAction(() -> {
                                    root.animate().alpha(0f).setDuration(400).withEndAction(this::startLauncher).start();
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
