package com.kdt.mcgui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.utils.FearTheme;

/**
 * The side panel's silhouette: a wedge, not a rectangle.
 *
 * The right edge leans, so the panel reads as an angled/triangular slab - the
 * aggressive shape the reference uses - while the left edge stays straight so
 * the rows inside keep a sane baseline. The slanted edge carries the accent
 * colour, which rides the shared red &lt;-&gt; blue cycle.
 */
public class FearWedgeDrawable extends Drawable {
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    /** How far the right edge leans in over the panel's full height, in px. */
    private float mSlant = 34f;

    public FearWedgeDrawable() {
        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(0xE60E0509);
        mEdge.setStyle(Paint.Style.STROKE);
        mEdge.setStrokeWidth(3f);
        mEdge.setColor(FearTheme.RED);
    }

    public void setSlant(float px) { mSlant = px; invalidateSelf(); }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float w = getBounds().width();
        float h = getBounds().height();
        float s = Math.min(mSlant, w * 0.35f);
        mPath.reset();
        mPath.moveTo(0f, 0f);
        mPath.lineTo(w, 0f);
        mPath.lineTo(w - s, h);
        mPath.lineTo(0f, h);
        mPath.close();
        mEdge.setColor(FearTheme.accent());
        canvas.drawPath(mPath, mFill);
        canvas.drawPath(mPath, mEdge);
    }

    @Override public void setAlpha(int alpha) { mFill.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable android.graphics.ColorFilter cf) { mFill.setColorFilter(cf); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
