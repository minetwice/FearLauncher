package com.kdt.mcgui;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

/**
 * FEAR home background: a muted clip that loops forever behind the UI.
 *
 * It is a TextureView rather than a VideoView on purpose - a SurfaceView punches
 * a hole through the window and would end up drawn over the interface, while a
 * TextureView composites like any other view, so the panels sit on top of it.
 * The clip is centre-cropped to fill and muted (it is a backdrop, not a video
 * player).
 */
public class LoopingVideoBackground extends TextureView implements TextureView.SurfaceTextureListener {
    private MediaPlayer mPlayer;
    private int mResId = 0;

    public LoopingVideoBackground(Context context) {
        super(context);
        init();
    }

    public LoopingVideoBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setSurfaceTextureListener(this);
    }

    /** Point the background at a raw video resource. Safe to call before/after layout. */
    public void setVideoResource(int resId) {
        mResId = resId;
        if (isAvailable()) startPlayback(getSurfaceTexture());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        startPlayback(surfaceTexture);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) { }

    private void startPlayback(SurfaceTexture surfaceTexture) {
        if (mResId == 0 || surfaceTexture == null) return;
        release();
        try {
            mPlayer = MediaPlayer.create(getContext(), mResId);
            if (mPlayer == null) return;
            mPlayer.setSurface(new Surface(surfaceTexture));
            mPlayer.setLooping(true);
            mPlayer.setVolume(0f, 0f);
            mPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            mPlayer.start();
        } catch (Throwable t) {
            release();
        }
    }

    /** Stop and free the player. */
    public void release() {
        if (mPlayer == null) return;
        try { mPlayer.stop(); } catch (Throwable ignored) { }
        try { mPlayer.release(); } catch (Throwable ignored) { }
        mPlayer = null;
    }
}
