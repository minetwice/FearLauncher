package com.kdt.mcgui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.utils.FearTheme;

/**
 * The on/off pill from the reference.
 *
 * A stadium track with a round knob. Switched on the track takes the accent
 * colour and the knob goes pale; switched off the track is grey and the knob
 * goes dark. There is a soft glow and a hairline border either way, and the
 * change animates rather than snapping.
 */
public class FearToggle extends View {
    private static final int OFF_TRACK = 0xFF4A4A4A;
    private static final int ON_KNOB   = 0xFFF3F7FF;
    private static final int OFF_KNOB  = 0xFF232323;

    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private boolean mChecked;
    private float mPhase;          // 0 = off, 1 = on
    private float mDensity = 1f;
    private ValueAnimator mAnimator;
    private OnCheckedChangeListener mListener;

    public interface OnCheckedChangeListener { void onCheckedChanged(FearToggle toggle, boolean checked); }

    public FearToggle(Context c) { super(c); init(); }
    public FearToggle(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public FearToggle(Context c, @Nullable AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        mDensity = getResources().getDisplayMetrics().density;
        mGlow.setStyle(Paint.Style.FILL);
        mBorder.setStyle(Paint.Style.STROKE);
        mBorder.setStrokeWidth(Math.max(1f, 1f * mDensity));
        setClickable(true);
        setFocusable(true);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { mListener = l; }

    public boolean isChecked() { return mChecked; }

    public void setChecked(boolean checked) { setChecked(checked, false); }

    public void setChecked(boolean checked, boolean animate) {
        if (mChecked == checked && mPhase == (checked ? 1f : 0f)) return;
        mChecked = checked;
        if (mAnimator != null) { mAnimator.cancel(); mAnimator = null; }
        if (!animate) {
            mPhase = checked ? 1f : 0f;
            invalidate();
            return;
        }
        mAnimator = ValueAnimator.ofFloat(mPhase, checked ? 1f : 0f);
        mAnimator.setDuration(190);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(a -> { mPhase = (float) a.getAnimatedValue(); invalidate(); });
        mAnimator.start();
    }

    public void toggle() {
        setChecked(!mChecked, true);
        if (mListener != null) mListener.onCheckedChanged(this, mChecked);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = h / 2f;
        int accent = FearTheme.accent();

        mRect.set(0, 0, w, h);

        // soft glow under the track, strongest when on
        mGlow.setColor((accent & 0x00FFFFFF) | ((int) (0x0A + 0x3A * mPhase) << 24));
        float g = 2.5f * mDensity;
        mRect.inset(-g, -g);
        canvas.drawRoundRect(mRect, r + g, r + g, mGlow);
        mRect.set(0, 0, w, h);

        // track
        mTrack.setColor(blend(OFF_TRACK, accent, mPhase));
        canvas.drawRoundRect(mRect, r, r, mTrack);

        // hairline border
        mBorder.setColor(blend(0x55FFFFFF, 0xAAFFFFFF, mPhase));
        mRect.inset(0.5f, 0.5f);
        canvas.drawRoundRect(mRect, r, r, mBorder);
        mRect.set(0, 0, w, h);

        // knob
        float pad = 2.5f * mDensity;
        float knobR = r - pad;
        float cx = r + (w - 2f * r) * mPhase;
        mKnob.setColor(blend(OFF_KNOB, ON_KNOB, mPhase));
        canvas.drawCircle(cx, r, knobR, mKnob);
        // small highlight so the knob reads as glass
        mKnob.setColor(0x33FFFFFF);
        canvas.drawCircle(cx - knobR * 0.28f, r - knobR * 0.3f, knobR * 0.42f, mKnob);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        switch (event.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start();
                return true;
            case android.view.MotionEvent.ACTION_UP:
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                toggle();
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
