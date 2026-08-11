package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class GlassParticleView extends View {
    private static final int PARTICLE_COUNT = 20;
    private final Particle[] mParticles = new Particle[PARTICLE_COUNT];
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        // Draw soft ambient red glow pulsing/drifting in the background
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);

        for (Particle p : mParticles) {
            if (p.x == 0 && p.y == 0) p.reset(width, height);

            // Crimson red radial gradient glow for each particle
            RadialGradient radialGradient = new RadialGradient(
                    p.x, p.y, p.size,
                    p.color, 0x00000000,
                    Shader.TileMode.CLAMP
            );
            mPaint.setShader(radialGradient);
            canvas.drawCircle(p.x, p.y, p.size, mPaint);

            // Update particle positions slowly
            p.x += p.vx;
            p.y += p.vy;

            // Soft drift boundary check
            if (p.x < -p.size || p.x > width + p.size || p.y < -p.size || p.y > height + p.size) {
                p.reset(width, height);
            }
        }

        invalidate();
    }

    private class Particle {
        float x, y, vx, vy, size;
        int color;
        void reset(int w, int h) {
            x = mRandom.nextInt(w);
            y = mRandom.nextInt(h);
            // Drifts extremely slowly to keep CPU/GPU friendly
            vx = (mRandom.nextFloat() - 0.5f) * 0.3f;
            vy = (mRandom.nextFloat() - 0.5f) * 0.3f;
            // Larger, soft ambient particles
            size = 120f + mRandom.nextFloat() * 320f;
            // Soft crimson red variations with different alphas
            int alpha = (int) (12 + mRandom.nextFloat() * 28); // Low opacity (4% to 15%)
            color = Color.argb(alpha, 225, 29, 46); // Crimson Red (#e11d2e)
        }
    }
}
