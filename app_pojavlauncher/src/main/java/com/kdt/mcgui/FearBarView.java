package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * The FEAR loading bar.
 *
 * The frames were lifted from the reference clip and had their black background
 * keyed out, so only the dragon head and its track are drawn - the home screen
 * shows straight through everything else.
 *
 * The frame on screen is picked from the download percentage. That means the
 * animation runs at the speed of the transfer itself: it crawls while the line
 * is slow and races when it is fast, and it can never run ahead of the bytes
 * that have actually arrived.
 */
public class FearBarView extends View {
    private static final int FRAME_COUNT = 31;
    private static int[] sFrameIds;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mDst = new RectF();
    private Bitmap mFrame;
    private float mProgress = 0f;
    private int mShown = -1;

    public FearBarView(Context context) { super(context); init(); }
    public FearBarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public FearBarView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);
        if (sFrameIds == null) {
            int[] ids = new int[FRAME_COUNT];
            for (int i = 0; i < FRAME_COUNT; i++) {
                ids[i] = getResources().getIdentifier(
                        String.format(Locale.US, "fear_bar_f%02d", i),
                        "drawable", getContext().getPackageName());
            }
            sFrameIds = ids;
        }
    }

    /** @param progress 0..100 */
    public void setProgress(float progress) {
        float p = Math.max(0f, Math.min(100f, progress));
        if (p == mProgress) return;
        mProgress = p;
        loadFrame();
        invalidate();
    }

    public float getProgress() { return mProgress; }

    private void loadFrame() {
        int idx = Math.round(mProgress / 100f * (FRAME_COUNT - 1));
        if (idx < 0) idx = 0;
        if (idx > FRAME_COUNT - 1) idx = FRAME_COUNT - 1;
        if (idx == mShown) return;
        mShown = idx;
        if (sFrameIds == null || sFrameIds[idx] == 0) return;
        Bitmap old = mFrame;
        mFrame = BitmapFactory.decodeResource(getResources(), sFrameIds[idx]);
        if (old != null && !old.isRecycled()) old.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFrame == null) loadFrame();
        if (mFrame == null) return;
        float scale = Math.min(getWidth() / (float) mFrame.getWidth(),
                               getHeight() / (float) mFrame.getHeight());
        float dw = mFrame.getWidth() * scale;
        float dh = mFrame.getHeight() * scale;
        float left = (getWidth() - dw) / 2f;
        float top = (getHeight() - dh) / 2f;
        mDst.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(mFrame, null, mDst, mPaint);
    }
}
