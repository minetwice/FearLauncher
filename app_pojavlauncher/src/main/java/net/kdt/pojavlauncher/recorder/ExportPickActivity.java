package net.kdt.pojavlauncher.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Transparent trampoline that opens the system "save as" picker
 * (any folder, any name) and hands the chosen uri back through a
 * static listener. Lets the export board ask for a destination
 * without owning the activity result.
 */
public class ExportPickActivity extends Activity {

    public interface Listener {
        void onExportTargetPicked(@Nullable Uri uri);
    }

    private static final int REQUEST_SAVE_AS = 91;
    private static volatile Listener sListener;

    public static void pick(Context context, String suggestedName, Listener listener) {
        sListener = listener;
        Intent intent = new Intent(context, ExportPickActivity.class);
        intent.putExtra("name", suggestedName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = "fear_rec_export.mp4";
        if (getIntent() != null && getIntent().getStringExtra("name") != null) {
            name = getIntent().getStringExtra("name");
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQUEST_SAVE_AS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_AS) {
            Listener listener = sListener;
            sListener = null;
            if (listener != null) {
                listener.onExportTargetPicked(resultCode == RESULT_OK && data != null ? data.getData() : null);
            }
        }
        finish();
    }
}
