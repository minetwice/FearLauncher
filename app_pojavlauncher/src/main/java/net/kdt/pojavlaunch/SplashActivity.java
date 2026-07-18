package net.kdt.pojavlaunch;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Instant start requested: bypass/remove intro animation
        startLauncher();
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
