package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class ColorWheelView extends View {

    private Paint mWheelPaint;
    private Paint mSelectorPaint;
    private float mCenterX;
    private float mCenterY;
    private float mRadius;
    private float mSelectedX;
    private float mSelectedY;

    private int mSelectedColor = Color.CYAN;
    private OnColorSelectedListener mListener;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWheelPaint.setStyle(Paint.Style.FILL);

        mSelectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectorPaint.setStyle(Paint.Style.STROKE);
        mSelectorPaint.setColor(Color.WHITE);
        mSelectorPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2f;
        mCenterY = h / 2f;
        mRadius = Math.min(mCenterX, mCenterY) - 15f;

        // Create SweepGradient for rainbow hue
        int[] colors = {0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000};
        SweepGradient gradient = new SweepGradient(mCenterX, mCenterY, colors, null);
        mWheelPaint.setShader(gradient);

        mSelectedX = mCenterX;
        mSelectedY = mCenterY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mWheelPaint);
        // Draw touch selector
        canvas.drawCircle(mSelectedX, mSelectedY, 12f, mSelectorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float dx = x - mCenterX;
        float dy = y - mCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance <= mRadius) {
            mSelectedX = x;
            mSelectedY = y;
            // Calculate hue based on touch angle
            float angle = (float) Math.atan2(dy, dx);
            float hue = (float) (angle * 180 / Math.PI);
            if (hue < 0) hue += 360f;

            // Saturation based on distance ratio
            float saturation = distance / mRadius;

            mSelectedColor = Color.HSVToColor(new float[]{hue, saturation, 1.0f});
            if (mListener != null) {
                mListener.onColorSelected(mSelectedColor);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        mListener = listener;
    }

    public int getSelectedColor() {
        return mSelectedColor;
    }
}
