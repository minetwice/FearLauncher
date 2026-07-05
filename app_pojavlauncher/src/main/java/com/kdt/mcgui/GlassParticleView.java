package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class GlassParticleView extends View {
    private static final int PARTICLE_COUNT = 15;
    private final Particle[] mParticles = new Particle[PARTICLE_COUNT];
    private final Paint mPaint = new Paint();
    private final Random mRandom = new Random();

    public GlassParticleView(Context context) { super(context); init(); }
    public GlassParticleView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            mParticles[i] = new Particle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        for (Particle p : mParticles) {
            if (p.x == 0 && p.y == 0) p.reset(width, height);

            mPaint.setAlpha((int) (p.alpha * 255));
            mPaint.setShader(new LinearGradient(p.x, p.y, p.x + p.size, p.y + p.size,
                    0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));

            canvas.drawCircle(p.x, p.y, p.size, mPaint);

            p.x += p.vx;
            p.y += p.vy;

            if (p.x < -p.size || p.x > width + p.size || p.y < -p.size || p.y > height + p.size) {
                p.reset(width, height);
            }
        }

        invalidate();
    }

    private class Particle {
        float x, y, vx, vy, size, alpha;
        void reset(int w, int h) {
            x = mRandom.nextInt(w);
            y = mRandom.nextInt(h);
            vx = (mRandom.nextFloat() - 0.5f) * 0.5f;
            vy = (mRandom.nextFloat() - 0.5f) * 0.5f;
            size = 100f + mRandom.nextFloat() * 300f;
            alpha = 0.05f + mRandom.nextFloat() * 0.1f;
        }
    }
}
