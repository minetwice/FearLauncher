package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Random;

public class BackgroundAnimationView extends View {

    private int mAnimType = 0; // 0 to 14 representing the 15 Intense Custom animations
    private int mPrimaryColor = 0x00F0FF;
    private int mSecondaryColor = 0x005BFF;

    private final Paint mPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mPath1 = new Path();
    private final Path mPath2 = new Path();

    private final Random mRandom = new Random();

    // Fields for Intense Particle Arrays
    private static final int MAX_PARTICLES = 40;
    private final Particle[] mParticles = new Particle[MAX_PARTICLES];

    // Global Animation phases/timers for complex effects
    private float mAnimPhase = 0f;
    private int mTimeStopTimer = 0;
    private float mShakeX = 0f;
    private float mShakeY = 0f;
    private boolean mFlashActive = false;
    private float mPortalAngle = 0f;
    private float mOffset1 = 0f;
    private float mOffset2 = 0f;
    private float mOffset3 = 0f;

    public BackgroundAnimationView(Context context) {
        super(context);
        init();
    }

    public BackgroundAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BackgroundAnimationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            mParticles[i] = new Particle();
        }
        updatePaintStyle();
    }

    public void setAnimationType(int type) {
        mAnimType = Math.max(0, Math.min(type, 14));
        postInvalidate();
    }

    public void setThemeColors(int primaryColor, int secondaryColor) {
        mPrimaryColor = primaryColor;
        mSecondaryColor = secondaryColor;
        updatePaintStyle();
        postInvalidate();
    }

    private void updatePaintStyle() {
        mPaint1.setStyle(Paint.Style.FILL);
        mPaint2.setStyle(Paint.Style.FILL);
        mPaint3.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        // Apply global screen shake offset if active (for speed dashes, slashes, power-ups)
        canvas.save();
        if (mShakeX != 0 || mShakeY != 0) {
            canvas.translate(mShakeX, mShakeY);
            mShakeX *= 0.85f;
            mShakeY *= 0.85f;
            if (Math.abs(mShakeX) < 1f) mShakeX = 0f;
            if (Math.abs(mShakeY) < 1f) mShakeY = 0f;
        }

        switch (mAnimType) {
            case 0: drawSupremeAura(canvas, width, height); break;
            case 1: drawRasenganSpiral(canvas, width, height); break;
            case 2: drawChidoriLightning(canvas, width, height); break;
            case 3: drawSpiritBomb(canvas, width, height); break;
            case 4: drawBankaiSlash(canvas, width, height); break;
            case 5: drawKamehameha(canvas, width, height); break;
            case 6: drawDragonsFury(canvas, width, height); break;
            case 7: drawVoidImplosion(canvas, width, height); break;
            case 8: drawFractalSpiral(canvas, width, height); break;
            case 9: drawTemporalRewind(canvas, width, height); break;
            case 10: drawVoltaicStorm(canvas, width, height); break;
            case 11: drawGravityDistortion(canvas, width, height); break;
            case 12: drawPrismaticShatter(canvas, width, height); break;
            case 13: drawNebulaSwirl(canvas, width, height); break;
            case 14: drawPlasmaEruption(canvas, width, height); break;
        }

        canvas.restore();

        // Strobe Flash handler (Nuclear dome, ultimate charge, lightning)
        if (mFlashActive) {
            mPaint1.setShader(null);
            mPaint1.setColor(0xCCFFFFFF);
            canvas.drawRect(0, 0, width, height, mPaint1);
            mFlashActive = false;
        }

        // Keep infinite looping (60 FPS)
        postInvalidateDelayed(16);
    }

    // 1. Supreme Aura Explosion
    private void drawSupremeAura(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mAnimPhase += 0.05f;

        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
        float radiusBase = (float) Math.sin(mAnimPhase * 2) * 20f + 140f;
        canvas.drawCircle(cx, cy, radiusBase, mPaint1);

        // draw smaller energy waves
        mPaint2.setStyle(Paint.Style.STROKE);
        mPaint2.setStrokeWidth(3f);
        mPaint2.setColor((mSecondaryColor & 0x00FFFFFF) | 0x80000000);
        canvas.drawCircle(cx, cy, radiusBase + 15f + (float)Math.sin(mAnimPhase)*10f, mPaint2);
        canvas.drawCircle(cx, cy, radiusBase - 15f - (float)Math.sin(mAnimPhase)*10f, mPaint2);
        mPaint2.setStyle(Paint.Style.FILL);

        // rising energy columns in core
        mPaint3.setColor(0xFFFFFFFF);
        canvas.drawRect(cx - 15f, cy - radiusBase, cx + 15f, cy + radiusBase, mPaint3);

        // particles floating around
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 0) p.reset(width, height, 0);
            p.y -= p.vy * 0.8f;
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);
            if (p.y < 0) p.reset(width, height, 0);
        }
    }

    // 2. Rasengan Spiral Sphere
    private void drawRasenganChakra(Canvas canvas, int width, int height) {
        // Alias method for compilation
        drawRasenganSpiral(canvas, width, height);
    }

    private void drawRasenganSpiral(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mPortalAngle += 0.06f;

        // Draw multiple rotating inner rings
        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(3f);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x60000000);
        canvas.drawCircle(cx, cy, 100f, mPaint1);

        mPaint1.setColor((mSecondaryColor & 0x00FFFFFF) | 0x40000000);
        canvas.drawCircle(cx, cy, 120f, mPaint1);

        // opposite rotating outer ring
        mPaint2.setStyle(Paint.Style.STROKE);
        mPaint2.setStrokeWidth(4f);
        mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x80000000);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(-mPortalAngle * 57.29f);
        canvas.drawOval(-130f, -60f, 130f, 60f, mPaint2);
        canvas.restore();

        mPaint1.setStyle(Paint.Style.FILL);
        mPaint2.setStyle(Paint.Style.FILL);

        // swirling inner streams
        for (int i = 0; i < 6; i++) {
            float angle = mPortalAngle + (i * 1.047f);
            float px = cx + (float) Math.cos(angle) * 100f;
            float py = cy + (float) Math.sin(angle) * 100f;
            mPaint3.setColor(0xFFFFFFFF);
            canvas.drawCircle(px, py, 8f, mPaint3);
        }
    }

    // 3. Chidori Lightning Claw
    private void drawChidoriLightning(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mPortalAngle += 0.08f;

        mPaint1.setShader(null);
        mPaint1.setColor(0xFF80E0FF); // Chidori lightning neon-blue
        mPaint1.setStrokeWidth(3.5f);

        // core bright flashes
        mPaint2.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, 35f, mPaint2);
        mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x90000000);
        canvas.drawCircle(cx, cy, 65f, mPaint2);

        // dynamic electric branches
        if (mRandom.nextFloat() > 0.4f) {
            mPath1.reset();
            mPath1.moveTo(cx, cy);
            float lx = cx;
            float ly = cy;
            for (int i = 0; i < 5; i++) {
                lx += (mRandom.nextFloat() - 0.5f) * 150f;
                ly += (mRandom.nextFloat() - 0.5f) * 150f;
                mPath1.lineTo(lx, ly);
            }
            mPaint1.setStyle(Paint.Style.STROKE);
            canvas.drawPath(mPath1, mPaint1);
            mPaint1.setStyle(Paint.Style.FILL);
        }
    }

    // 4. Spirit Bomb Gathering
    private void drawSpiritBomb(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height * 0.25f; // Bomb forms at top
        mAnimPhase += 0.03f;

        mPaint1.setShader(null);
        float bombRadius = (mAnimPhase * 15f) % 180f + 30f;
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x60000000);
        canvas.drawCircle(cx, cy, bombRadius, mPaint1);

        mPaint2.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, bombRadius * 0.4f, mPaint2);

        // particles spiraling in
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 3) p.reset(width, height, 3);

            float dx = cx - p.x;
            float dy = cy - p.y;
            p.x += dx * 0.04f + Math.sin(mAnimPhase + p.vy) * 4f;
            p.y += dy * 0.04f + Math.cos(mAnimPhase + p.vy) * 4f;

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            if (Math.abs(dx) < 25f && Math.abs(dy) < 25f) {
                p.reset(width, height, 3);
            }
        }
    }

    // 5. Bankai Slash Eruption
    private void drawBankaiSlash(Canvas canvas, int width, int height) {
        mAnimPhase += 0.04f;
        float cx = width / 2f;

        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFFFFF);
        mPaint1.setStrokeWidth(5f);

        // draw sharp vertical slash
        if (((int)(mAnimPhase * 10) % 6) == 0) {
            canvas.drawLine(cx, 0, cx, height, mPaint1);
            mShakeX = (mRandom.nextFloat() - 0.5f) * 20f;
            mShakeY = (mRandom.nextFloat() - 0.5f) * 20f;
        }

        // shattering transparent glass pieces drifting around
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 4) p.reset(width, height, 4);

            mPaint2.setColor(0x40FFFFFF);
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.vx * 30f);
            canvas.drawRect(-p.size, -p.size, p.size, p.size, mPaint2);
            canvas.restore();

            p.y += p.vy * 0.8f;
            if (p.y > height + p.size) p.reset(width, height, 4);
        }
    }

    // 6. Kamehameha Charge-up
    private void drawKamehameha(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mAnimPhase += 0.08f;

        mPaint1.setShader(null);
        // Blinding core laser beam shoots horizontally
        float chargeLevel = (float) Math.sin(mAnimPhase) * 50f + 60f;
        if (chargeLevel > 85f) {
            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x90000000);
            canvas.drawRect(0, cy - 40f, width, cy + 40f, mPaint1);
            mPaint2.setColor(0xFFFFFFFF);
            canvas.drawRect(0, cy - 15f, width, cy + 15f, mPaint2);
            mShakeY = (mRandom.nextFloat() - 0.5f) * 24f;
        } else {
            // charge sphere
            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x60000000);
            canvas.drawCircle(cx, cy, chargeLevel, mPaint1);
            mPaint2.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, chargeLevel * 0.4f, mPaint2);
        }
    }

    // 7. Dragon’s Fury Vortex
    private void drawDragonsFury(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        mAnimPhase += 0.05f;

        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x33000000);

        // Swirling vortex pillars
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 6) p.reset(width, height, 6);

            p.y -= p.vy * 1.5f;
            float swirlRadius = (p.y / (float) height) * 160f + 20f;
            p.x = cx + (float) Math.sin(mAnimPhase + p.vy) * swirlRadius;

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            if (p.y < 0) p.reset(width, height, 6);
        }
    }

    // 8. Void Implosion
    private void drawVoidImplosion(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setColor(0xFF02020B); // central black void spot
        canvas.drawCircle(cx, cy, 65f, mPaint1);

        mPaint2.setStyle(Paint.Style.STROKE);
        mPaint2.setStrokeWidth(3f);
        mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x80000000);
        canvas.drawCircle(cx, cy, 75f, mPaint2);
        mPaint2.setStyle(Paint.Style.FILL);

        // Sucking in lines/particles
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 7) p.reset(width, height, 7);

            float dx = cx - p.x;
            float dy = cy - p.y;
            p.x += dx * 0.05f;
            p.y += dy * 0.05f;

            mPaint3.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
            canvas.drawRect(p.x, p.y, p.x + p.size * 2f, p.y + p.size / 2f, mPaint3);

            if (Math.abs(dx) < 65f && Math.abs(dy) < 65f) {
                p.reset(width, height, 7);
            }
        }
    }

    // 9. Fractal Spiral Burst
    private void drawFractalSpiral(Canvas canvas, int width, int height) {
        mAnimPhase += 0.02f;
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(2f);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x3D000000);

        // draw complex fractal pattern lines recursively
        for (int i = 0; i < 8; i++) {
            float angle = mAnimPhase + (i * 0.785f);
            float x1 = cx + (float) Math.cos(angle) * 50f;
            float y1 = cy + (float) Math.sin(angle) * 50f;
            float x2 = cx + (float) Math.cos(angle) * 180f;
            float y2 = cy + (float) Math.sin(angle) * 180f;

            canvas.drawLine(x1, y1, x2, y2, mPaint1);
            // branch ends
            canvas.drawCircle(x2, y2, 8f, mPaint1);
        }
        mPaint1.setStyle(Paint.Style.FILL);
    }

    // 10. Temporal Rewind Ring
    private void drawTemporalRewind(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mAnimPhase -= 0.05f; // reverse ghoomna

        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(4f);
        mPaint1.setColor((mSecondaryColor & 0x00FFFFFF) | 0x4D000000);
        canvas.drawCircle(cx, cy, 140f, mPaint1);

        // reverse rotating clock hands
        mPaint1.setColor(0xFFFFFFFF);
        mPaint1.setStrokeWidth(5f);
        float handX = cx + (float) Math.cos(mAnimPhase) * 110f;
        float handY = cy + (float) Math.sin(mAnimPhase) * 110f;
        canvas.drawLine(cx, cy, handX, handY, mPaint1);
        mPaint1.setStyle(Paint.Style.FILL);
    }

    // 11. Voltaic Storm Grid
    private void drawVoltaicStorm(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x22000000);
        mPaint1.setStrokeWidth(2f);

        // Shakey grids
        mShakeX = (mRandom.nextFloat() - 0.5f) * 4f;
        for (int y = 40; y < height; y += 50) {
            canvas.drawLine(0, y, width, y, mPaint1);
        }
        for (int x = 40; x < width; x += 50) {
            canvas.drawLine(x, 0, x, height, mPaint1);
        }

        // Random massive lightning strike
        if (mRandom.nextFloat() > 0.82f) {
            mFlashActive = true;
            mPaint2.setColor(0xFFFFFFFF);
            mPaint2.setStrokeWidth(6f);
            float sx = mRandom.nextInt(width);
            canvas.drawLine(sx, 0, sx + (mRandom.nextFloat() - 0.5f)*120f, height, mPaint2);
        }
    }

    // 12. Gravity Distortion Lens
    private void drawGravityDistortion(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(1.5f);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x33000000);

        // curved light rays
        for (int r = 50; r < 400; r += 35) {
            canvas.drawCircle(cx, cy, r, mPaint1);
        }
        mPaint1.setStyle(Paint.Style.FILL);
    }

    // 13. Prismatic Shatter
    private void drawPrismaticShatter(Canvas canvas, int width, int height) {
        mAnimPhase += 0.04f;
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFFFFF);

        // draw cracking rotating core
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(mAnimPhase * 57.29f);
        canvas.drawRect(-30f, -30f, 30f, 30f, mPaint1);
        canvas.restore();
    }

    // 14. Nebula Swirl
    private void drawNebulaSwirl(Canvas canvas, int width, int height) {
        mAnimPhase += 0.015f;
        float cx = width / 2f;
        float cy = height / 2f;

        RadialGradient rg = new RadialGradient(cx, cy, width * 0.45f,
                new int[] { 0x00000000, (mPrimaryColor & 0x00FFFFFF) | 0x22000000, 0x00000000 },
                null, Shader.TileMode.CLAMP);

        mPaint1.setShader(rg);
        canvas.drawCircle(cx, cy, width * 0.5f, mPaint1);
        mPaint1.setShader(null);

        // twinkling stars
        mPaint2.setColor(0xFFFFFFFF);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 13) p.reset(width, height, 13);
            mPaint2.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);
        }
    }

    // 15. Plasma Eruption
    private void drawPlasmaEruption(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x50000000); // glowing hot plasma

        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 14) p.reset(width, height, 14);

            // loop-shaped arcs rising and falling
            p.y -= p.vy * 1.5f;
            p.x += Math.sin(p.y * 0.05f) * 2.2f;

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            if (p.y < 0) p.reset(width, height, 14);
        }
    }

    // Particle structure definition
    private class Particle {
        float x, y, vx, vy, size, alpha;
        int mode;

        void reset(int w, int h, int activeMode) {
            mode = activeMode;
            x = mRandom.nextInt(w);
            vx = (mRandom.nextFloat() - 0.5f) * 0.8f;
            vy = 1f + mRandom.nextFloat() * 2f;
            alpha = 0.1f + mRandom.nextFloat() * 0.25f;

            switch (activeMode) {
                case 0: // Aura Explosion
                    y = mRandom.nextInt(h);
                    size = 5f + mRandom.nextFloat() * 5f;
                    break;
                case 1: // Speed Dash Trailing Lines
                    x = w + 50f + mRandom.nextInt(250);
                    y = mRandom.nextInt(h);
                    size = 70f + mRandom.nextFloat() * 120f;
                    vy = 3f + mRandom.nextFloat() * 4f;
                    break;
                case 2: // Energy Compression
                    x = mRandom.nextInt(w);
                    y = mRandom.nextInt(h);
                    size = 4f + mRandom.nextFloat() * 6f;
                    break;
                case 3: // Spirit Bomb Gathering
                    x = mRandom.nextInt(w);
                    y = h + mRandom.nextInt(200);
                    size = 6f + mRandom.nextFloat() * 10f;
                    break;
                case 4: // Bankai Glass Shards
                    y = mRandom.nextFloat() * -100f;
                    size = 12f + mRandom.nextFloat() * 16f;
                    vy = 1.5f + mRandom.nextFloat() * 2.5f;
                    break;
                case 5: // Power Up Debris
                    x = w / 2f + (mRandom.nextFloat() - 0.5f) * 140f;
                    y = h + mRandom.nextInt(100);
                    size = 8f + mRandom.nextFloat() * 12f;
                    break;
                case 6: // Dragon Fury pillars
                    y = mRandom.nextInt(h);
                    size = 4f + mRandom.nextFloat() * 6f;
                    break;
                case 7: // Void spaghettify
                    x = mRandom.nextInt(w);
                    y = mRandom.nextInt(h);
                    size = 5f + mRandom.nextFloat() * 8f;
                    break;
                case 13: // Nebula Star elements
                case 14: // Plasma
                default:
                    y = mRandom.nextInt(h);
                    size = 4f + mRandom.nextFloat() * 6f;
                    break;
            }
        }
    }
}
