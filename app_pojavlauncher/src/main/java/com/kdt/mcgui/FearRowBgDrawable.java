package com.kdt.mcgui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Sidebar row background.
 *
 * A rounded, faintly lit fill, plus - when the row is the selected one - a narrow
 * white bar down its left edge. Drawn in code because a layer-list cannot express
 * a fixed-width left bar on API 21; its item sizing attributes only exist from
 * API 23, and below that the "bar" stretches across the whole row and blanks it out.
 */
public class FearRowBgDrawable extends Drawable {
    private static final int RESTING = 0x14FFFFFF;
    private static final int ACTIVE  = 0x1FFFFFFF;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private boolean mActive;
    private float mRadius = 12f;
    private float mBarWidth = 3f;
    private float mBarInset = 9f;

    public FearRowBgDrawable() {
        mBar.setColor(0xFFFFFFFF);
    }

    public void setMetrics(float density) {
        mRadius = 12f * density;
        mBarWidth = 3f * density;
        mBarInset = 9f * density;
    }

    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        invalidateSelf();
    }

    public boolean isActive() { return mActive; }

    @Override
    public void draw(@NonNull Canvas canvas) {
        mRect.set(getBounds());
        mFill.setColor(mActive ? ACTIVE : RESTING);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mFill);

        if (mActive) {
            float w = mBarWidth;
            mRect.set(0f, mBarInset, w, getBounds().height() - mBarInset);
            canvas.drawRoundRect(mRect, w / 2f, w / 2f, mBar);
        }
    }

    @Override public void setAlpha(int alpha) { mFill.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable android.graphics.ColorFilter cf) { mFill.setColorFilter(cf); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
