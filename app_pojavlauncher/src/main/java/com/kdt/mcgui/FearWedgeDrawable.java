package com.kdt.mcgui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.utils.FearTheme;

/**
 * The side panel's silhouette - an inverted trapezoid, matching the RedMagic
 * game-space panel the reference shows.
 *
 * Left edge straight and vertical, top and bottom edges horizontal, and the
 * inner (right) edge a diagonal that angles inward from the top toward the
 * bottom, so the slab is widest at the top and tapers as it descends.
 *
 * The slanted edge carries a neon glow in the accent colour, which rides the
 * shared red &lt;-&gt; blue cycle.
 */
public class FearWedgeDrawable extends Drawable {
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    /** How far the inner edge leans in over the panel's full height, in px. */
    private float mSlant = 60f;

    public FearWedgeDrawable() {
        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(0xD90C0508);
        mGlow.setStyle(Paint.Style.STROKE);
        mGlow.setStrokeWidth(14f);
        mGlow.setStrokeJoin(Paint.Join.ROUND);
        mEdge.setStyle(Paint.Style.STROKE);
        mEdge.setStrokeWidth(3f);
        mEdge.setStrokeJoin(Paint.Join.ROUND);
        mEdge.setColor(FearTheme.RED);
    }

    public void setSlant(float px) { mSlant = px; invalidateSelf(); }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float w = getBounds().width();
        float h = getBounds().height();
        float s = Math.min(mSlant, w * 0.45f);
        mPath.reset();
        mPath.moveTo(0f, 0f);
        mPath.lineTo(w, 0f);           // straight top
        mPath.lineTo(w - s, h);        // inner edge leans inward going down
        mPath.lineTo(0f, h);           // straight bottom
        mPath.close();

        int accent = FearTheme.accent();
        canvas.drawPath(mPath, mFill);

        // neon bloom hugging the slanted inner edge
        mGlow.setColor((accent & 0x00FFFFFF) | 0x55000000);
        canvas.drawPath(mPath, mGlow);

        mEdge.setColor(accent);
        canvas.drawPath(mPath, mEdge);
    }

    @Override public void setAlpha(int alpha) { mFill.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable android.graphics.ColorFilter cf) { mFill.setColorFilter(cf); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
