package net.kdt.pojavlaunch.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
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

import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;

import java.io.File;

public class RecorderService extends Service {
    private static final String TAG = "RecorderService";
    private static final String CHANNEL_ID = "RecorderServiceChannel";
    private static final int NOTIFICATION_ID = 888;

    public static final String ACTION_START = "net.kdt.pojavlaunch.recorder.START";
    public static final String ACTION_PAUSE = "net.kdt.pojavlaunch.recorder.PAUSE";
    public static final String ACTION_RESUME = "net.kdt.pojavlaunch.recorder.RESUME";
    public static final String ACTION_STOP = "net.kdt.pojavlaunch.recorder.STOP";

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_DATA = "extra_data";
    public static final String EXTRA_FPS = "extra_fps";
    public static final String EXTRA_BITRATE = "extra_bitrate";
    public static final String EXTRA_ENABLE_AUDIO = "extra_enable_audio";
    public static final String EXTRA_NOISE_REDUCTION = "extra_noise_reduction";

    private static boolean sIsRecording = false;
    private static boolean sIsPaused = false;
    private static String sCurrentVideoPath = null;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;
    private RecorderOverlayView mOverlayView;

    public static boolean isRecording() {
        return sIsRecording;
    }

    public static boolean isPaused() {
        return sIsPaused;
    }

    public static String getCurrentVideoPath() {
        return sCurrentVideoPath;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_START:
                    int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
                    Intent data = intent.getParcelableExtra(EXTRA_DATA);
                    int fps = intent.getIntExtra(EXTRA_FPS, 60);
                    int bitrate = intent.getIntExtra(EXTRA_BITRATE, 10_000_000);
                    boolean enableAudio = intent.getBooleanExtra(EXTRA_ENABLE_AUDIO, true);
                    boolean noiseReduction = intent.getBooleanExtra(EXTRA_NOISE_REDUCTION, true);
                    if (resultCode != -1 && data != null) {
                        startRecording(resultCode, data, fps, bitrate, enableAudio, noiseReduction);
                    }
                    break;
                case ACTION_PAUSE:
                    pauseRecording();
                    break;
                case ACTION_RESUME:
                    resumeRecording();
                    break;
                case ACTION_STOP:
                    stopRecording();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FearLauncher Screen Recorder",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active screen recording controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startRecording(int resultCode, Intent data, int fps, int bitrate, boolean enableAudio, boolean noiseReduction) {
        try {
            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            if (mMediaProjection == null) {
                Toast.makeText(this, "Failed to obtain MediaProjection permission", Toast.LENGTH_SHORT).show();
                stopSelf();
                return;
            }

            File moviesDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "FearLauncher"
            );
            if (!moviesDir.exists()) {
                moviesDir.mkdirs();
            }

            sCurrentVideoPath = new File(moviesDir, "FearRec_" + System.currentTimeMillis() + ".mp4").getAbsolutePath();

            mMediaRecorder = new MediaRecorder();
            if (enableAudio) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (enableAudio) {
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setAudioEncodingBitRate(128000);
                mMediaRecorder.setAudioSamplingRate(44100);
            }
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setOutputFile(sCurrentVideoPath);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            // Align dimensions to 16 for H.264 encoder compatibility
            width = (width / 16) * 16;
            height = (height / 16) * 16;

            mMediaRecorder.setVideoSize(width, height);
            mMediaRecorder.setVideoFrameRate(fps);
            mMediaRecorder.setVideoEncodingBitRate(bitrate);

            mMediaRecorder.prepare();
            mMediaRecorder.start();

            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "FearRecorderDisplay",
                    width,
                    height,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mMediaRecorder.getSurface(),
                    null,
                    null
            );

            if (enableAudio && noiseReduction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        Log.d(TAG, "NoiseSuppressor is available on this device.");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize NoiseSuppressor: " + e.getMessage());
                }
            }

            sIsRecording = true;
            sIsPaused = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification("Screen Recording Active"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Screen Recording Active"));
            }
            showOverlayControls();

            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error starting recorder: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            cleanup();
            stopSelf();
        }
    }

    public void pauseRecording() {
        if (sIsRecording && !sIsPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mMediaRecorder != null) {
                try {
                    mMediaRecorder.pause();
                    sIsPaused = true;
                    updateNotification("Recording Paused");
                    if (mOverlayView != null) {
                        mOverlayView.onRecordingPaused();
                    }
                    Toast.makeText(this, "Recording paused", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error pausing recording: " + e.getMessage());
                }
            }
        }
    }

    public void resumeRecording() {
        if (sIsRecording && sIsPaused) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mMediaRecorder != null) {
                try {
                    mMediaRecorder.resume();
                    sIsPaused = false;
                    updateNotification("Recording Active");
                    if (mOverlayView != null) {
                        mOverlayView.onRecordingResumed();
                    }
                    Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error resuming recording: " + e.getMessage());
                }
            }
        }
    }

    public void stopRecording() {
        if (sIsRecording) {
            try {
                if (mMediaRecorder != null) {
                    mMediaRecorder.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder: " + e.getMessage());
            }

            cleanup();
            sIsRecording = false;
            sIsPaused = false;

            removeOverlayControls();
            stopForeground(true);

            Toast.makeText(this, "Recording saved: " + sCurrentVideoPath, Toast.LENGTH_LONG).show();

            Intent intent = new Intent("net.kdt.pojavlaunch.recorder.REFRESH_DASHBOARD");
            sendBroadcast(intent);
        }
        stopSelf();
    }

    private void showOverlayControls() {
        try {
            if (mOverlayView == null) {
                mOverlayView = new RecorderOverlayView(this, this);
            }
            mOverlayView.show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay controls: " + e.getMessage());
        }
    }

    private void removeOverlayControls() {
        if (mOverlayView != null) {
            mOverlayView.hide();
            mOverlayView = null;
        }
    }

    private void cleanup() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private Notification buildNotification(String statusText) {
        Intent notificationIntent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FearLauncher Recorder")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (sIsRecording) {
            stopRecording();
        }
        removeOverlayControls();
        super.onDestroy();
    }
}
