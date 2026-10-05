package net.kdt.pojavlaunch.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Point;
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
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fearlauncher.fear.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Records the screen (and, optionally, the microphone and the device's own audio) to an
 * MP4 file, running as a foreground service so the capture survives the game being in the
 * foreground and the launcher being backgrounded.
 *
 * The pipeline is the standard low-overhead one: MediaProjection feeds a VirtualDisplay
 * whose surface IS the H.264 encoder's input surface, so no frame is ever copied through
 * the CPU. Audio comes from one or two AudioRecords (microphone and/or playback capture)
 * that are mixed into a single AAC track. Video and audio are drained on one thread into
 * a MediaMuxer, which keeps the file's interleaving correct.
 *
 * The microphone is opened as VOICE_COMMUNICATION so the platform's echo canceller and
 * noise suppressor can engage.
 */
public class ScreenRecorderService extends Service {

    private static final String TAG = "ScreenRecorder";

    public static final String ACTION_START = "net.kdt.pojavlaunch.recorder.START";
    public static final String ACTION_STOP = "net.kdt.pojavlaunch.recorder.STOP";
    public static final String ACTION_PAUSE = "net.kdt.pojavlaunch.recorder.PAUSE";
    public static final String ACTION_RESUME = "net.kdt.pojavlaunch.recorder.RESUME";
    public static final String ACTION_TOGGLE_MIC = "net.kdt.pojavlaunch.recorder.TOGGLE_MIC";
    public static final String ACTION_TOGGLE_INTERNAL = "net.kdt.pojavlaunch.recorder.TOGGLE_INTERNAL";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    /** Broadcast so the overlay and the Dashboard can reflect the live state. */
    public static final String ACTION_STATE = "net.kdt.pojavlaunch.recorder.STATE";
    public static final String EXTRA_STATE = "state";
    public static final int STATE_RECORDING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_STOPPED = 3;

    private static final String CHANNEL_ID = "fear_recorder";
    private static final int NOTIFICATION_ID = 0x5FEA;

    private static final String MIME_VIDEO = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MIME_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS = 2;
    private static final int AUDIO_BITRATE = 128_000;

    private MediaProjection mProjection;
    private VirtualDisplay mVirtualDisplay;

    /**
     * Android 14 (API 34) and up refuse createVirtualDisplay() unless the projection has
     * a callback registered first - it throws IllegalStateException, the capture never
     * starts, and nothing is written to the file. It also tells us when the user revokes
     * the projection from the system UI, so the recording can finish cleanly.
     */
    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            if (mRunning) stopCapture();
        }
    };
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaMuxer mMuxer;
    private MediaCodec.BufferInfo mVideoInfo;
    private MediaCodec.BufferInfo mAudioInfo;

    private int mWidth, mHeight, mDpi, mFps, mVideoBitrate;
    private boolean mMicEnabled, mInternalEnabled;

    private int mVideoTrack = -1;
    private int mAudioTrack = -1;
    private boolean mMuxerStarted;
    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;
    private boolean mVideoFormatReady;
    private boolean mAudioFormatReady;
    private int mSamplesWritten;
    private long mStartNanos;
    private volatile boolean mPaused;
    private volatile boolean mRunning;
    private volatile boolean mStopRequested;

    private Thread mVideoDrainThread;
    private Thread mAudioThread;
    private AudioRecord mMicRecord;
    private AudioRecord mInternalRecord;
    private File mOutputFile;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case ACTION_START:
                startCapture(intent);
                break;
            case ACTION_STOP:
                stopCapture();
                break;
            case ACTION_PAUSE:
                mPaused = true;
                broadcastState(STATE_PAUSED);
                break;
            case ACTION_RESUME:
                mPaused = false;
                broadcastState(STATE_RECORDING);
                break;
            case ACTION_TOGGLE_MIC:
                mMicEnabled = !mMicEnabled;
                RecordingSettings.setMicEnabled(this, mMicEnabled);
                applyAudioSourceChanges();
                broadcastState(mPaused ? STATE_PAUSED : STATE_RECORDING);
                break;
            case ACTION_TOGGLE_INTERNAL:
                mInternalEnabled = !mInternalEnabled;
                RecordingSettings.setInternalAudioEnabled(this, mInternalEnabled);
                applyAudioSourceChanges();
                broadcastState(mPaused ? STATE_PAUSED : STATE_RECORDING);
                break;
        }
        return START_NOT_STICKY;
    }

    // ---- start / stop -------------------------------------------------------

    private void startCapture(Intent intent) {
        if (mRunning) return;

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultData == null) {
            Log.w(TAG, "No projection data; refusing to start");
            stopSelf();
            return;
        }

        RecordingSettings.Config config = RecordingSettings.load(this);
        mFps = config.fps;
        mVideoBitrate = config.videoBitrate;
        mMicEnabled = config.mic;
        mInternalEnabled = config.internalAudio;

        resolveSize(config.quality);

        // The foreground notification must exist before the projection is taken on API 29+,
        // otherwise the platform kills the capture the moment the app leaves the foreground.
        startForegroundCompat();

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            stopSelf();
            return;
        }
        mProjection = manager.getMediaProjection(resultCode, resultData);
        if (mProjection == null) {
            Log.w(TAG, "getMediaProjection returned null");
            stopSelf();
            return;
        }

        try {
            mOutputFile = newOutputFile();
            mMuxerStarted = false;
            mVideoFormatReady = false;
            mAudioFormatReady = false;
            mVideoTrack = -1;
            mAudioTrack = -1;
            mSamplesWritten = 0;
            prepareEncoders();
            prepareMuxer();
            // The clock has to exist before the audio thread can stamp a chunk, or its
            // first timestamp is nonsense and the muxer writes a broken timeline.
            mStartNanos = System.nanoTime();
            mStopRequested = false;
            mRunning = true;
            // Encoders first: the drain thread treats a dequeue on a stopped encoder as
            // "finished", so it must not be started before they are running.
            prepareVirtualDisplay();
            startDrainThread();
            startAudio();

            broadcastState(STATE_RECORDING);
            Log.i(TAG, "Recording to " + mOutputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Could not start recording", e);
            showError("Recording could not start: " + e.getClass().getSimpleName());
            releaseAll();
            stopSelf();
        }
    }

    /** Surfaces a failure to the person holding the phone, not just to logcat. */
    private void showError(final String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                android.widget.Toast.makeText(getApplicationContext(), message,
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception ignored) { }
        });
    }

    private void stopCapture() {
        if (!mRunning) {
            stopSelf();
            return;
        }
        mStopRequested = true;
        // Flush instead of tearing down. Stopping the encoders right here discards every
        // frame they still hold, and a short clip can lose all of them - which is exactly
        // what leaves a file that reports 0:00. Signalling end-of-stream and letting the
        // drain thread run to completion gives the muxer a whole file; the drain thread
        // then releases everything and stops the service.
        if (mVideoEncoder != null) {
            try {
                mVideoEncoder.signalEndOfInputStream();
            } catch (Exception ignored) { }
        }
        mRunning = false;   // the audio loop leaves and queues its own end-of-stream
    }

    /** Picks an output size that keeps the device aspect ratio at the chosen quality. */
    private void resolveSize(int quality) {
        Point size = new Point();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealSize(size);
        int screenW = size.x > 0 ? size.x : 1280;
        int screenH = size.y > 0 ? size.y : 720;

        int shortSide = Math.min(screenW, screenH);
        int longSide = Math.max(screenW, screenH);
        // Quality is the SHORT side; scale the long side to match the panel's ratio.
        int targetShort = quality;
        int targetLong = Math.round(longSide * (targetShort / (float) shortSide));

        // Encoders want even dimensions.
        if (screenW >= screenH) {
            mWidth = even(targetLong);
            mHeight = even(targetShort);
        } else {
            mWidth = even(targetShort);
            mHeight = even(targetLong);
        }
        mDpi = getResources().getDisplayMetrics().densityDpi;
    }

    private static int even(int v) {
        return (v / 2) * 2;
    }

    private File newOutputFile() {
        File dir = new File(getExternalFilesDir(null), "recordings");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(dir, "FEAR_" + stamp + ".mp4");
    }

    // ---- encoders -----------------------------------------------------------

    private void prepareEncoders() throws Exception {
        MediaFormat video = MediaFormat.createVideoFormat(MIME_VIDEO, mWidth, mHeight);
        video.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        video.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        video.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
        video.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        video.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

        mVideoEncoder = MediaCodec.createEncoderByType(MIME_VIDEO);
        mVideoEncoder.configure(video, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoInfo = new MediaCodec.BufferInfo();

        MediaFormat audio = MediaFormat.createAudioFormat(MIME_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNELS);
        audio.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audio.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        audio.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        mAudioEncoder = MediaCodec.createEncoderByType(MIME_AUDIO);
        mAudioEncoder.configure(audio, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioInfo = new MediaCodec.BufferInfo();
    }

    private void prepareMuxer() throws Exception {
        mMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private void prepareVirtualDisplay() {
        android.view.Surface surface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();
        mAudioEncoder.start();

        mProjection.registerCallback(mProjectionCallback,
                new android.os.Handler(android.os.Looper.getMainLooper()));

        mVirtualDisplay = mProjection.createVirtualDisplay(
                "fear-recorder", mWidth, mHeight, mDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null);
        if (mVirtualDisplay == null) {
            throw new IllegalStateException("Virtual display was refused");
        }
    }

    // ---- audio --------------------------------------------------------------

    private void startAudio() {
        mAudioThread = new Thread(this::audioLoop, "fear-recorder-audio");
        mAudioThread.start();
    }

    /**
     * Opens whichever sources are enabled. Called at start and again whenever a source is
     * toggled, so the switch in the overlay takes effect without ending the recording.
     */
    private void applyAudioSourceChanges() {
        synchronized (this) {
            boolean wantMic = mMicEnabled && hasAudioPermission();
            if (wantMic && mMicRecord == null) mMicRecord = openMic();
            if (!wantMic && mMicRecord != null) {
                releaseRecord(mMicRecord);
                mMicRecord = null;
            }

            boolean wantInternal = mInternalEnabled && Build.VERSION.SDK_INT >= 29;
            if (wantInternal && mInternalRecord == null) mInternalRecord = openInternal();
            if (!wantInternal && mInternalRecord != null) {
                releaseRecord(mInternalRecord);
                mInternalRecord = null;
            }
        }
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private AudioRecord openMic() {
        try {
            int min = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(min, AUDIO_SAMPLE_RATE);
            AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return null;
            }
            record.startRecording();
            return record;
        } catch (Exception e) {
            Log.w(TAG, "Microphone unavailable", e);
            return null;
        }
    }

    private AudioRecord openInternal() {
        try {
            // Match every usage the API allows. Each app picks its own usage - a voice
            // chat and an in-app chime are not the same stream - so the wider the net the
            // likelier the app we care about falls inside it. Whether an app may be
            // captured at all is still that app's own choice.
            AudioPlaybackCaptureConfiguration capture =
                    new AudioPlaybackCaptureConfiguration.Builder(mProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .addMatchingUsage(AudioAttributes.USAGE_ALARM)
                            .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build();
            int min = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(min, AUDIO_SAMPLE_RATE);
            AudioRecord record = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build())
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setAudioPlaybackCaptureConfig(capture)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return null;
            }
            record.startRecording();
            return record;
        } catch (Exception e) {
            // Expected on apps that forbid capture, and on devices without the API.
            Log.w(TAG, "Internal audio capture unavailable", e);
            return null;
        }
    }

    private void releaseRecord(AudioRecord record) {
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
        } catch (Exception ignored) { }
        try {
            record.release();
        } catch (Exception ignored) { }
    }

    /**
     * Reads the enabled sources, averages them into one PCM buffer and feeds the AAC
     * encoder. Averaging (rather than summing) keeps the mix from clipping when both the
     * game and the microphone are loud at once.
     */
    private void audioLoop() {
        final int framesPerChunk = 1024;
        short[] micBuf = new short[framesPerChunk * AUDIO_CHANNELS];
        short[] intBuf = new short[framesPerChunk * AUDIO_CHANNELS];
        short[] mixed = new short[framesPerChunk * AUDIO_CHANNELS];

        while (mRunning) {
            AudioRecord mic;
            AudioRecord internal;
            synchronized (this) {
                mic = mMicRecord;
                internal = mInternalRecord;
            }

            if (mic == null && internal == null) {
                // Keep the AAC encoder alive with silence when both sources are off, so it
                // still emits its output format and the muxer can start. "No audio" should
                // be a silent track, not a missing one.
                if (!mPaused) {
                    feedAudio(new short[framesPerChunk * AUDIO_CHANNELS],
                            framesPerChunk * AUDIO_CHANNELS);
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            int micRead = mic != null ? mic.read(micBuf, 0, micBuf.length) : 0;
            int intRead = internal != null ? internal.read(intBuf, 0, intBuf.length) : 0;
            if (micRead <= 0 && intRead <= 0) {
                if (!mPaused) {
                    feedAudio(new short[framesPerChunk * AUDIO_CHANNELS],
                            framesPerChunk * AUDIO_CHANNELS);
                }
                continue;
            }

            int count = Math.max(micRead, intRead);
            for (int i = 0; i < count; i++) {
                int sum = 0;
                int sources = 0;
                if (i < micRead) {
                    sum += micBuf[i];
                    sources++;
                }
                if (i < intRead) {
                    sum += intBuf[i];
                    sources++;
                }
                mixed[i] = (short) (sources == 0 ? 0 : sum / sources);
            }

            if (mPaused) continue;
            feedAudio(mixed, count);
        }
        // End the audio track so the drain thread can finish the file.
        queueAudioEos();
    }

    /** Queues the audio encoder's end-of-stream marker once capture has stopped. */
    private void queueAudioEos() {
        try {
            int index = mAudioEncoder.dequeueInputBuffer(200_000);
            if (index < 0) return;
            long pts = Math.max(0L, (System.nanoTime() - mStartNanos) / 1000L);
            mAudioEncoder.queueInputBuffer(index, 0, 0, pts,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } catch (Exception ignored) { }
    }

    private void feedAudio(short[] pcm, int samples) {
        try {
            int index = mAudioEncoder.dequeueInputBuffer(10_000);
            if (index < 0) return;
            ByteBuffer buffer = mAudioEncoder.getInputBuffer(index);
            if (buffer == null) return;
            buffer.clear();
            int shorts = Math.min(samples, buffer.capacity() / 2);
            // asShortBuffer() views the same memory, so this writes straight into the
            // encoder's input buffer without an intermediate copy.
            buffer.asShortBuffer().put(pcm, 0, shorts);
            int bytes = shorts * 2;
            long pts = (System.nanoTime() - mStartNanos) / 1000L;
            mAudioEncoder.queueInputBuffer(index, 0, bytes, pts, 0);
        } catch (Exception e) {
            Log.w(TAG, "Dropped an audio chunk", e);
        }
    }

    // ---- drain --------------------------------------------------------------

    private void startDrainThread() {
        mVideoDrainThread = new Thread(this::drainLoop, "fear-recorder-drain");
        mVideoDrainThread.start();
    }

    /**
     * Single writer for the muxer: pulls whatever either encoder has produced and writes
     * it. Keeping both tracks on one thread is what keeps the MP4 interleaving valid.
     */
    private void drainLoop() {
        boolean videoDone = false;
        boolean audioDone = false;

        while (!videoDone || !audioDone) {
            if (!videoDone) videoDone = drainVideo();
            if (!audioDone) audioDone = drainAudio();
            if (videoDone && audioDone) break;
        }
        finishMuxer();
        // Teardown belongs to this thread: nothing may be released while samples are
        // still being written, or the tail of the file is lost.
        if (mStopRequested) teardownAfterStop();
    }

    private boolean drainVideo() {
        try {
            int index = mVideoEncoder.dequeueOutputBuffer(mVideoInfo, 10_000);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mVideoFormat = mVideoEncoder.getOutputFormat();
                mVideoFormatReady = true;
                tryStartMuxer();
                return false;
            }
            if (index < 0) return false;
            writeSample(mVideoEncoder, index, mVideoInfo, true);
            return (mVideoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private boolean drainAudio() {
        try {
            int index = mAudioEncoder.dequeueOutputBuffer(mAudioInfo, 10_000);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mAudioFormat = mAudioEncoder.getOutputFormat();
                mAudioFormatReady = true;
                tryStartMuxer();
                return false;
            }
            if (index < 0) return false;
            writeSample(mAudioEncoder, index, mAudioInfo, false);
            return (mAudioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * A track may only be added before the muxer starts, and both formats arrive
     * asynchronously, so the start waits until video and audio have each reported theirs.
     */
    private void tryStartMuxer() {
        if (mMuxerStarted || mMuxer == null) return;
        if (!mVideoFormatReady || !mAudioFormatReady) return;
        mVideoTrack = mMuxer.addTrack(mVideoFormat);
        mAudioTrack = mMuxer.addTrack(mAudioFormat);
        mMuxer.start();
        mMuxerStarted = true;
    }

    private void writeSample(MediaCodec codec, int index, MediaCodec.BufferInfo info, boolean video) {
        try {
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                info.size = 0;
            }
            if (info.size > 0 && mMuxerStarted) {
                ByteBuffer buffer = codec.getOutputBuffer(index);
                if (buffer != null) {
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    int track = video ? mVideoTrack : mAudioTrack;
                    if (track >= 0) {
                        mMuxer.writeSampleData(track, buffer, info);
                        mSamplesWritten++;
                    }
                }
            }
            codec.releaseOutputBuffer(index, false);
        } catch (Exception e) {
            Log.w(TAG, "Could not write a sample", e);
        }
    }

    private void finishMuxer() {
        boolean hadSamples = mSamplesWritten > 0;
        try {
            if (mMuxer != null && mMuxerStarted) mMuxer.stop();
        } catch (Exception e) {
            Log.w(TAG, "Muxer stop failed", e);
        }
        try {
            if (mMuxer != null) mMuxer.release();
        } catch (Exception ignored) { }
        mMuxer = null;
        // A muxer that never started, or that wrote nothing, leaves an unplayable stub
        // behind. Delete it rather than list a 0:00 clip that cannot be opened.
        if ((!mMuxerStarted || !hadSamples) && mOutputFile != null && mOutputFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mOutputFile.delete();
        }
    }

    // ---- lifecycle ----------------------------------------------------------

    /** Frees the capture resources. Never called from the drain thread itself. */
    private void releaseResources() {
        try {
            synchronized (this) {
                if (mMicRecord != null) {
                    releaseRecord(mMicRecord);
                    mMicRecord = null;
                }
                if (mInternalRecord != null) {
                    releaseRecord(mInternalRecord);
                    mInternalRecord = null;
                }
            }
        } catch (Exception ignored) { }

        try {
            if (mVirtualDisplay != null) mVirtualDisplay.release();
        } catch (Exception ignored) { }
        mVirtualDisplay = null;

        try {
            if (mProjection != null) mProjection.stop();
        } catch (Exception ignored) { }
        mProjection = null;

        try {
            if (mVideoEncoder != null) {
                try { mVideoEncoder.stop(); } catch (Exception ignored) { }
                mVideoEncoder.release();
            }
        } catch (Exception ignored) { }
        mVideoEncoder = null;

        try {
            if (mAudioEncoder != null) {
                try { mAudioEncoder.stop(); } catch (Exception ignored) { }
                mAudioEncoder.release();
            }
        } catch (Exception ignored) { }
        mAudioEncoder = null;

    }

    /** Runs on the drain thread once the file is closed: tear down, then stop. */
    private void teardownAfterStop() {
        if (mAudioThread != null && Thread.currentThread() != mAudioThread) {
            try {
                mAudioThread.join(1500);
            } catch (InterruptedException ignored) { }
        }
        releaseResources();
        broadcastState(STATE_STOPPED);
        stopForeground(true);
        stopSelf();
    }

    /** Full teardown, used when the service is destroyed while still recording. */
    private void releaseAll() {
        releaseResources();
        if (mVideoDrainThread != null && Thread.currentThread() != mVideoDrainThread) {
            try {
                mVideoDrainThread.join(3000);
            } catch (InterruptedException ignored) { }
        }
        mVideoDrainThread = null;
        if (mAudioThread != null && Thread.currentThread() != mAudioThread) {
            try {
                mAudioThread.join(2000);
            } catch (InterruptedException ignored) { }
        }
        mAudioThread = null;
        finishMuxer();
    }

    @Override
    public void onDestroy() {
        if (mRunning) {
            mRunning = false;
            releaseAll();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- notification / state ----------------------------------------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.recorder_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, net.kdt.pojavlaunch.LauncherActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ScreenRecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.recorder_notification_title))
                .setContentText(getString(R.string.recorder_notification_text))
                .setSmallIcon(R.drawable.ic_app_logo)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(0, getString(R.string.recorder_stop), stopPending)
                .build();
    }

    private void broadcastState(int state) {
        Intent intent = new Intent(ACTION_STATE).setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra("mic", mMicEnabled);
        intent.putExtra("internal", mInternalEnabled);
        sendBroadcast(intent);
    }

    /** Convenience for callers that have a Context. */
    public static void start(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ScreenRecorderService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, ScreenRecorderService.class).setAction(ACTION_STOP));
    }
}
