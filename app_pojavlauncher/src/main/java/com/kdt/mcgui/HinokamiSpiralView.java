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

    private void init() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(8f);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 20f;
        mRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        for (int i = 0; i < 3; i++) {
            mPaint.setColor(i == 0 ? 0xFFFF4500 : (i == 1 ? 0xFFFFD700 : 0xFFFF8C00));
            mPaint.setStrokeWidth(12f - (i * 3f));
            canvas.save();
            canvas.rotate(mRotation + (i * 120f), cx, cy);
            canvas.drawArc(mRect, 0, 90, false, mPaint);
            canvas.drawArc(mRect, 180, 45, false, mPaint);
            canvas.restore();
        }

        mRotation += 5f;
        invalidate();
    }
}
