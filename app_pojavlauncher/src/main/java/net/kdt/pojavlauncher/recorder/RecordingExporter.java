package net.kdt.pojavlauncher.recorder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.ParcelFileDescriptor;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Export / re-master engine for recordings.
 *
 * Video: decodes the source, re-draws every frame through a GPU scaler
 * into an H.264 encoder at the chosen resolution (up to 4K) and a
 * constant frame rate (smooths out laggy VFR screen recordings).
 *
 * Audio: reads the separate device / mic tracks written by RecorderService,
 * optionally mutes either source, applies noise reduction (high-pass +
 * noise gate) and voice boost (gain), then mixes into one AAC track.
 */
public class RecordingExporter {

    public static class Options {
        public int targetWidth;     // 0 = keep source resolution
        public int targetHeight;
        public int targetFps;       // 0 = keep source timing (VFR passthrough)
        public boolean muteInternal;
        public boolean muteMic;
        public boolean noiseReduction;
        public boolean voiceBoost;
    }

    public interface Listener {
        void onProgress(int percent, String stage);

        void onDone();

        void onError(String message);
    }

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int FRAME_SAMPLES = 1024;
    private static final int SAMPLE_RATE = 44100;
    private static final long CHUNK_US = Math.round(FRAME_SAMPLES * 1_000_000L / (double) SAMPLE_RATE);

    private volatile boolean mCancelled = false;
    private final Listener mListener;

    // ---- EGL / GL ----
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private int mProgram;
    private int mPosLoc;
    private int mTexLoc;
    private int mMatrixLoc;
    private int mTextureId;
    private android.graphics.SurfaceTexture mSurfaceTexture;
    private final float[] mTransform = new float[16];
    private FloatBuffer mVertexCoords;
    private FloatBuffer mTexCoords;
    private final Object mFrameSync = new Object();
    private boolean mFrameAvailable = false;

    // ---- codecs ----
    private MediaCodec mVideoDecoder;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAacEncoder;
    private MediaMuxer mMuxer;
    private int mVideoMuxTrack = -1;
    private int mAudioMuxTrack = -1;
    private boolean mMuxerStarted = false;
    private final MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
    private boolean mAacEos = false;
    private boolean mAacEosQueued = false;

    private static class Sample {
        final byte[] data;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        Sample(byte[] data, int size, long pts, int flags) {
            this.data = data;
            info.set(0, size, pts, flags);
        }
    }

    private final List<Sample> mPendingVideo = new ArrayList<>();
    private final List<Sample> mPendingAudio = new ArrayList<>();

    public RecordingExporter(Listener listener) {
        mListener = listener;
    }

    public void cancel() {
        mCancelled = true;
    }

