package net.kdt.pojavlauncher.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Transparent trampoline activity that:
 *  1. asks for the RECORD_AUDIO runtime permission (if mic is wanted),
 *  2. shows the MediaProjection consent dialog,
 *  3. starts RecorderService and finishes.
 * Using an activity keeps the fragment/result plumbing out of the
 * launcher UI and lets recording be started from anywhere.
 */
public class RecorderPermissionActivity extends Activity {

    private static final int REQUEST_MIC = 71;
    private static final int REQUEST_PROJECTION = 72;

    private boolean mWantMic;
    private boolean mWantDevice;
    private int mWidth = 1280;
    private int mHeight = 720;
    private int mFps = 30;
    private int mBitrate = 16_000_000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        if (in != null) {
            mWantMic = in.getBooleanExtra("mic", true);
            mWantDevice = in.getBooleanExtra("device", true);
            mWidth = in.getIntExtra("width", 1280);
            mHeight = in.getIntExtra("height", 720);
            mFps = in.getIntExtra("fps", 30);
            mBitrate = in.getIntExtra("bitrate", 16_000_000);
        }

        if (mWantMic
                && android.os.Build.VERSION.SDK_INT >= 23
                && !hasMicPermission()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        askProjection();
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC) {
            // continue even if denied — recording falls back to device audio only
            askProjection();
        }
    }

    private void askProjection() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            finish();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, RecorderService.class);
                service.setAction(RecorderService.ACTION_START);
                service.putExtra("resultCode", resultCode);
                service.putExtra("data", data);
                service.putExtra("mic", mWantMic && hasMicPermission());
                service.putExtra("device", mWantDevice);
                service.putExtra("width", mWidth);
                service.putExtra("height", mHeight);
                service.putExtra("fps", mFps);
                service.putExtra("bitrate", mBitrate);
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
            }
            finish();
        }
    }
}
