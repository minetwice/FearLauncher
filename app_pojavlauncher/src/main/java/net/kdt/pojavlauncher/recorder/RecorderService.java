package net.kdt.pojavlauncher.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import git.artdeell.mojo.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FearLauncher built-in screen recorder.
 * - Video: MediaProjection -> VirtualDisplay -> MediaCodec(H.264) -> MediaMuxer
 * - Audio: internal (playback capture, API 29+) + microphone, mixed live into a
 *   single AAC track. Both can be toggled while recording.
 * - Pause/resume keeps video and audio timelines aligned (wall-clock PTS with gaps).
 */
public class RecorderService extends Service {

    public static final String ACTION_START = "net.kdt.pojavlauncher.recorder.action.START";
    public static final String ACTION_PAUSE = "net.kdt.pojavlauncher.recorder.action.PAUSE";
    public static final String ACTION_RESUME = "net.kdt.pojavlauncher.recorder.action.RESUME";
    public static final String ACTION_STOP = "net.kdt.pojavlauncher.recorder.action.STOP";
    public static final String ACTION_MIC_ON = "net.kdt.pojavlauncher.recorder.action.MIC_ON";
    public static final String ACTION_MIC_OFF = "net.kdt.pojavlauncher.recorder.action.MIC_OFF";
    public static final String ACTION_DEVICE_ON = "net.kdt.pojavlauncher.recorder.action.DEVICE_ON";
    public static final String ACTION_DEVICE_OFF = "net.kdt.pojavlauncher.recorder.action.DEVICE_OFF";

    public static final String ACTION_STATE_CHANGED = "net.kdt.pojavlauncher.recorder.STATE_CHANGED";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MIC = "mic";
    public static final String EXTRA_DEVICE = "device";

    public static final int STATE_IDLE = 0;
    public static final int STATE_RECORDING = 1;
    public static final int STATE_PAUSED = 2;

    private static final String TAG = "FearRecorder";
    private static final String CHANNEL_ID = "fear_recorder_channel";
    private static final int NOTIFICATION_ID = 4210;

    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BITRATE = 192000;

    // ------- shared static state (same process as the dashboard) -------
    private static volatile int sState = STATE_IDLE;
    private static volatile boolean sMicEnabled = true;
    private static volatile boolean sDeviceAudioEnabled = true;
    private static volatile long sStartElapsed;
    private static volatile long sPausedTotalMs;
    private static volatile long sPauseStart;

    public static int getState() { return sState; }
    public static boolean isMicEnabledStatic() { return sMicEnabled; }
    public static boolean isDeviceAudioEnabledStatic() { return sDeviceAudioEnabled; }

    public static long getRecordedMs() {
        if (sState == STATE_IDLE) return 0;
        long now = SystemClock.elapsedRealtime();
        long paused = sPausedTotalMs + (sState == STATE_PAUSED ? (now - sPauseStart) : 0L);
        long total = now - sStartElapsed;
        return Math.max(0, total - paused);
    }

    // ------- instance state -------
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private VirtualDisplay mVirtualDisplay;
    private Surface mVideoInputSurface;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaMuxer mMuxer;
    private ParcelFileDescriptor mMuxerFd;
    private String mOutputPath;      // legacy (< API 29) file path
    private Uri mOutputUri;          // MediaStore uri (API 29+)
    private AudioRecord mInternalAudio;
    private AudioRecord mMicAudio;
    private Thread mAudioThread;
    private Thread mVideoThread;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final Object mMuxerLock = new Object();
    private volatile boolean mMuxerStarted;
    private volatile int mVideoTrack = -1;
    private volatile int mAudioTrack = -1;
    private volatile long mStartNanos;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private int mVideoWidth = 1280;
    private int mVideoHeight = 720;
    private int mVideoFps = 30;
    private int mVideoBitrate = 16_000_000;

