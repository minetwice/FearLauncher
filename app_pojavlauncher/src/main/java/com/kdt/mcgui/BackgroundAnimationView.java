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

    private int mAnimType = 0; // 0 to 19 representing the 20 animation styles (10 classic + 10 anime)
    private int mPrimaryColor = 0x00F0FF;
    private int mSecondaryColor = 0x005BFF;

    private final Paint mPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mPath1 = new Path();
    private final Path mPath2 = new Path();
    private final Path mPath3 = new Path();

    private final Random mRandom = new Random();

    // Fields for Wave Animation (Mode 0)
    private float mOffset1 = 0f;
    private float mOffset2 = 0f;
    private float mOffset3 = 0f;

    // Unified Particle structure for modes (1, 2, 3, 5, 7, 9, 10, 11, 13, 15, 16, 17)
    private static final int MAX_PARTICLES = 35;
    private final Particle[] mParticles = new Particle[MAX_PARTICLES];

    // Fields for Matrix Rain (Mode 4)
    private static final int MATRIX_COLUMNS = 25;
    private final float[] mMatrixY = new float[MATRIX_COLUMNS];
    private final String[] mMatrixChars = new String[MATRIX_COLUMNS];

    // Fields for Aurora (Mode 6) & Grid (Mode 12)
    private float mAuroraPhase = 0f;

    // Fields for Portal Vortex (Mode 8) & Rasengan (Mode 14) & Sharingan (Mode 18)
    private float mPortalAngle = 0f;

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
        for (int i = 0; i < MATRIX_COLUMNS; i++) {
            mMatrixY[i] = mRandom.nextFloat() * -500f;
            mMatrixChars[i] = String.valueOf((char) (33 + mRandom.nextInt(90)));
        }
        updatePaintStyle();
    }

    public void setAnimationType(int type) {
        mAnimType = Math.max(0, Math.min(type, 19));
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

        switch (mAnimType) {
            case 0: drawWaterWaves(canvas, width, height); break;
            case 1: drawGlassParticles(canvas, width, height); break;
            case 2: drawCosmicStarfield(canvas, width, height); break;
            case 3: drawLavaBubbles(canvas, width, height); break;
            case 4: drawMatrixRain(canvas, width, height); break;
            case 5: drawSnowfall(canvas, width, height); break;
            case 6: drawAurora(canvas, width, height); break;
            case 7: drawRainStorm(canvas, width, height); break;
            case 8: drawPortalVortex(canvas, width, height); break;
            case 9: drawFireEmbers(canvas, width, height); break;
            case 10: drawAnimeSakura(canvas, width, height); break;
            case 11: drawHinokamiFire(canvas, width, height); break;
            case 12: drawNeonGrid(canvas, width, height); break;
            case 13: drawChidoriSparks(canvas, width, height); break;
            case 14: drawRasenganChakra(canvas, width, height); break;
            case 15: drawShadowCloneSmoke(canvas, width, height); break;
            case 16: drawSuperSaiyanAura(canvas, width, height); break;
            case 17: drawAmaterasuBlackFire(canvas, width, height); break;
            case 18: drawMangekyouSharingan(canvas, width, height); break;
            case 19: drawDomainExpansionVoid(canvas, width, height); break;
        }

        // Loop animation frame smoothly (60 FPS)
        postInvalidateDelayed(16);
    }

    // 0. Flowing Water Waves
    private void drawWaterWaves(Canvas canvas, int width, int height) {
        float baseHeight = height * 0.82f;

        mPath1.reset(); mPath2.reset(); mPath3.reset();
        mPath1.moveTo(0, height); mPath2.moveTo(0, height); mPath3.moveTo(0, height);
        mPath1.lineTo(0, baseHeight); mPath2.lineTo(0, baseHeight - 15f); mPath3.lineTo(0, baseHeight + 10f);

        for (int x = 0; x <= width; x += 12) {
            float y1 = (float) (Math.sin((x * 0.005) + mOffset1) * 32f) + baseHeight;
            float y2 = (float) (Math.sin((x * 0.007) + mOffset2) * 24f) + baseHeight - 10f;
            float y3 = (float) (Math.cos((x * 0.004) + mOffset3) * 18f) + baseHeight + 15f;

            mPath1.lineTo(x, y1); mPath2.lineTo(x, y2); mPath3.lineTo(x, y3);
        }

        mPath1.lineTo(width, height); mPath2.lineTo(width, height); mPath3.lineTo(width, height);
        mPath1.close(); mPath2.close(); mPath3.close();

        mPaint3.setColor((mPrimaryColor & 0x00FFFFFF) | 0x15000000);
        mPaint2.setColor((mSecondaryColor & 0x00FFFFFF) | 0x1A000000);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x2E000000);

        canvas.drawPath(mPath3, mPaint3);
        canvas.drawPath(mPath2, mPaint2);
        canvas.drawPath(mPath1, mPaint1);

        mOffset1 += 0.022f; mOffset2 += 0.015f; mOffset3 += 0.010f;
    }

    // 1. Floating Glass Particles
    private void drawGlassParticles(Canvas canvas, int width, int height) {
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 1) p.reset(width, height, 1);

            mPaint1.setAlpha((int) (p.alpha * 255));
            mPaint1.setShader(new LinearGradient(p.x, p.y, p.x + p.size, p.y + p.size,
                    (mPrimaryColor & 0x00FFFFFF) | 0x40000000, 0x00FFFFFF, Shader.TileMode.CLAMP));

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);
            mPaint1.setShader(null);

            p.x += p.vx; p.y += p.vy;

            if (p.x < -p.size || p.x > width + p.size || p.y < -p.size || p.y > height + p.size) {
                p.reset(width, height, 1);
            }
        }
    }

    // 2. Cosmic Starfield
    private void drawCosmicStarfield(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFFFFF);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 2) p.reset(width, height, 2);

            mPaint1.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            float cx = width / 2f;
            float cy = height / 2f;
            p.x += (p.x - cx) * 0.015f + p.vx;
            p.y += (p.y - cy) * 0.015f + p.vy;
            p.size += 0.05f;

            if (p.x < 0 || p.x > width || p.y < 0 || p.y > height) {
                p.reset(width, height, 2);
            }
        }
    }

    // 3. Molten Lava Bubbles
    private void drawLavaBubbles(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 3) p.reset(width, height, 3);

            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x22000000);
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            mPaint2.setStyle(Paint.Style.STROKE);
            mPaint2.setStrokeWidth(2f);
            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
            canvas.drawCircle(p.x, p.y, p.size, mPaint2);
            mPaint2.setStyle(Paint.Style.FILL);

            p.y -= p.vy * 1.5f;
            p.x += Math.sin(p.y * 0.05f) * 0.8f;

            if (p.y < -p.size) {
                p.reset(width, height, 3);
            }
        }
    }

    // 4. Digital Matrix Rain
    private void drawMatrixRain(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setTextSize(width / (float) MATRIX_COLUMNS * 0.8f);
        mPaint1.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);

        float columnWidth = width / (float) MATRIX_COLUMNS;
        for (int i = 0; i < MATRIX_COLUMNS; i++) {
            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0xCC000000);
            canvas.drawText(mMatrixChars[i], i * columnWidth, mMatrixY[i], mPaint1);

            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x44000000);
            canvas.drawText(mMatrixChars[i], i * columnWidth, mMatrixY[i] - 30f, mPaint1);
            canvas.drawText(mMatrixChars[i], i * columnWidth, mMatrixY[i] - 60f, mPaint1);

            mMatrixY[i] += 12f;

            if (mRandom.nextFloat() > 0.95f) {
                mMatrixChars[i] = String.valueOf((char) (33 + mRandom.nextInt(90)));
            }

            if (mMatrixY[i] > height + 80f) {
                mMatrixY[i] = -100f;
            }
        }
    }

    // 5. Snowfall Blizzard
    private void drawSnowfall(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFFFFF);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 5) p.reset(width, height, 5);

            mPaint1.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.y += p.vy * 0.8f;
            p.x += Math.sin(p.y * 0.02f) * 0.6f + p.vx * 0.2f;

            if (p.y > height + p.size) {
                p.reset(width, height, 5);
            }
        }
    }

    // 6. Aurora Light Columns
    private void drawAurora(Canvas canvas, int width, int height) {
        int step = width / 6;
        for (int i = 0; i < 6; i++) {
            float x = i * step + step / 2f;
            float sway = (float) (Math.sin(mAuroraPhase + i) * 60f);

            LinearGradient lg = new LinearGradient(x + sway, 0, x + sway, height,
                    new int[] { 0x00FFFFFF, (mPrimaryColor & 0x00FFFFFF) | 0x22000000, 0x00FFFFFF },
                    null, Shader.TileMode.CLAMP);

            mPaint1.setShader(lg);
            canvas.drawRect(i * step, 0, (i + 1) * step, height, mPaint1);
        }
        mPaint1.setShader(null);
        mAuroraPhase += 0.008f;
    }

    // 7. Diagonal Rain Storm
    private void drawRainStorm(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x4D000000);
        mPaint1.setStrokeWidth(2.5f);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 7) p.reset(width, height, 7);

            canvas.drawLine(p.x, p.y, p.x - 8f, p.y + p.size, mPaint1);

            p.y += p.vy * 4f; p.x -= 4f;

            if (p.y > height || p.x < 0) {
                p.reset(width, height, 7);
            }
        }
    }

    // 8. Portal Vortex Rings
    private void drawPortalVortex(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(3f);

        float cx = width / 2f;
        float cy = height / 2f;
        float maxRadius = Math.max(width, height) * 0.6f;

        for (int r = 40; r < maxRadius; r += 60) {
            mPaint1.setColor((mSecondaryColor & 0x00FFFFFF) | 0x1A000000);
            canvas.drawCircle(cx, cy, r, mPaint1);

            mPaint2.setColor((mPrimaryColor & 0x00FFFFFF) | 0x40000000);
            float px = cx + (float) (Math.cos(mPortalAngle + (r * 0.02f)) * r);
            float py = cy + (float) (Math.sin(mPortalAngle + (r * 0.02f)) * r);
            canvas.drawCircle(px, py, 6f, mPaint2);
        }

        mPaint1.setStyle(Paint.Style.FILL);
        mPortalAngle += 0.015f;
    }

    // 9. Rising Fire Embers
    private void drawFireEmbers(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 9) p.reset(width, height, 9);

            mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x50000000);
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.y -= p.vy * 1.8f;
            p.x += Math.sin(p.y * 0.08f) * 1.2f + p.vx * 0.4f;

            if (p.y < -p.size) {
                p.reset(width, height, 9);
            }
        }
    }

    // 10. Anime Cherry Blossom (Sakura)
    private void drawAnimeSakura(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFFFFB2D1); // Soft anime Sakura pink color
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 10) p.reset(width, height, 10);

            mPaint1.setAlpha((int) (p.alpha * 255));
            // Draw rotated oval for petal feel
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.vx * 40f);
            canvas.drawOval(-p.size, -p.size / 2f, p.size, p.size / 2f, mPaint1);
            canvas.restore();

            p.y += p.vy * 0.9f;
            p.x += Math.sin(p.y * 0.03) * 0.8f + p.vx * 1.5f;

            if (p.y > height + p.size) p.reset(width, height, 10);
        }
    }

    // 11. Hinokami Fire Dance (Anime Flame Columns)
    private void drawHinokamiFire(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 11) p.reset(width, height, 11);

            // Red to deep orange core
            mPaint1.setColor(p.vx > 0 ? 0xFFFF3C00 : 0xFFFF9000);
            mPaint1.setAlpha((int) (p.alpha * 255));

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.y -= p.vy * 2.2f;
            p.x += Math.sin(p.y * 0.05f) * 1.5f;
            p.size -= 0.12f;

            if (p.y < -p.size || p.size <= 1f) p.reset(width, height, 11);
        }
    }

    // 12. Neon Grid Cyber-wave
    private void drawNeonGrid(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor((mPrimaryColor & 0x00FFFFFF) | 0x2E000000);
        mPaint1.setStrokeWidth(2.5f);

        float horizon = height * 0.5f;
        // Drawing horizontal perspective lines
        for (float y = horizon; y < height; y += (height - y) * 0.18f + 10f) {
            canvas.drawLine(0, y, width, y, mPaint1);
        }

        // Perspective vertical vanishing lines
        float cx = width / 2f;
        for (int i = -10; i <= 10; i++) {
            float xOffset = i * (width / 12f);
            canvas.drawLine(cx, horizon, cx + xOffset * 2f, height, mPaint1);
        }
    }

    // 13. Chidori Electric Sparks
    private void drawChidoriSparks(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0xFF00F0FF); // Chidori blue light
        mPaint1.setStrokeWidth(3f);

        if (mRandom.nextFloat() > 0.7f) {
            float sx = mRandom.nextInt(width);
            float sy = mRandom.nextInt(height);
            mPath1.reset();
            mPath1.moveTo(sx, sy);
            for (int i = 0; i < 4; i++) {
                sx += (mRandom.nextFloat() - 0.5f) * 120f;
                sy += (mRandom.nextFloat() - 0.5f) * 120f;
                mPath1.lineTo(sx, sy);
            }
            mPaint1.setStyle(Paint.Style.STROKE);
            canvas.drawPath(mPath1, mPaint1);
            mPaint1.setStyle(Paint.Style.FILL);
        }

        // Electric particles
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 13) p.reset(width, height, 13);
            mPaint1.setColor(0xFF80FAFF);
            mPaint1.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.x += p.vx * 3f; p.y += p.vy * 3f;
            if (p.x < 0 || p.x > width || p.y < 0 || p.y > height) p.reset(width, height, 13);
        }
    }

    // 14. Rasengan Chakra Swirl
    private void drawRasenganChakra(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        float cx = width / 2f;
        float cy = height / 2f;

        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(2f);
        mPaint1.setColor(0x2E00F0FF);
        canvas.drawCircle(cx, cy, 140f, mPaint1);

        for (int i = 0; i < 3; i++) {
            mPaint2.setColor(0x4000C8FF);
            float angle = mPortalAngle + (i * 2.09f);
            float px1 = cx + (float) (Math.cos(angle) * 140f);
            float py1 = cy + (float) (Math.sin(angle) * 140f);
            canvas.drawCircle(px1, py1, 25f, mPaint2);
        }

        mPaint1.setStyle(Paint.Style.FILL);
        mPortalAngle += 0.035f;
    }

    // 15. Shadow Clone Multi-smoke (Expanding puffs)
    private void drawShadowCloneSmoke(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        mPaint1.setColor(0x35FFFFFF); // Translucent smoke white
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 15) p.reset(width, height, 15);

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.size += 1.8f;
            p.y -= p.vy * 0.5f;
            p.alpha -= 0.006f;

            if (p.alpha <= 0.02f) p.reset(width, height, 15);
        }
    }

    // 16. Super Saiyan Aura
    private void drawSuperSaiyanAura(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 16) p.reset(width, height, 16);

            mPaint1.setColor(0xFFFFEA00); // Super Saiyan gold
            mPaint1.setAlpha((int) (p.alpha * 255));

            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            p.y -= p.vy * 3.5f;
            p.x += (mRandom.nextFloat() - 0.5f) * 12f; // Extreme flame flicker
            p.size -= 0.15f;

            if (p.y < 0 || p.size <= 1f) p.reset(width, height, 16);
        }
    }

    // 17. Amaterasu Black Flames
    private void drawAmaterasuBlackFire(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        for (Particle p : mParticles) {
            if (p.size == 0 || p.mode != 17) p.reset(width, height, 17);

            // Black core
            mPaint1.setColor(0xFF0D0D0D);
            canvas.drawCircle(p.x, p.y, p.size, mPaint1);

            // Purple glow outline
            mPaint2.setStyle(Paint.Style.STROKE);
            mPaint2.setStrokeWidth(2.5f);
            mPaint2.setColor(0xCC7F00FF);
            canvas.drawCircle(p.x, p.y, p.size + 1.5f, mPaint2);
            mPaint2.setStyle(Paint.Style.FILL);

            p.y -= p.vy * 1.5f;
            p.x += Math.cos(p.y * 0.07f) * 1.8f;

            if (p.y < -p.size) p.reset(width, height, 17);
        }
    }

    // 18. Mangekyou Sharingan Pattern
    private void drawMangekyouSharingan(Canvas canvas, int width, int height) {
        mPaint1.setShader(null);
        float cx = width / 2f;
        float cy = height / 2f;

        // Base Sharingan red orbit
        mPaint1.setStyle(Paint.Style.STROKE);
        mPaint1.setStrokeWidth(4f);
        mPaint1.setColor(0x44FF003C);
        canvas.drawCircle(cx, cy, 120f, mPaint1);

        // Drawing three spinning Sharingan tomoes
        mPaint2.setStyle(Paint.Style.FILL);
        mPaint2.setColor(0xCCFF003C);
        for (int i = 0; i < 3; i++) {
            float angle = mPortalAngle + (i * 2.094f);
            float tx = cx + (float) (Math.cos(angle) * 120f);
            float ty = cy + (float) (Math.sin(angle) * 120f);
            canvas.drawCircle(tx, ty, 15f, mPaint2);
        }

        mPaint1.setStyle(Paint.Style.FILL);
        mPortalAngle += 0.022f;
    }

    // 19. Domain Expansion Void
    private void drawDomainExpansionVoid(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        RadialGradient rg = new RadialGradient(cx, cy, width * 0.45f,
                new int[] { 0x00000000, 0x334B00FF, 0x7F0D001F },
                null, Shader.TileMode.CLAMP);

        mPaint1.setShader(rg);
        canvas.drawCircle(cx, cy, width * 0.5f, mPaint1);
        mPaint1.setShader(null);
    }

    // Unified Particle class
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
                case 1: // Glass Particle
                    y = mRandom.nextInt(h);
                    size = 80f + mRandom.nextFloat() * 200f;
                    alpha = 0.04f + mRandom.nextFloat() * 0.08f;
                    break;
                case 2: // Starfield
                    x = (w / 2f) + (mRandom.nextFloat() - 0.5f) * 100f;
                    y = (h / 2f) + (mRandom.nextFloat() - 0.5f) * 100f;
                    size = 1f + mRandom.nextFloat() * 2f;
                    break;
                case 3: // Lava Bubbles
                case 17: // Amaterasu Black Fire
                    y = h + 20f + mRandom.nextInt(100);
                    size = 12f + mRandom.nextFloat() * 20f;
                    break;
                case 5: // Snowfall
                    y = mRandom.nextFloat() * -100f;
                    size = 3f + mRandom.nextFloat() * 5f;
                    alpha = 0.3f + mRandom.nextFloat() * 0.5f;
                    break;
                case 7: // Rain Storm
                    y = mRandom.nextFloat() * -300f;
                    size = 15f + mRandom.nextFloat() * 25f;
                    alpha = 0.2f + mRandom.nextFloat() * 0.3f;
                    break;
                case 9: // Fire Embers
                    y = h + 20f + mRandom.nextInt(150);
                    size = 2f + mRandom.nextFloat() * 5f;
                    break;
                case 10: // Sakura Petals
                    y = mRandom.nextFloat() * -150f;
                    size = 8f + mRandom.nextFloat() * 12f;
                    alpha = 0.4f + mRandom.nextFloat() * 0.5f;
                    break;
                case 11: // Hinokami Fire
                case 16: // Saiyan Aura
                    x = (w * 0.1f) + mRandom.nextInt((int)(w * 0.8f));
                    y = h - mRandom.nextInt(80);
                    size = 18f + mRandom.nextFloat() * 22f;
                    break;
                case 13: // Chidori
                    y = mRandom.nextInt(h);
                    size = 2f + mRandom.nextFloat() * 4f;
                    break;
                case 15: // Smoke
                    x = (w / 2f) + (mRandom.nextFloat() - 0.5f) * 150f;
                    y = (h / 2f) + (mRandom.nextFloat() - 0.5f) * 150f;
                    size = 15f + mRandom.nextFloat() * 30f;
                    alpha = 0.25f + mRandom.nextFloat() * 0.35f;
                    break;
                default:
                    y = mRandom.nextInt(h);
                    size = 10f;
                    break;
            }
        }
    }
}
