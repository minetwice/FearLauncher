package net.kdt.pojavlaunch.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The clip strip under the preview: one block for the whole recording, a playhead, and a
 * trim handle at each end.
 *
 * Drawn rather than assembled from child views because a timeline is a single continuous
 * scale - the playhead, the clip and both handles have to share one time-to-pixel mapping,
 * and keeping that in one place is what stops them drifting apart.
 */
public class TimelineView extends View {

    public interface Listener {
        void onScrubTo(float fraction);
        void onTrimChanged(float startFraction, float endFraction);
    }

    private static final int NONE = 0, PLAYHEAD = 1, TRIM_START = 2, TRIM_END = 3;

    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPlayhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private float mPlay = 0f;      // 0..1
    private float mTrimStart = 0f;
    private float mTrimEnd = 1f;
    private int mDragging = NONE;

    private Listener mListener;

    public TimelineView(Context context) {
        super(context);
        init();
    }

    public TimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mTrack.setColor(0xFF141824);
        mClip.setColor(0xFF2E6BFF);
        mHandle.setColor(0xFFFF2B3A);
        mPlayhead.setColor(0xFFFFFFFF);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setProgress(float fraction) {
        mPlay = clamp(fraction);
        invalidate();
    }

    public float getTrimStart() {
        return mTrimStart;
    }

    public float getTrimEnd() {
        return mTrimEnd;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = 12f;

        mRect.set(0, pad, w, h - pad);
        canvas.drawRoundRect(mRect, 10, 10, mTrack);

        // the kept part of the clip, between the two trim handles
        mRect.set(mTrimStart * w, pad, mTrimEnd * w, h - pad);
        canvas.drawRoundRect(mRect, 8, 8, mClip);

        // trim handles
        float handleW = 6f;
        mRect.set(mTrimStart * w, pad, mTrimStart * w + handleW, h - pad);
        canvas.drawRoundRect(mRect, 3, 3, mHandle);
        mRect.set(mTrimEnd * w - handleW, pad, mTrimEnd * w, h - pad);
        canvas.drawRoundRect(mRect, 3, 3, mHandle);

        // playhead
        mRect.set(mPlay * w - 1.5f, 0, mPlay * w + 1.5f, h);
        canvas.drawRoundRect(mRect, 1.5f, 1.5f, mPlayhead);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float w = Math.max(getWidth(), 1);
        float fraction = clamp(x / w);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = pickTarget(x, w);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDragging == TRIM_START) {
                    mTrimStart = Math.min(fraction, mTrimEnd - 0.02f);
                    notifyTrim();
                } else if (mDragging == TRIM_END) {
                    mTrimEnd = Math.max(fraction, mTrimStart + 0.02f);
                    notifyTrim();
                } else if (mDragging == PLAYHEAD) {
                    mPlay = fraction;
                    if (mListener != null) mListener.onScrubTo(mPlay);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = NONE;
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** Whichever of the three targets is nearest to the touch wins. */
    private int pickTarget(float x, float w) {
        float startX = mTrimStart * w;
        float endX = mTrimEnd * w;
        float playX = mPlay * w;
        float threshold = 40f;

        float dStart = Math.abs(x - startX);
        float dEnd = Math.abs(x - endX);
        float dPlay = Math.abs(x - playX);

        if (dStart < threshold && dStart <= dEnd) return TRIM_START;
        if (dEnd < threshold) return TRIM_END;
        return PLAYHEAD;
    }

    private void notifyTrim() {
        if (mListener != null) mListener.onTrimChanged(mTrimStart, mTrimEnd);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
