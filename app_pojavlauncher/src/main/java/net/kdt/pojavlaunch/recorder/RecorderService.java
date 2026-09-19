package net.kdt.pojavlauncher.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import git.artdeell.mojo.R;

import java.io.File;
import java.io.IOException;

public class RecorderService extends Service {
    private static final String TAG = "RecorderService";
    private static final String CHANNEL_ID = "RecorderServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private String outputPath;
    private boolean isRecording = false;
    private boolean isPaused = false;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            
            if (resultCode == 0 && data != null) {
                startRecording(resultCode, data);
            } else if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case "PAUSE":
                        pauseRecording();
                        break;
                    case "RESUME":
                        resumeRecording();
                        break;
                    case "STOP":
                        stopRecording();
                        break;
                }
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Recorder Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startRecording(int resultCode, Intent data) {
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            mediaRecorder = new MediaRecorder();

            // Create FearLauncher directory if it doesn't exist
            File fearLauncherDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "FearLauncher"
            );
            if (!fearLauncherDir.exists()) {
                fearLauncherDir.mkdirs();
            }

            // Set up MediaRecorder
            outputPath = fearLauncherDir.getAbsolutePath() + "/recording_" + System.currentTimeMillis() + ".mp4";

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setOutputFile(outputPath);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoEncodingBitRate(5_000_000); // 5 Mbps

            // Set up VirtualDisplay
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int dpi = metrics.densityDpi;

            mediaRecorder.prepare();
            mediaRecorder.start();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "RecorderDisplay",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(),
                null,
                null
            );

            isRecording = true;
            isPaused = false;

            // Start foreground service
            startForeground(NOTIFICATION_ID, createNotification("Recording..."));
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            stopSelf();
        }
    }

    private void pauseRecording() {
        if (isRecording && !isPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.pause();
            }
            isPaused = true;
            updateNotification("Recording Paused");
            Toast.makeText(this, "Recording paused", Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeRecording() {
        if (isRecording && isPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.resume();
            }
            isPaused = false;
            updateNotification("Recording...");
            Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                Log.e(TAG, "Error stopping recorder: " + e.getMessage());
            }
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }

            isRecording = false;
            isPaused = false;

            stopForeground(true);
            Toast.makeText(this, "Recording saved: " + outputPath, Toast.LENGTH_LONG).show();

            // Notify the dashboard to refresh
            Intent refreshIntent = new Intent("REFRESH_DASHBOARD");
            sendBroadcast(refreshIntent);
        }
        stopSelf();
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FearLauncher Recorder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(text));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            stopRecording();
        }
    }
}