    @Override
    public void onCreate() {
        super.onCreate();
        mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        switch (action) {
            case ACTION_START:
                int resultCode = intent.getIntExtra("resultCode", -1);
                android.content.Intent data = null;
                if (Build.VERSION.SDK_INT >= 33) {
                    data = intent.getParcelableExtra("data", android.content.Intent.class);
                } else {
                    data = intent.getParcelableExtra("data");
                }
                if (data == null) {
                    Toast.makeText(this, "Recorder: missing projection result", Toast.LENGTH_SHORT).show();
                    stopSelf();
                    return START_NOT_STICKY;
                }
                mMicWanted = intent.getBooleanExtra("mic", true);
                mDeviceWanted = intent.getBooleanExtra("device", true)
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
                mVideoWidth = intent.getIntExtra("width", 1280);
                mVideoHeight = intent.getIntExtra("height", 720);
                mVideoFps = intent.getIntExtra("fps", 30);
                mVideoBitrate = intent.getIntExtra("bitrate", 16_000_000);
                startRecording(resultCode, data);
                return START_STICKY;
            case ACTION_PAUSE:
                pauseRecording();
                break;
            case ACTION_RESUME:
                resumeRecording();
                break;
            case ACTION_STOP:
                stopRecording();
                break;
            case ACTION_MIC_ON:
                setMic(true);
                break;
            case ACTION_MIC_OFF:
                setMic(false);
                break;
            case ACTION_DEVICE_ON:
                setDeviceAudio(true);
                break;
            case ACTION_DEVICE_OFF:
                setDeviceAudio(false);
                break;
        }
        return START_NOT_STICKY;
    }

    private volatile boolean mMicWanted = true;
    private volatile boolean mDeviceWanted = true;

