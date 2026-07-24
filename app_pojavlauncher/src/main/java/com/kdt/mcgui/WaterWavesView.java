package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class WaterWavesView extends View {

    private Paint mWavePaint1;
    private Paint mWavePaint2;
    private Paint mWavePaint3;

    private Path mPath1 = new Path();
    private Path mPath2 = new Path();
    private Path mPath3 = new Path();

    private float mOffset1 = 0f;
    private float mOffset2 = 0f;
    private float mOffset3 = 0f;

    private int mPrimaryColor = 0x3300F0FF;   // Default translucent cyan
    private int mSecondaryColor = 0x22005BFF; // Default translucent deep sapphire

    public WaterWavesView(Context context) {
        super(context);
        init();
    }

    public WaterWavesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaterWavesView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mWavePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaint1.setStyle(Paint.Style.FILL);

        mWavePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaint2.setStyle(Paint.Style.FILL);

        mWavePaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaint3.setStyle(Paint.Style.FILL);

        updatePaintColors();
    }

    public void setThemeColors(int primaryColor, int secondaryColor) {
        mPrimaryColor = (primaryColor & 0x00FFFFFF) | 0x2E000000;   // Inset alpha
        mSecondaryColor = (secondaryColor & 0x00FFFFFF) | 0x1A000000; // Inset alpha
        updatePaintColors();
        postInvalidate();
    }

    private void updatePaintColors() {
        mWavePaint1.setColor(mPrimaryColor);
        mWavePaint2.setColor(mSecondaryColor);
        mWavePaint3.setColor((mPrimaryColor & 0x00FFFFFF) | 0x15000000); // Super faint layer
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        // Base line of waves at 80% screen height
        float baseHeight = height * 0.82f;

        mPath1.reset();
        mPath2.reset();
        mPath3.reset();

        mPath1.moveTo(0, height);
        mPath2.moveTo(0, height);
        mPath3.moveTo(0, height);

        mPath1.lineTo(0, baseHeight);
        mPath2.lineTo(0, baseHeight - 15f);
        mPath3.lineTo(0, baseHeight + 10f);

        // Calculate sine paths
        for (int x = 0; x <= width; x += 12) {
            float y1 = (float) (Math.sin((x * 0.005) + mOffset1) * 32f) + baseHeight;
            float y2 = (float) (Math.sin((x * 0.007) + mOffset2) * 24f) + baseHeight - 10f;
            float y3 = (float) (Math.cos((x * 0.004) + mOffset3) * 18f) + baseHeight + 15f;

            mPath1.lineTo(x, y1);
            mPath2.lineTo(x, y2);
            mPath3.lineTo(x, y3);
        }

        mPath1.lineTo(width, height);
        mPath2.lineTo(width, height);
        mPath3.lineTo(width, height);

        mPath1.close();
        mPath2.close();
        mPath3.close();

        // Draw overlapping paths
        canvas.drawPath(mPath3, mWavePaint3);
        canvas.drawPath(mPath2, mWavePaint2);
        canvas.drawPath(mPath1, mWavePaint1);

        // Update offsets for continuous beautiful flowing animation
        mOffset1 += 0.022f;
        mOffset2 += 0.015f;
        mOffset3 += 0.010f;

        // Repeat animation in the next frame smoothly (approx. 60 FPS)
        postInvalidateDelayed(16);
    }
}