    public void export(Context context, RecordingStore.Entry input, Options options,
                       ParcelFileDescriptor outFd) {
        try {
            doExport(context, input, options, outFd);
            if (mCancelled) {
                mListener.onError("Export cancelled");
            } else {
                mListener.onDone();
            }
        } catch (Exception e) {
            mListener.onError(e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            try {
                if (outFd != null) outFd.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void doExport(Context context, RecordingStore.Entry input, Options options,
                           ParcelFileDescriptor outFd) throws Exception {
        mMuxer = new MediaMuxer(outFd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // ---------------- video source ----------------
        MediaExtractor videoExtractor = new MediaExtractor();
        RecordingStore.setDataSource(context, videoExtractor, input);
        int videoTrackIndex = -1;
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            MediaFormat f = videoExtractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                videoTrackIndex = i;
                break;
            }
        }
        if (videoTrackIndex < 0) throw new Exception("No video track found");
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        videoExtractor.selectTrack(videoTrackIndex);

        int srcW = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
        int srcH = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int srcFps = 30;
        try {
            if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                srcFps = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            }
        } catch (Exception ignored) {
        }
        long durationUs = 1_000_000L;
        try {
            if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION);
            }
        } catch (Exception ignored) {
        }
        if (durationUs < 1_000_000L) durationUs = 1_000_000L;

        int targetW, targetH;
        if (options.targetWidth > 0 && options.targetHeight > 0) {
            double scale = Math.min(options.targetWidth / (double) srcW,
                    options.targetHeight / (double) srcH);
            targetW = even((int) Math.round(srcW * scale));
            targetH = even((int) Math.round(srcH * scale));
        } else {
            targetW = even(srcW);
            targetH = even(srcH);
        }
        int targetFps = options.targetFps > 0 ? options.targetFps : Math.max(15, srcFps);
        long videoBitrate = (long) targetW * targetH * targetFps / 4;
        if (videoBitrate < 8_000_000L) videoBitrate = 8_000_000L;
        if (videoBitrate > 60_000_000L) videoBitrate = 60_000_000L;

        // ---------------- video encoder + GL scaler ----------------
        MediaFormat encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH);
        encFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) videoBitrate);
        encFormat.setInteger(MediaFormat.KEY_FRAME_RATE, targetFps);
        encFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mVideoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mVideoEncoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface encoderSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();

        eglSetup(encoderSurface);
        glSetup(targetW, targetH);

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mTextureId = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        mSurfaceTexture = new android.graphics.SurfaceTexture(mTextureId);
        mSurfaceTexture.setDefaultBufferSize(srcW, srcH);
        // two-arg variant: the single-arg one needs a Looper on this thread —
        // the export thread has none, which crashed every export at 0 bytes.
        mSurfaceTexture.setOnFrameAvailableListener(st -> {
            synchronized (mFrameSync) {
                mFrameAvailable = true;
                mFrameSync.notifyAll();
            }
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
        mDecoderSurface = new Surface(mSurfaceTexture);

        mVideoDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
        mVideoDecoder.configure(videoFormat, mDecoderSurface, null, 0);
        mVideoDecoder.start();

        // ---------------- audio ----------------
        // Track roles: mono = mic; stereo: 1st = mix, 2nd = device-only. Legacy single track = device.
        AudioPcmReader internalReader = null;
        AudioPcmReader micReader = null;
        {
            MediaEnumerator enumerator = new MediaEnumerator(context, input);
            int internalIdx = enumerator.firstStereoAfterMix;
            int micIdx = enumerator.monoTrack;
            if (internalIdx < 0 && micIdx < 0) internalIdx = enumerator.anyAudio;
            if (!options.muteInternal && internalIdx >= 0) {
                internalReader = new AudioPcmReader(context, input, internalIdx,
                        options.noiseReduction, options.voiceBoost);
            }
            if (!options.muteMic && micIdx >= 0) {
                micReader = new AudioPcmReader(context, input, micIdx,
                        options.noiseReduction, options.voiceBoost);
            }
        }

        if (internalReader != null || micReader != null) {
            mAacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mAacEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAacEncoder.start();
        }

        // ---------------- main transcode loop ----------------
        if (mListener != null) mListener.onProgress(0, "Preparing…");
        final long frameDurUs = 1_000_000L / targetFps;
        long nextTickUs = 0;
        long audioPtsUs = 0;
        boolean inputEos = false;
        boolean videoDone = false;
        boolean audioDone = (mAacEncoder == null);
        int starveGuard = 0;

        final short[] intPcm = new short[FRAME_SAMPLES * 2];
        final short[] micPcm = new short[FRAME_SAMPLES * 2];
        final short[] mixPcm = new short[FRAME_SAMPLES * 2];
        final byte[] pcmBytes = new byte[FRAME_SAMPLES * 4];

        while ((!videoDone || !audioDone) && !mCancelled) {
            // ---- video pump: emit ticks covered by the audio clock ----
            if (!videoDone) {
                boolean handled = false;
                while (!handled && !mCancelled) {
                    if (!inputEos) {
                        int inIdx = mVideoDecoder.dequeueInputBuffer(10_000);
                        if (inIdx >= 0) {
                            ByteBuffer ib = mVideoDecoder.getInputBuffer(inIdx);
                            int size = videoExtractor.readSampleData(ib, 0);
                            if (size < 0) {
                                mVideoDecoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEos = true;
                            } else {
                                mVideoDecoder.queueInputBuffer(inIdx, 0, size, videoExtractor.getSampleTime(), 0);
                                videoExtractor.advance();
                            }
                        }
                    }
                    int outIdx = mVideoDecoder.dequeueOutputBuffer(mInfo, 10_000L);
                    if (outIdx >= 0) {
                        if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            mVideoDecoder.releaseOutputBuffer(outIdx, false);
                            while (nextTickUs <= durationUs && !mCancelled) {
                                drawFrame(nextTickUs);
                                nextTickUs += frameDurUs;
                                drainVideoCodec(false);
                            }
                            videoDone = true;
                            handled = true;
                            break;
                        }
                        if (mInfo.size > 0) {
                            mVideoDecoder.releaseOutputBuffer(outIdx, true);
                            if (awaitFrame(600)) {
                                long pts = mInfo.presentationTimeUs;
                                while (nextTickUs <= pts && nextTickUs <= durationUs && !mCancelled) {
                                    drawFrame(nextTickUs);
                                    nextTickUs += frameDurUs;
                                    drainVideoCodec(false);
                                }
                            }
                        } else {
                            mVideoDecoder.releaseOutputBuffer(outIdx, false);
                        }
                        handled = true;
                        starveGuard = 0;
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // decoder format — ignore
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        handled = true; // yield to audio
                        if (++starveGuard > 100000) { // decoder dead — bail out
                            videoDone = true;
                        }
                    }
                }
                drainVideoCodec(false);
            }

            // ---- audio: one 1024-sample chunk per round ----
            if (!audioDone && !mCancelled) {
                boolean intEos = (internalReader == null);
                boolean micEos = (micReader == null);
                if (internalReader != null) intEos = !internalReader.readStereo(intPcm, FRAME_SAMPLES);
                if (micReader != null) micEos = !micReader.readStereo(micPcm, FRAME_SAMPLES);
                for (int i = 0; i < FRAME_SAMPLES; i++) {
                    mixPcm[i * 2] = clip16(intPcm[i * 2] + micPcm[i * 2]);
                    mixPcm[i * 2 + 1] = clip16(intPcm[i * 2 + 1] + micPcm[i * 2 + 1]);
                }
                ByteBuffer bb = ByteBuffer.wrap(pcmBytes);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < mixPcm.length; i++) bb.putShort(mixPcm[i]);
                feedAac(pcmBytes, audioPtsUs);
                audioPtsUs += CHUNK_US;
                if (intEos && micEos && !mAacEosQueued) {
                    mAacEosQueued = queueAacEos();
                }
                drainAac(false);
                audioDone = mAacEos;
            }

            String stage;
            if (videoDone) stage = "Finalizing audio…";
            else if (targetW != srcW || targetH != srcH) stage = "Upscaling video to " + targetH + "p · " + targetFps + " fps…";
            else if (options.targetFps > 0) stage = "Smoothing to " + targetFps + " fps…";
            else stage = "Re-encoding video…";
            int percent = (int) (Math.min(nextTickUs, durationUs) * 100L / durationUs);
            if (mListener != null) mListener.onProgress(Math.min(99, percent), stage);
        }

        if (!mCancelled) {
            try {
                mVideoEncoder.signalEndOfInputStream();
            } catch (Exception ignored) {
            }
            drainVideoCodec(true);
            if (mAacEncoder != null && !mAacEosQueued) {
                queueAacEos();
            }
            drainAac(true);
        }
        if (mMuxerStarted) {
            mMuxer.stop();
        }

        // ---------------- cleanup ----------------
        if (internalReader != null) internalReader.release();
        if (micReader != null) micReader.release();
        try { videoExtractor.release(); } catch (Exception ignored) { }
        try { if (mVideoDecoder != null) mVideoDecoder.stop(); } catch (Exception ignored) { }
        try { if (mVideoDecoder != null) mVideoDecoder.release(); } catch (Exception ignored) { }
        try { if (mVideoEncoder != null) mVideoEncoder.stop(); } catch (Exception ignored) { }
        try { if (mVideoEncoder != null) mVideoEncoder.release(); } catch (Exception ignored) { }
        try { if (mAacEncoder != null) mAacEncoder.stop(); } catch (Exception ignored) { }
        try { if (mAacEncoder != null) mAacEncoder.release(); } catch (Exception ignored) { }
        try { if (mMuxer != null) mMuxer.release(); } catch (Exception ignored) { }
        try { if (mSurfaceTexture != null) mSurfaceTexture.release(); } catch (Exception ignored) { }
        try { if (mDecoderSurface != null) mDecoderSurface.release(); } catch (Exception ignored) { }
        eglTeardown();
    }

    private Surface mDecoderSurface;

    // ------------------------------------------------------------------
    // AUDIO
    // ------------------------------------------------------------------
    private void feedAac(byte[] data, long ptsUs) {
        try {
            int idx = mAacEncoder.dequeueInputBuffer(10_000);
            if (idx >= 0) {
                ByteBuffer in = mAacEncoder.getInputBuffer(idx);
                if (in != null) {
                    in.clear();
                    in.put(data);
                    mAacEncoder.queueInputBuffer(idx, 0, data.length, ptsUs, 0);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("FearExport", "feedAac", e);
        }
    }

    private boolean queueAacEos() {
        try {
            int idx = mAacEncoder.dequeueInputBuffer(10_000);
            if (idx >= 0) {
                mAacEncoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return true;
            }
        } catch (Exception ignored) {
            return true;
        }
        return false;
    }

    private void drainAac(boolean block) {
        if (mAacEncoder == null) return;
        while (!mCancelled) {
            int idx = mAacEncoder.dequeueOutputBuffer(mInfo, block ? 10_000L : 0L);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mAudioMuxTrack = mMuxer.addTrack(mAacEncoder.getOutputFormat());
                checkMuxerStart();
                continue;
            }
            if (idx >= 0) {
                if (mInfo.size > 0) {
                    ByteBuffer ob = mAacEncoder.getOutputBuffer(idx);
                    if (ob != null) {
                        byte[] data = new byte[mInfo.size];
                        ob.position(mInfo.offset);
                        ob.get(data);
                        Sample s = new Sample(data, mInfo.size, mInfo.presentationTimeUs, mInfo.flags);
                        if (mMuxerStarted) {
                            mMuxer.writeSampleData(mAudioMuxTrack, ByteBuffer.wrap(s.data), s.info);
                        } else {
                            mPendingAudio.add(s);
                        }
                    }
                }
                boolean eos = (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                mAacEncoder.releaseOutputBuffer(idx, false);
                if (eos) {
                    mAacEos = true;
                    return;
                }
            }
        }
    }

    private void drainVideoCodec(boolean block) {
        while (!mCancelled || block) {
            int idx = mVideoEncoder.dequeueOutputBuffer(mInfo, block ? 10_000L : 0L);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mVideoMuxTrack = mMuxer.addTrack(mVideoEncoder.getOutputFormat());
                checkMuxerStart();
                continue;
            }
            if (idx >= 0) {
                if (mInfo.size > 0) {
                    ByteBuffer ob = mVideoEncoder.getOutputBuffer(idx);
                    if (ob != null) {
                        byte[] data = new byte[mInfo.size];
                        ob.position(mInfo.offset);
                        ob.get(data);
                        Sample s = new Sample(data, mInfo.size, mInfo.presentationTimeUs, mInfo.flags);
                        if (mMuxerStarted) {
                            mMuxer.writeSampleData(mVideoMuxTrack, ByteBuffer.wrap(s.data), s.info);
                        } else {
                            mPendingVideo.add(s);
                        }
                    }
                }
                boolean eos = (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                mVideoEncoder.releaseOutputBuffer(idx, false);
                if (eos) return;
            }
        }
    }

    private void checkMuxerStart() {
        if (mMuxerStarted) return;
        boolean audioReady = (mAacEncoder == null) || mAudioMuxTrack >= 0;
        if (mVideoMuxTrack >= 0 && audioReady) {
            mMuxer.start();
            mMuxerStarted = true;
            for (Sample s : mPendingVideo) {
                mMuxer.writeSampleData(mVideoMuxTrack, ByteBuffer.wrap(s.data), s.info);
            }
            mPendingVideo.clear();
            for (Sample s : mPendingAudio) {
                mMuxer.writeSampleData(mAudioMuxTrack, ByteBuffer.wrap(s.data), s.info);
            }
            mPendingAudio.clear();
        }
    }

    private static short clip16(float v) {
        if (v > 32767f) return 32767;
        if (v < -32768f) return -32768;
        return (short) v;
    }

    private static int even(int v) {
        return (v / 2) * 2;
    }

    // ------------------------------------------------------------------
    // GL / EGL
    // ------------------------------------------------------------------
    private void eglSetup(Surface surface) throws Exception {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) throw new Exception("No EGL display");
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new Exception("eglInitialize failed");
        }
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                || numConfigs[0] < 1) {
            throw new Exception("eglChooseConfig failed");
        }
        int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        int[] surfAttribs = {EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], surface, surfAttribs, 0);
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new Exception("eglMakeCurrent failed");
        }
    }

    private void glSetup(int w, int h) throws Exception {
        GLES20.glViewport(0, 0, w, h);
        String vertexSource =
                "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTexCoord;\n" +
                        "uniform mat4 uMVPMatrix;\n" +
                        "varying vec2 vTexCoord;\n" +
                        "void main() {\n" +
                        "  gl_Position = aPosition;\n" +
                        "  vTexCoord = (uMVPMatrix * aTexCoord).xy;\n" +
                        "}\n";
        String fragmentSource =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTexCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
                        "}\n";
        mProgram = buildProgram(vertexSource, fragmentSource);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mTexLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        mMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        float[] quad = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
        float[] tex = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
        mVertexCoords = ByteBuffer.allocateDirect(quad.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexCoords.put(quad).position(0);
        mTexCoords = ByteBuffer.allocateDirect(tex.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoords.put(tex).position(0);
    }

    private int buildProgram(String vertexSource, String fragmentSource) throws Exception {
        int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vs, vertexSource);
        GLES20.glCompileShader(vs);
        int[] status = new int[1];
        GLES20.glGetShaderiv(vs, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) throw new Exception("Vertex shader: " + GLES20.glGetShaderInfoLog(vs));
        int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fs, fragmentSource);
        GLES20.glCompileShader(fs);
        GLES20.glGetShaderiv(fs, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) throw new Exception("Fragment shader: " + GLES20.glGetShaderInfoLog(fs));
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) throw new Exception("Program link: " + GLES20.glGetProgramInfoLog(program));
        return program;
    }

    private boolean awaitFrame(long timeoutMs) {
        synchronized (mFrameSync) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!mFrameAvailable) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return false;
                try {
                    mFrameSync.wait(left);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            mFrameAvailable = false;
        }
        try {
            mSurfaceTexture.updateTexImage();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void drawFrame(long ptsUs) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        mSurfaceTexture.getTransformMatrix(mTransform);
        GLES20.glUniformMatrix4fv(mMatrixLoc, 1, false, mTransform, 0);
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 8, mVertexCoords);
        GLES20.glEnableVertexAttribArray(mTexLoc);
        GLES20.glVertexAttribPointer(mTexLoc, 2, GLES20.GL_FLOAT, false, 8, mTexCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexLoc);
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, ptsUs * 1000L);
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    private void eglTeardown() {
        try {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            }
        } catch (Exception ignored) {
        }
        try {
            if (mEGLSurface != null && mEGLSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            }
        } catch (Exception ignored) {
        }
        try {
            if (mEGLContext != null && mEGLContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            }
        } catch (Exception ignored) {
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Small helper that inspects the audio track layout of a recording. */
    private static class MediaEnumerator {
        int firstStereoAfterMix = -1;
        int monoTrack = -1;
        int anyAudio = -1;

        MediaEnumerator(Context context, RecordingStore.Entry input) throws Exception {
            MediaExtractor ex = new MediaExtractor();
            try {
                RecordingStore.setDataSource(context, ex, input);
                int stereoSeen = 0;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("audio/")) continue;
                    if (anyAudio < 0) anyAudio = i;
                    int ch = f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
                    if (ch == 1) {
                        if (monoTrack < 0) monoTrack = i;
                    } else {
                        stereoSeen++;
                        if (stereoSeen == 2 && firstStereoAfterMix < 0) firstStereoAfterMix = i;
                    }
                }
            } finally {
                try {
                    ex.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Decodes one audio track to PCM stereo with optional noise reduction / voice boost. */
    private static class AudioPcmReader {
        private final MediaExtractor extractor;
        private final MediaCodec decoder;
        private final int channels;
        private final AudioDsp dsp;
        private final short[] carry = new short[4096];
        private int carryPos = 0;
        private int carryLen = 0;
        private boolean inputEos = false;
        private boolean outputEos = false;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        AudioPcmReader(Context context, RecordingStore.Entry input, int trackIndex,
                       boolean nr, boolean boost) throws Exception {
            extractor = new MediaExtractor();
            RecordingStore.setDataSource(context, extractor, input);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            extractor.selectTrack(trackIndex);
            channels = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            dsp = new AudioDsp(nr, boost);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime != null ? mime : "audio/mp4a-latm");
            decoder.configure(trackFormat, null, null, 0);
            decoder.start();
        }

        /**
         * Fills out (interleaved stereo, 2*frames shorts) with the next chunk.
         * Returns false when the track has ended.
         */
        boolean readStereo(short[] out, int frames) {
            int done = 0;
            while (done < frames && !outputEos) {
                if (carryPos < carryLen) {
                    int take = Math.min(frames - done, carryLen - carryPos);
                    for (int i = 0; i < take; i++) {
                        int src = (carryPos + i) * channels;
                        short l = carry[src];
                        short r = channels >= 2 ? carry[src + 1] : l;
                        out[(done + i) * 2] = dsp.process(l, false);
                        out[(done + i) * 2 + 1] = dsp.process(r, true);
                        done++;
                    }
                    carryPos += take;
                    continue;
                }
                if (!inputEos) {
                    try {
                        int inIdx = decoder.dequeueInputBuffer(20_000L);
                        if (inIdx >= 0) {
                            ByteBuffer ib = decoder.getInputBuffer(inIdx);
                            int size = extractor.readSampleData(ib, 0);
                            if (size < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEos = true;
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, size, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    } catch (Exception e) {
                        inputEos = true;
                    }
                    continue;
                }
                int outIdx;
                try {
                    outIdx = decoder.dequeueOutputBuffer(info, 20_000L);
                } catch (Exception e) {
                    outputEos = true;
                    break;
                }
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputEos) outputEos = true;
                    continue;
                }
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        ByteBuffer ob = decoder.getOutputBuffer(outIdx);
                        if (ob != null) {
                            int samples = Math.min(info.size / 2 / Math.max(1, channels), carry.length / Math.max(1, channels));
                            ob.position(info.offset);
                            int shorts = samples * channels;
                            for (int i = 0; i < shorts; i++) {
                                carry[i] = ob.getShort();
                            }
                            carryPos = 0;
                            carryLen = samples;
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
                }
            }
            for (int i = done; i < frames; i++) {
                out[i * 2] = 0;
                out[i * 2 + 1] = 0;
            }
            return done > 0 || !outputEos;
        }

        void release() {
            try {
                decoder.stop();
            } catch (Exception ignored) {
            }
            try {
                decoder.release();
            } catch (Exception ignored) {
            }
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** High-pass + noise gate + boost, streaming, no memory cost. */
    private static class AudioDsp {
        private final boolean nr;
        private final boolean boost;
        private float xL, yL, xR, yR;

        AudioDsp(boolean nr, boolean boost) {
            this.nr = nr;
            this.boost = boost;
        }

        short process(short s, boolean right) {
            if (!nr && !boost) return s;
            float v = s;
            if (nr) {
                float xPrev = right ? xR : xL;
                float yPrev = right ? yR : yL;
                float y = 0.985f * (yPrev + v - xPrev);
                if (right) {
                    xR = v;
                    yR = y;
                } else {
                    xL = v;
                    yL = y;
                }
                v = y;
                if (Math.abs(v) < 800f) v *= 0.12f; // noise gate
            }
            if (boost) {
                v *= 1.8f;
                if (v > 32767f) v = 32767f;
                else if (v < -32768f) v = -32768f;
            } else if (v > 32767f) v = 32767f;
            else if (v < -32768f) v = -32768f;
            return (short) v;
        }
    }
}