    // ------------------------------------------------------------------
    // START
    // ------------------------------------------------------------------
    private void startRecording(int resultCode, Intent data) {
        if (sState != STATE_IDLE) return;
        try {
            startForegroundCompat();
            mProjection = mProjectionManager.getMediaProjection(resultCode, data);
            if (mProjection == null) {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
                stopSelf();
                return;
            }
            mProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopRecording();
                }
            }, mMainHandler);

            // ---- video encoder ----
            MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mVideoWidth, mVideoHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoFps);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mVideoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoInputSurface = mVideoEncoder.createInputSurface();
            mVideoEncoder.start();

            DisplayMetrics dm = getResources().getDisplayMetrics();
            mVirtualDisplay = mProjection.createVirtualDisplay(
                    "FearRecorder", mVideoWidth, mVideoHeight, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mVideoInputSurface, null, mMainHandler);

            // ---- audio encoder ----
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.start();

            // ---- audio sources ----
            sMicEnabled = mMicWanted;
            sDeviceAudioEnabled = mDeviceWanted;
            if (sDeviceAudioEnabled) mInternalAudio = openInternalAudio(mProjection);
            if (mInternalAudio == null) sDeviceAudioEnabled = false;
            if (sMicEnabled) {
                mMicAudio = openMic();
                if (mMicAudio == null) sMicEnabled = false;
            }

            // ---- output / muxer ----
            openOutput();

            mStartNanos = System.nanoTime();
            sStartElapsed = SystemClock.elapsedRealtime();
            sPausedTotalMs = 0;
            mRunning.set(true);

            mVideoThread = new Thread(this::videoDrainLoop, "FearRec-Video");
            mVideoThread.start();
            mAudioThread = new Thread(this::audioLoop, "FearRec-Audio");
            mAudioThread.start();

            sState = STATE_RECORDING;
            FloatingRecorderUI.attach(this);
            updateNotification();
            broadcastState();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Recorder failed to start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            releaseAll();
            stopSelf();
        }
    }

    @Nullable
    private AudioRecord openInternalAudio(MediaProjection projection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) minBuf = 8192;
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            AudioRecord record = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(minBuf * 2, 16384))
                    .setAudioPlaybackCaptureConfig(config)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) return null;
            record.startRecording();
            return record;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Internal audio capture unavailable", e);
            return null;
        }
    }

    @Nullable
    private AudioRecord openMic() {
        try {
            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) minBuf = 4096;
            AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) return null;
            record.startRecording();
            return record;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Mic unavailable", e);
            return null;
        }
    }

    private void openOutput() throws Exception {
        String fileName = "fear_rec_" + System.currentTimeMillis() + ".mp4";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FearLauncher");
            mOutputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (mOutputUri == null) throw new Exception("MediaStore insert failed");
            mMuxerFd = getContentResolver().openFileDescriptor(mOutputUri, "rw");
            if (mMuxerFd == null) throw new Exception("Cannot open output file");
            mMuxer = new MediaMuxer(mMuxerFd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } else {
            File dir = new File(getExternalFilesDir(null), "Recordings");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            mOutputPath = file.getAbsolutePath();
            mMuxer = new MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
    }

    // ------------------------------------------------------------------
    // PAUSE / RESUME / TOGGLES
    // ------------------------------------------------------------------
    private void pauseRecording() {
        if (sState != STATE_RECORDING) return;
        sState = STATE_PAUSED;
        sPauseStart = SystemClock.elapsedRealtime();
        try {
            if (mVirtualDisplay != null) mVirtualDisplay.setSurface(null);
        } catch (Exception ignored) {
        }
        updateNotification();
        FloatingRecorderUI.notifyState(this);
        broadcastState();
    }

    private void resumeRecording() {
        if (sState != STATE_PAUSED) return;
        sPausedTotalMs += SystemClock.elapsedRealtime() - sPauseStart;
        sState = STATE_RECORDING;
        try {
            if (mVirtualDisplay != null && mVideoInputSurface != null)
                mVirtualDisplay.setSurface(mVideoInputSurface);
        } catch (Exception ignored) {
        }
        updateNotification();
        FloatingRecorderUI.notifyState(this);
        broadcastState();
    }

    private void setMic(boolean on) {
        if (sState == STATE_IDLE) {
            sMicEnabled = on;
            broadcastState();
            return;
        }
        if (on && mMicAudio == null) {
            mMicAudio = openMic();
            if (mMicAudio == null) {
                Toast.makeText(this, "Microphone not available", Toast.LENGTH_SHORT).show();
                return;
            }
            sMicEnabled = true;
        } else if (!on && mMicAudio != null) {
            try {
                mMicAudio.stop();
                mMicAudio.release();
            } catch (Exception ignored) {
            }
            mMicAudio = null;
            sMicEnabled = false;
        } else {
            sMicEnabled = on;
        }
        updateNotification();
        FloatingRecorderUI.notifyState(this);
        broadcastState();
    }

    private void setDeviceAudio(boolean on) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Internal audio needs Android 10+", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sState == STATE_IDLE) {
            sDeviceAudioEnabled = on;
            broadcastState();
            return;
        }
        if (on && mInternalAudio == null) {
            mInternalAudio = openInternalAudio(mProjection);
            if (mInternalAudio == null) {
                Toast.makeText(this, "Device audio not available", Toast.LENGTH_SHORT).show();
                return;
            }
            sDeviceAudioEnabled = true;
        } else if (!on && mInternalAudio != null) {
            try {
                mInternalAudio.stop();
                mInternalAudio.release();
            } catch (Exception ignored) {
            }
            mInternalAudio = null;
            sDeviceAudioEnabled = false;
        } else {
            sDeviceAudioEnabled = on;
        }
        updateNotification();
        FloatingRecorderUI.notifyState(this);
        broadcastState();
    }

    // ------------------------------------------------------------------
    // ENCODING LOOPS
    // ------------------------------------------------------------------
    private void videoDrainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (mRunning.get()) {
            try {
                int index = mVideoEncoder.dequeueOutputBuffer(info, 10_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (mMuxerLock) {
                        mVideoTrack = mMuxer.addTrack(mVideoEncoder.getOutputFormat());
                        maybeStartMuxer();
                    }
                } else if (index >= 0) {
                    synchronized (mMuxerLock) {
                        if (mMuxerStarted && info.size > 0) {
                            ByteBuffer out = mVideoEncoder.getOutputBuffer(index);
                            if (out != null) {
                                out.position(info.offset);
                                out.limit(info.offset + info.size);
                                mMuxer.writeSampleData(mVideoTrack, out, info);
                            }
                        }
                    }
                    mVideoEncoder.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Video drain error", e);
                break;
            }
        }
    }

    private static final int FRAME_SAMPLES = 1024; // per channel
    private final short[] mMixBuf = new short[FRAME_SAMPLES * 2];
    private final short[] mInternalBuf = new short[FRAME_SAMPLES * 2];
    private final short[] mMicBuf = new short[FRAME_SAMPLES];
    private final byte[] mBytes = new byte[FRAME_SAMPLES * 4];

    private void audioLoop() {
        while (mRunning.get()) {
            try {
                if (sState == STATE_PAUSED) {
                    Thread.sleep(40);
                    continue;
                }
                java.util.Arrays.fill(mMixBuf, (short) 0);
                boolean hasAudio = false;

                AudioRecord internal = mInternalAudio;
                if (internal != null && sDeviceAudioEnabled) {
                    int n = internal.read(mInternalBuf, 0, mInternalBuf.length);
                    if (n > 0) {
                        for (int i = 0; i < FRAME_SAMPLES * 2; i++) {
                            if (i < n) mMixBuf[i] = mInternalBuf[i];
                        }
                        hasAudio = true;
                    }
                }

                AudioRecord mic = mMicAudio;
                if (mic != null && sMicEnabled) {
                    int n = mic.read(mMicBuf, 0, FRAME_SAMPLES);
                    if (n > 0) {
                        for (int i = 0; i < FRAME_SAMPLES; i++) {
                            short m = (n > i) ? mMicBuf[i] : (short) 0;
                            // gentle mic level so commentary sits over game audio
                            short scaled = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, m * 1.4f));
                            mMixBuf[i * 2] = clamp(mMixBuf[i * 2] + scaled);
                            mMixBuf[i * 2 + 1] = clamp(mMixBuf[i * 2 + 1] + scaled);
                        }
                        hasAudio = true;
                    }
                }

                if (!hasAudio) {
                    // keep the AAC track fed with silence so A/V stays in sync
                    Thread.sleep(5);
                }

                ByteBuffer buffer = ByteBuffer.wrap(mBytes);
                for (int i = 0; i < mMixBuf.length; i++) {
                    buffer.putShort(mMixBuf[i]);
                }
                long ptsUs = (System.nanoTime() - mStartNanos) / 1000L;
                feedAudio(mBytes, mBytes.length, ptsUs);
                drainAudio();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                android.util.Log.e(TAG, "Audio loop error", e);
            }
        }
        // flush + EOS
        drainAudio();
    }

    private static short clamp(int v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) v;
    }

    private void feedAudio(byte[] data, int length, long ptsUs) {
        try {
            int index = mAudioEncoder.dequeueInputBuffer(10_000);
            if (index >= 0) {
                ByteBuffer in = mAudioEncoder.getInputBuffer(index);
                if (in != null) {
                    in.clear();
                    in.put(data, 0, length);
                    mAudioEncoder.queueInputBuffer(index, 0, length, ptsUs, 0);
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Feed audio error", e);
        }
    }

    private void drainAudio() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (true) {
                int index = mAudioEncoder.dequeueOutputBuffer(info, 0);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (mMuxerLock) {
                        mAudioTrack = mMuxer.addTrack(mAudioEncoder.getOutputFormat());
                        maybeStartMuxer();
                    }
                } else if (index >= 0) {
                    synchronized (mMuxerLock) {
                        if (mMuxerStarted && info.size > 0) {
                            ByteBuffer out = mAudioEncoder.getOutputBuffer(index);
                            if (out != null) {
                                out.position(info.offset);
                                out.limit(info.offset + info.size);
                                mMuxer.writeSampleData(mAudioTrack, out, info);
                            }
                        }
                    }
                    mAudioEncoder.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
                } else {
                    return;
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Drain audio error", e);
        }
    }

    private void maybeStartMuxer() {
        if (!mMuxerStarted && mVideoTrack >= 0 && mAudioTrack >= 0) {
            mMuxer.start();
            mMuxerStarted = true;
        }
    }

    // ------------------------------------------------------------------
    // STOP
    // ------------------------------------------------------------------
    public void stopRecording() {
        if (sState == STATE_IDLE) return;
        sState = STATE_IDLE;
        mRunning.set(false);
        broadcastState();
        FloatingRecorderUI.detach();

        try {
            if (mAudioThread != null) {
                mAudioThread.interrupt();
                mAudioThread.join(800);
            }
        } catch (InterruptedException ignored) {
        }
        try {
            if (mAudioEncoder != null) mAudioEncoder.signalEndOfInputStream();
        } catch (Exception ignored) {
        }
        try {
            if (mVideoEncoder != null) mVideoEncoder.signalEndOfInputStream();
        } catch (Exception ignored) {
        }
        try {
            if (mVideoThread != null) mVideoThread.join(1500);
        } catch (InterruptedException ignored) {
        }

        boolean ok = true;
        try {
            synchronized (mMuxerLock) {
                if (mMuxerStarted && mMuxer != null) {
                    mMuxer.stop();
                }
            }
        } catch (Exception e) {
            ok = false;
            android.util.Log.e(TAG, "Muxer stop failed", e);
        }

        String where;
        if (mOutputUri != null) {
            where = "Movies/FearLauncher";
            if (!ok) {
                try {
                    getContentResolver().delete(mOutputUri, null, null);
                } catch (Exception ignored) {
                }
            }
        } else {
            where = mOutputPath != null ? mOutputPath : "";
            if (ok && mOutputPath != null) {
                android.media.MediaScannerConnection.scanFile(this,
                        new String[]{mOutputPath}, new String[]{"video/mp4"}, null);
            }
        }

        releaseAll();

        if (ok) {
            Toast.makeText(this, "Recording saved: " + where, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Recording failed while saving", Toast.LENGTH_LONG).show();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseAll() {
        try { if (mInternalAudio != null) mInternalAudio.release(); } catch (Exception ignored) {}
        try { if (mMicAudio != null) mMicAudio.release(); } catch (Exception ignored) {}
        mInternalAudio = null;
        mMicAudio = null;
        try { if (mVideoEncoder != null) mVideoEncoder.stop(); } catch (Exception ignored) {}
        try { if (mVideoEncoder != null) mVideoEncoder.release(); } catch (Exception ignored) {}
        try { if (mAudioEncoder != null) mAudioEncoder.stop(); } catch (Exception ignored) {}
        try { if (mAudioEncoder != null) mAudioEncoder.release(); } catch (Exception ignored) {}
        mVideoEncoder = null;
        mAudioEncoder = null;
        try { if (mVirtualDisplay != null) mVirtualDisplay.release(); } catch (Exception ignored) {}
        mVirtualDisplay = null;
        try { if (mProjection != null) mProjection.stop(); } catch (Exception ignored) {}
        mProjection = null;
        try { if (mMuxer != null) mMuxer.release(); } catch (Exception ignored) {}
        mMuxer = null;
        try { if (mMuxerFd != null) mMuxerFd.close(); } catch (Exception ignored) {}
        mMuxerFd = null;
        if (mVideoInputSurface != null) {
            try { mVideoInputSurface.release(); } catch (Exception ignored) {}
            mVideoInputSurface = null;
        }
        mMuxerStarted = false;
        mVideoTrack = -1;
        mAudioTrack = -1;
        sState = STATE_IDLE;
    }

    // ------------------------------------------------------------------
    // NOTIFICATION
    // ------------------------------------------------------------------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Fear Screen Recorder", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Screen recording status and controls");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        boolean paused = (sState == STATE_PAUSED);
        String text = paused ? "Paused — tap resume to continue"
                : (sMicEnabled && sDeviceAudioEnabled ? "Recording screen + game audio + mic"
                : sMicEnabled ? "Recording screen + microphone"
                : sDeviceAudioEnabled ? "Recording screen + game audio"
                : "Recording screen");

        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rec_video)
                .setContentTitle(paused ? "Fear Recorder (paused)" : "Fear Recorder — recording")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        builder.addAction(new NotificationCompat.Action(0,
                paused ? "Resume" : "Pause",
                servicePendingIntent(paused ? ACTION_RESUME : ACTION_PAUSE, 1)));
        builder.addAction(new NotificationCompat.Action(0,
                sMicEnabled ? "Mic off" : "Mic on",
                servicePendingIntent(sMicEnabled ? ACTION_MIC_OFF : ACTION_MIC_ON, 2)));
        builder.addAction(new NotificationCompat.Action(0,
                "Stop",
                servicePendingIntent(ACTION_STOP, 3)));
        if (open != null) {
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            builder.setContentIntent(PendingIntent.getActivity(this, 0, open,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }
        return builder.build();
    }

    private PendingIntent servicePendingIntent(String action, int code) {
        Intent intent = new Intent(this, RecorderService.class).setAction(action);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getService(this, code, intent, flags);
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, sState);
        intent.putExtra(EXTRA_MIC, sMicEnabled);
        intent.putExtra(EXTRA_DEVICE, sDeviceAudioEnabled);
        sendBroadcast(intent);
    }
}
