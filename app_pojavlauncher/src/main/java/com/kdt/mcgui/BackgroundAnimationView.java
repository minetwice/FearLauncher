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

    private int mAnimType = 0; // 0 to 14 representing the 15 Intense/Destructive animations
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
            case 0: drawAuraBurst(canvas, width, height); break;
            case 1: drawSpeedDash(canvas, width, height); break;
            case 2: drawEnergyCompression(canvas, width, height); break;
            case 3: drawSlashStrike(canvas, width, height); break;
            case 4: drawDomainBarrier(canvas, width, height); break;
            case 5: drawPowerUpPillar(canvas, width, height); break;
            case 6: drawUltimateCharge(canvas, width, height); break;
            case 7: drawBlackHole(canvas, width, height); break;
            case 8: drawDigitalDecay(canvas, width, height); break;
            case 9: drawMeteorImpact(canvas, width, height); break;
            case 10: drawLightningStorm(canvas, width, height); break;
            case 11: drawVolcanicEruption(canvas, width, height); break;
            case 12: drawCycloneFunnel(canvas, width, height); break;
            case 13: drawNuclearDome(canvas, width, height); break;
            case 14: drawDimensionalCollapse(canvas, width, height); break;
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

    // 0. Aura Burst (Aura Blast)
    private void drawAuraBurst(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mAnimPhase += 0.05f;

        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x60000000);

        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 0) p.reset(width, height, 0);

            // Pull to center
            float dx = cx - p.x;
            float dy = cy - p.y;
            p.x += dx * 0.05f;
            p.y += dy * 0.05f;

            canvas.drawCircle(p.x + (float)Math.sin(mAnimPhase) * 6f, p.y, p.size, mPaint1);

            // Explode when close
            if (Math.abs(dx) < 25f && Math.abs(dy) < 25f) {
                p.reset(width, height, 0);
                mShakeX = (mRandom.nextFloat() - 0.5f) * 35f;
                mShakeY = (mRandom.nextFloat() - 0.5f) * 35f;
                mFlashActive = true;
            }
        }

        // Shockwave rings expanding
        mPaint2.setStyle(Paint.Style.STROKE);
        mPaint2.setStrokeWidth(5f);
        mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x90000000);
        float radius = (mAnimPhase * 18f) % (width * 0.5f);
        canvas.drawCircle(cx, cy, radius, mPaint2);
        mPaint2.setStyle(Paint.Style.FILL);
    }

    // 1. Speed Dash (Teleportation Strike)
    private void drawSpeedDash(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x3D000000);
        mPaint1.setStrokeWidth(3f);

        // trailing speed lines
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 1) p.reset(width, height, 1);

            canvas.drawLine(p.x, p.y, p.x + p.size, p.y, mPaint1);
            p.x -= p.vy * 8f; // high-speed dash left

            if (p.x < -p.size) {
                p.reset(width, height, 1);
                mShakeX = -12f;
                mShakeY = (mRandom.nextFloat() - 0.5f) * 8f;
            }
        }

        // Random horizontal glitch tear bars
        if (mRandom.nextFloat() > 0.85f) {
            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x40000000);
            float gy = mRandom.nextInt(height);
            canvas.drawRect(0, gy, width, gy + 15f, mPaint2);
        }
    }

    // 2. Energy Compression (Beam Charge)
    private void drawEnergyCompression(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        mAnimPhase += 0.08f;

        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 2) p.reset(width, height, 2);

            // Gravity pull to singular center point
            float dx = cx - p.x;
            float dy = cy - p.y;
            p.x += dx * 0.08f;
            p.y += dy * 0.08f;

            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            if (Math.abs(dx) < 15f && Math.abs(dy) < 15f) {
                p.reset(width, height, 2);
            }
        }

        // Blinding beam shoots every few seconds
        float beamPulse = (float) Math.sin(mAnimPhase) * 40f + 50f;
        if (beamPulse > 80f) {
            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0xB0000000);
            canvas.drawRect(0, cy - 25f, width, cy + 25f, mPaint2);
            mPaint3.setColor(0xFFFFFFFF);
            canvas.drawRect(0, cy - 10f, width, cy + 10f, mPaint3);
            mShakeY = (mRandom.nextFloat() - 0.5f) * 20f;
        }
    }

    // 3. Slash Strike (Sword Slash)
    private void drawSlashStrike(Canvas canvas, int width, int height) {
        mAnimPhase += 0.06f;
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFFFFF);
        mPaint1.setStrokeWidth(6f);

        // draw random jagged diagonal slash marks
        if (((int)mAnimPhase % 4) == 0) {
            mShakeX = (mRandom.nextFloat() - 0.5f) * 25f;
            mShakeY = (mRandom.nextFloat() - 0.5f) * 25f;

            float sx = mRandom.nextInt(width / 2);
            float sy = mRandom.nextInt(height / 2);
            canvas.drawLine(sx, sy, sx + width * 0.4f, sy + height * 0.4f, mPaint1);

            // slash spark sparks
            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x90000000);
            canvas.drawCircle(sx + width * 0.2f, sy + height * 0.2f, 35f, mPaint2);
        }
    }

    // 4. Domain Barrier (Domain Expansion)
    private void drawDomainBarrier(Canvas canvas, int width, int height) {
        mAnimPhase += 0.02f;
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x22000000);
        mPaint1.setStrokeWidth(2f);

        float horizon = height * 0.5f;
        // Draw geometric grid lines rising into dome
        for (float y = horizon; y < height; y += 22f) {
            canvas.drawLine(0, y, width, y, mPaint1);
        }

        float cx = width / 2f;
        for (int i = -8; i <= 8; i++) {
            canvas.drawLine(cx, horizon, cx + i * 85f, height, mPaint1);
        }

        // Rapid rotating particles inside barrier
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 4) p.reset(width, height, 4);

            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x50000000);
            p.x += Math.cos(mAnimPhase + p.vy) * 12f;
            p.y += Math.sin(mAnimPhase + p.vy) * 12f;
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);
        }
    }

    // 5. Power Up Pillar (Super Saiyan)
    private void drawPowerUpPillar(Canvas canvas, int width, int height) {
        mAnimPhase += 0.05f;
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFDD00); // Goku golden aura
        mPaint1.setAlpha(120);

        mShakeX = (mRandom.nextFloat() - 0.5f) * 6f;
        mShakeY = (mRandom.nextFloat() - 0.5f) * 6f;

        // Draw vertical columns
        float cx = width / 2f;
        canvas.drawRect(cx - 60f, 0, cx + 60f, height, mPaint1);
        mPaint1.setColor(0xFFFFFFFF);
        mPaint1.setAlpha(180);
        canvas.drawRect(cx - 20f, 0, cx + 20f, height, mPaint1);

        // Anti-gravity debris rising up
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 5) p.reset(width, height, 5);

            mPaint2.setColor(0xFFFFEE33);
            canvas.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, mPaint2);

            p.y -= p.vy * 3.5f;

            if (p.y < -p.size) {
                p.reset(width, height, 5);
            }
        }
    }

    // 6. Ultimate Charge (Time-Stop & Blast)
    private void drawUltimateCharge(Canvas canvas, int width, int height) {
        mTimeStopTimer++;
        float cx = width / 2f;
        float cy = height / 2f;

        // Timestop freezes everything at 120 frames interval
        if (mTimeStopTimer % 180 < 30) {
            // Time stop: draw black & white frozen void circles
            mPaint1.setShader(null);
            mPaint1.setColor(0x80101010);
            canvas.drawRect(0, 0, width, height, mPaint1);
            mPaint2.setColor(0xFFFFFFFF);
            mPaint2.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, 180f, mPaint2);
            mPaint2.setStyle(Paint.Style.FILL);
            return;
        }

        // Swirling vortex of lines
        mAnimPhase += 0.08f;
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
        for (int r = 50; r < 400; r += 45) {
            float px = cx + (float) Math.cos(mAnimPhase + (r * 0.01)) * r;
            float py = cy + (float) Math.sin(mAnimPhase + (r * 0.01)) * r;
            canvas.drawCircle(px, py, 8f, mPaint1);
        }

        if (mTimeStopTimer % 180 == 31) {
            mFlashActive = true; // Blast transition
        }
    }

    // 7. Black Hole (Gravity Collapse)
    private void drawBlackHole(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        // Translucent space-bending lensing rings
        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(4f);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x2E000000);
        canvas.drawCircle(cx, cy, 100f, mPaint1);
        canvas.drawCircle(cx, cy, 180f, mPaint1);
        mPaint1.setStyle(Paint.Style.FILL);

        // Core absolute black sphere
        mPaint2.setColor(0xFF03030D);
        canvas.drawCircle(cx, cy, 55f, mPaint2);

        // pull and stretch particles
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 7) p.reset(width, height, 7);

            float dx = cx - p.x;
            float dy = cy - p.y;
            p.x += dx * 0.04f;
            p.y += dy * 0.04f;

            mPaint3.setColor((mPrimaryColor & 0x00FFFFFF) | 0x40000000);
            // Spaghettify stretch
            canvas.drawRect(p.x, p.y, p.x + p.size * 2f, p.y + p.size / 2f, mPaint3);

            if (Math.abs(dx) < 55f && Math.abs(dy) < 55f) {
                p.reset(width, height, 7);
            }
        }
    }

    // 8. Digital Decay (Severe Glitch)
    private void drawDigitalDecay(Canvas canvas, int width, int height) {
        if (mRandom.nextFloat() > 0.40f) {
            mPaint1.setShader(null);
            // Tearing bar offset
            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x3D000000);
            float gy = mRandom.nextInt(height);
            canvas.drawRect(0, gy, width, gy + 35f, mPaint1);

            // Digital glitch box clusters
            mPaint2.setColor(mRandom.nextFloat() > 0.5f ? 0x4000FFFF : 0x40FF0055);
            for (int i = 0; i < 6; i++) {
                float bx = mRandom.nextInt(width);
                float by = mRandom.nextInt(height);
                canvas.drawRect(bx, by, bx + 50f, by + 50f, mPaint2);
            }
            mShakeX = (mRandom.nextFloat() - 0.5f) * 12f;
            mShakeY = (mRandom.nextFloat() - 0.5f) * 12f;
        }
    }

    // 9. Meteor Impact (Crater Explosion)
    private void drawMeteorImpact(Canvas canvas, int width, int height) {
        mAnimPhase += 0.04f;
        float mx = (mAnimPhase * 18f) % (width + 300f) - 100f;
        float my = (mAnimPhase * 10f) % (height + 200f) - 100f;

        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFF6A00); // blazing meteor tail
        canvas.drawCircle(mx, my, 22f, mPaint1);

        // Crater impact explosion trigger
        if (my > height - 40f) {
            mShakeY = -35f;
            mFlashActive = true;
            mAnimPhase = 0f; // reset loop streak
        }

        // Rising ash debris
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 9) p.reset(width, height, 9);
            mPaint2.setColor(0xFFFF9900);
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);
            p.y -= p.vy * 0.8f;
            if (p.y < 0) p.reset(width, height, 9);
        }
    }

    // 10. Lightning Storm
    private void drawLightningStorm(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFD6F6FF);
        mPaint1.setStrokeWidth(4f);

        if (mRandom.nextFloat() > 0.85f) {
            mFlashActive = true;
            mShakeX = (mRandom.nextFloat() - 0.5f) * 18f;

            float sx = mRandom.nextInt(width);
            float sy = 0;
            mPath1.reset();
            mPath1.moveTo(sx, sy);
            while (sy < height) {
                sx += (mRandom.nextFloat() - 0.5f) * 100f;
                sy += mRandom.nextFloat() * 120f;
                mPath1.lineTo(sx, sy);
            }
            mPaint1.setStyle(Paint.Style.STROKE);
            canvas.drawPath(mPath1, mPaint1);
            mPaint1.setStyle(Paint.Style.FILL);
        }
    }

    // 11. Volcanic Eruption
    private void drawVolcanicEruption(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFF4000); // Lava magma color

        // Ground magma cracks
        mPaint2.setColor(0x80FF2A00);
        canvas.drawRect(0, height - 20f, width, height, mPaint2);

        // Explosive rising magma
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 11) p.reset(width, height, 11);

            mPaint1.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.y -= p.vy * 2.8f;
            p.x += p.vx * 1.5f;

            if (p.y < 0) {
                p.reset(width, height, 11);
                mShakeY = -4f;
            }
        }
    }

    // 12. Cyclone Funnel (Tornado Vortex)
    private void drawCycloneFunnel(Canvas canvas, int width, int height) {
        mAnimPhase += 0.05f;
        float cx = width / 2f;
        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(3f);

        // Swirling wind funnel curves
        for (int y = 50; y < height; y += 40) {
            float widthRatio = (y / (float) height) * 160f + 20f;
            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x22000000);
            canvas.drawOval(cx - widthRatio, y - 10f, cx + widthRatio, y + 10f, mPaint1);
        }

        mPaint1.setStyle(Paint.Style.FILL);

        // Particles spinning in funnel
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 12) p.reset(width, height, 12);
            p.y -= p.vy * 1.5f;
            float swirlRadius = (p.y / (float) height) * 160f + 10f;
            p.x = cx + (float) Math.sin(mAnimPhase + p.vy) * swirlRadius;

            mPaint2.setColor(mPrimaryColor);
            mPaint2.setAlpha(120);
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);

            if (p.y < 0) p.reset(width, height, 12);
        }
    }

    // 13. Nuclear Dome (Shockwave push)
    private void drawNuclearDome(Canvas canvas, int width, int height) {
        mAnimPhase += 0.015f;
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(8f);
        mPaint1.setColor(0x35FFFFFF); // Transparent expanding white bubble

        float maxR = Math.max(width, height) * 0.7f;
        float radius = (mAnimPhase * maxR) % maxR;

        canvas.drawCircle(cx, cy, radius, mPaint1);
        mPaint1.setStyle(Paint.Style.FILL);

        // Push particles outward
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 13) p.reset(width, height, 13);

            float dx = p.x - cx;
            float dy = p.y - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < radius && dist > radius - 40f) {
                p.x += dx * 0.5f;
                p.y += dy * 0.5f;
                mShakeX = (mRandom.nextFloat() - 0.5f) * 4f;
            }

            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x60000000);
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);
        }
    }

    // 14. Dimensional Collapse (Space Warp Singularity)
    private void drawDimensionalCollapse(Canvas canvas, int width, int height) {
        mAnimPhase += 0.03f;
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x1A000000);
        mPaint1.setStrokeWidth(2f);

        // draw perspective lines bending to singularity
        float bendScale = (float) Math.sin(mAnimPhase) * 60f + 100f;
        for (int i = 0; i < width; i += 80) {
            canvas.drawLine(i, 0, cx + (i - cx) * (bendScale / 250f), cy, mPaint1);
            canvas.drawLine(i, height, cx + (i - cx) * (bendScale / 250f), cy, mPaint1);
        }

        // Singularity core
        mPaint2.setColor(0xFF1E0221); // deep royal void outline
        canvas.drawCircle(cx, cy, 32f, mPaint2);
        mPaint2.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, 12f, mPaint2);

        if (bendScale < 50f) {
            mShakeX = (mRandom.nextFloat() - 0.5f) * 40f;
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
                case 0: // Aura Burst
                    x = w / 2f + (mRandom.nextFloat() - 0.5f) * 450f;
                    y = h / 2f + (mRandom.nextFloat() - 0.5f) * 450f;
                    size = 4f + mRandom.nextFloat() * 6f;
                    break;
                case 1: // Speed Dash
                    x = w + 50f + mRandom.nextInt(300);
                    y = mRandom.nextInt(h);
                    size = 80f + mRandom.nextFloat() * 180f; // line length
                    vy = 3f + mRandom.nextFloat() * 5f; // speed ratio
                    break;
                case 2: // Energy Compression
                    x = mRandom.nextInt(w);
                    y = mRandom.nextInt(h);
                    size = 5f + mRandom.nextFloat() * 8f;
                    break;
                case 4: // Domain grid elements
                    y = h / 2f + mRandom.nextInt(h / 2);
                    size = 3f + mRandom.nextFloat() * 4f;
                    break;
                case 5: // Saiyan Aura Debris
                    x = (w * 0.35f) + mRandom.nextInt((int)(w * 0.3f));
                    y = h + mRandom.nextInt(100);
                    size = 12f + mRandom.nextFloat() * 22f; // box size
                    vy = 2.5f + mRandom.nextFloat() * 3.5f;
                    break;
                case 7: // Black hole stretching
                    x = mRandom.nextInt(w);
                    y = mRandom.nextInt(h);
                    size = 4f + mRandom.nextFloat() * 8f;
                    break;
                case 9: // Meteor ash
                    x = mRandom.nextInt(w);
                    y = h + mRandom.nextInt(50);
                    size = 3f + mRandom.nextFloat() * 6f;
                    vy = 1.5f + mRandom.nextFloat() * 2f;
                    break;
                case 11: // Volcanic Magma
                    x = (w / 2f) + (mRandom.nextFloat() - 0.5f) * 80f;
                    y = h - 20f;
                    size = 6f + mRandom.nextFloat() * 12f;
                    vx = (mRandom.nextFloat() - 0.5f) * 12f;
                    vy = 3f + mRandom.nextFloat() * 6f;
                    break;
                case 12: // Cyclone Vortex
                case 13: // Nuclear
                default:
                    y = mRandom.nextInt(h);
                    size = 6f + mRandom.nextFloat() * 6f;
                    break;
            }
        }
    }
}
