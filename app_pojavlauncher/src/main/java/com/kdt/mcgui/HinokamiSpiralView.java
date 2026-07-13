package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class HinokamiSpiralView extends View {
    private Paint mPaint;
    private float mRotation = 0;
    private final RectF mRect = new RectF();

    public HinokamiSpiralView(Context context) { super(context); init(); }
    public HinokamiSpiralView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    private float[] mSpeeds = {4f, -3f, 6f};
    private float[] mAlphas = {1f, 1f, 1f};

    private void init() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) - 20f;

        for (int i = 0; i < 3; i++) {
            float radius = maxRadius - (i * 25f);
            mRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

            // Energy/Spirit colors (Blue/Cyan/White) for the Pure White theme
            int color = i == 0 ? 0xFF00B0FF : (i == 1 ? 0xFFFFFFFF : 0xFF00E5FF);
            mPaint.setColor(color);
            mPaint.setAlpha((int)(mAlphas[i] * 255));
            mPaint.setStrokeWidth(15f - (i * 4f));

            canvas.save();
            canvas.rotate(mRotation * mSpeeds[i], cx, cy);

            // Draw dual arcs for a "slashing" ability effect
            canvas.drawArc(mRect, 0, 120, false, mPaint);
            canvas.drawArc(mRect, 180, 90, false, mPaint);

            // Add a glowing tip
            mPaint.setAlpha(255);
            mPaint.setStrokeWidth(20f - (i * 4f));
            canvas.drawArc(mRect, 115, 5, false, mPaint);

            canvas.restore();
        }

        mRotation += 1.5f;
        invalidate();
    }
}
