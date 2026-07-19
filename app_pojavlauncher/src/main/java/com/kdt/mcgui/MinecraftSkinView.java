package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinecraftSkinView extends View {
    private Bitmap mSkinBitmap;
    private boolean mIsAlex = false;
    private float mRotationY = -35f;
    private float mRotationX = -15f;
    private float mLastTouchX;
    private float mLastTouchY;

    public static final int PART_HEAD = 0;
    public static final int PART_TORSO = 1;
    public static final int PART_RIGHT_ARM = 2;
    public static final int PART_LEFT_ARM = 3;
    public static final int PART_RIGHT_LEG = 4;
    public static final int PART_LEFT_LEG = 5;

    public static final int PART_HEAD_OVERLAY = 6;
    public static final int PART_TORSO_OVERLAY = 7;
    public static final int PART_RIGHT_ARM_OVERLAY = 8;
    public static final int PART_LEFT_ARM_OVERLAY = 9;
    public static final int PART_RIGHT_LEG_OVERLAY = 10;
    public static final int PART_LEFT_LEG_OVERLAY = 11;

    private static final int FACE_FRONT = 0;
    private static final int FACE_BACK = 1;
    private static final int FACE_TOP = 2;
    private static final int FACE_BOTTOM = 3;
    private static final int FACE_LEFT = 4;
    private static final int FACE_RIGHT = 5;

    private Bitmap[][] mFaceBitmaps = new Bitmap[12][6];
    private Paint mPaint;

    public static final String DEFAULT_STEVE_BASE64 = "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";
    public static final String DEFAULT_ALEX_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAFJklEQVR4Xu2aP2sUURTF/RRir2BAsQyC4GJjaSwEg1iJqRSDgiBooSIWoggiYm0l2KiNiKCNhaWFlYVWVhYWfoAxZ8xZfnvyJtmdkN2dMBcO7++8fefc+2Zn3ps9e7awX48G1e/ng0rpjztHRlJD7a5zXqmQ43XOTMQQOSPrd7UAf18u10jCTRHiuhyvc2ZCTR5mPUVwW47XOaP3GQXON4ljMXK8zll61eRc/vn5RfXt3eOhEFwau0IAk7bXGe6CBDAkhGExcrzOmUgy7Bn+jgDh99fXI3B9jjd3lqFd8jKJu815Lgf3Yx3Hz7LyOZ+pmwmWSCbZJMd6lykCyymAr835TN048SSek1bq0E7i7M/ooLAeg7+X85m6pdecFkVYW9t/vn8arvOSCKxznoQpylzcJD0pkh/x8hpR2f4LB2rygvIyi1DyMoUkYfdT/VwIkF7mHd2EN4OFoggUIAlTpLkQwJMcPuxsRv7Dm/9YL/MvzySV2tMkTO/z93I+Uzd6ww8x+b++QYh18nlDJHGP6ehi3m1zIUDa1VdXKmJxcbHGwsJCjeyf9vDjg+r++3s1OYPEc/xEjpem8Q06I/u1tpyQiVuI7J9mAQR5ebi01kXwuOePHRxBWwFEXlGb/VpbTrBtBNy+e27kncAilMhThBwvLQXYkQjgpNpEgMjz5YjLIcffrgDbjgB5ywPefHujunjicHXt1GJ16+zxOm84MhzeBidE7/MmqQmqjktrK2gupfETTX2SZ6OJnH7M5C+fPFKTF5SnCIK95bJ+zNd7Qk+fXRr5m1S+JICuKdWRVArtPOvTIUqTZ6PJ2yIq2PMmr1R1FCD7UQBPXqlFSPIkndeZ5NMvT4ap8yyX6vLa5NloJkKiJG8xSiJZAJKxByxCkncbyyScqa5vg+TZaAxzhr8FYIQICn+WSSYnrzTJUoCSF+llgQ9khO8vCbcnz0ZbXl6uDIa4iauOfYSVlZUayjOMk0AdBWsp12aKUurPcfIJlH99FIL3GyF5NprJmJAJlgiXQELpzSREAQy2u8z6JJ4ilMhPJECJ4Orq6oY6izIYDKqlpaUaypMQiSgvUZKQ692HQrluqgIkoQzxbFfqyFB+39G9Qxw6s7Dhbm/CJqY+vIZiZKq2JJ0CcCm0EoChTpIlwu7DsgmYsAgmSYtjgdg/2wnVJ+kSeUbCxAKYLEl5CSTxFEiQp3xnF/KBJFP3cfgT9jzbk7jJ580vRUiejabne5EysVzzJi34fYBlT5Tk9M9x/fTREaiO4vCaFIJI8ikC01YR0FtvvfW20+bdIG6Jc5uMcFuO0XnL8wLXc7doVxKX2fu7luBW5tDP+nFMewqJfPdPqE+OM1NrS7633nrrrbfeeuuWeU9QaHO4qmcGb4Z0coODAgiTHq/7XUEC6FB14i2uWVseeU8aAX5n6GwEpACTRoAE8BLoRATw5NjwuSLPHQ2JUtoI1QapX5ezLTFXb5VJ0ALwcDUPWRkh3CmmALl7zH5Kcx4zMxL3wWqeMpfg43gR4nkiy1nHNOcxM+NROb8dYPjnNwXsk+/y48K/n/VG7iHkXgI5bMtSgCRrTztCEnm+n6c9JahPzmNmxuMzLoNxvi/QqZMJlU53mqA+OY+ZWekoLc8V85id/enVccgbOY+ZWRJUeZLvCzovQBLiAWuJsFJHhvK55pNoE3IeMzOGOkmWCJeQAowjwlzdA0yWpDb7viBB4rwZJmmSn6t/gc2+LxAcCULp+wKu/63I70QE/AMDdqWZ7rX6YgAAAABJRU5ErkJggg==";

    public MinecraftSkinView(Context context) {
        super(context);
        init();
    }

    public MinecraftSkinView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        // Use Nearest Neighbor/Point filtering to ensure razor-sharp pixels for low-res Minecraft skins
        mPaint.setFilterBitmap(false);
        setSkinBase64(DEFAULT_STEVE_BASE64, false);
    }

    public void setSkinBase64(String b64, boolean isAlex) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                setSkinBitmap(bmp, isAlex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadSkin(String pathOrType, boolean isAlex) {
        if ("steve".equalsIgnoreCase(pathOrType)) {
            setSkinBase64(DEFAULT_STEVE_BASE64, isAlex);
        } else if ("alex".equalsIgnoreCase(pathOrType)) {
            setSkinBase64(DEFAULT_ALEX_BASE64, isAlex);
        } else {
            try {
                java.io.File file = new java.io.File(pathOrType);
                if (file.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (bmp != null) {
                        setSkinBitmap(bmp, isAlex);
                    } else {
                        setSkinBase64(DEFAULT_STEVE_BASE64, isAlex);
                    }
                } else {
                    setSkinBase64(DEFAULT_STEVE_BASE64, isAlex);
                }
            } catch (Exception e) {
                e.printStackTrace();
                setSkinBase64(DEFAULT_STEVE_BASE64, isAlex);
            }
        }
    }

    public void setSkinBitmap(Bitmap bmp, boolean isAlex) {
        if (mSkinBitmap != null && mSkinBitmap != bmp) {
            mSkinBitmap.recycle();
        }
        mSkinBitmap = bmp;
        mIsAlex = isAlex;
        recycleFaceBitmaps();
        cropFaceBitmaps();
        invalidate();
    }

    public void setRotationAngles(float yaw, float pitch) {
        mRotationY = yaw;
        mRotationX = pitch;
        invalidate();
    }

    private void recycleFaceBitmaps() {
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 6; j++) {
                if (mFaceBitmaps[i][j] != null) {
                    mFaceBitmaps[i][j].recycle();
                    mFaceBitmaps[i][j] = null;
                }
            }
        }
    }

    private Bitmap crop(int x, int y, int w, int h, boolean mirror) {
        if (mSkinBitmap == null) return null;
        int sw = mSkinBitmap.getWidth();
        int sh = mSkinBitmap.getHeight();

        // Scale factor for HD skins
        int scale = sw / 64;
        int cx = x * scale;
        int cy = y * scale;
        int cw = w * scale;
        int ch = h * scale;

        if (cx + cw > sw || cy + ch > sh) {
            return null;
        }

        Bitmap cropped = Bitmap.createBitmap(mSkinBitmap, cx, cy, cw, ch);
        if (mirror) {
            Matrix matrix = new Matrix();
            matrix.setScale(-1, 1);
            Bitmap mirrored = Bitmap.createBitmap(cropped, 0, 0, cropped.getWidth(), cropped.getHeight(), matrix, false);
            cropped.recycle();
            return mirrored;
        }
        return cropped;
    }

    private void cropFaceBitmaps() {
        if (mSkinBitmap == null) return;
        boolean isLegacy = mSkinBitmap.getHeight() <= 32;

        // 1. Head
        mFaceBitmaps[PART_HEAD][FACE_TOP] = crop(8, 0, 8, 8, false);
        mFaceBitmaps[PART_HEAD][FACE_BOTTOM] = crop(16, 0, 8, 8, false);
        mFaceBitmaps[PART_HEAD][FACE_RIGHT] = crop(0, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD][FACE_FRONT] = crop(8, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD][FACE_LEFT] = crop(16, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD][FACE_BACK] = crop(24, 8, 8, 8, false);

        // Head Overlay
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_TOP] = crop(40, 0, 8, 8, false);
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_BOTTOM] = crop(48, 0, 8, 8, false);
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_RIGHT] = crop(32, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_FRONT] = crop(40, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_LEFT] = crop(48, 8, 8, 8, false);
        mFaceBitmaps[PART_HEAD_OVERLAY][FACE_BACK] = crop(56, 8, 8, 8, false);

        // 2. Torso
        mFaceBitmaps[PART_TORSO][FACE_TOP] = crop(20, 16, 8, 4, false);
        mFaceBitmaps[PART_TORSO][FACE_BOTTOM] = crop(28, 16, 8, 4, false);
        mFaceBitmaps[PART_TORSO][FACE_RIGHT] = crop(16, 20, 4, 12, false);
        mFaceBitmaps[PART_TORSO][FACE_FRONT] = crop(20, 20, 8, 12, false);
        mFaceBitmaps[PART_TORSO][FACE_LEFT] = crop(28, 20, 4, 12, false);
        mFaceBitmaps[PART_TORSO][FACE_BACK] = crop(32, 20, 8, 12, false);

        if (!isLegacy) {
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_TOP] = crop(20, 32, 8, 4, false);
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_BOTTOM] = crop(28, 32, 8, 4, false);
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_RIGHT] = crop(16, 36, 4, 12, false);
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_FRONT] = crop(20, 36, 8, 12, false);
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_LEFT] = crop(28, 36, 4, 12, false);
            mFaceBitmaps[PART_TORSO_OVERLAY][FACE_BACK] = crop(32, 36, 8, 12, false);
        }

        // 3. Right Arm
        int rw = mIsAlex ? 3 : 4;
        mFaceBitmaps[PART_RIGHT_ARM][FACE_TOP] = crop(44, 16, rw, 4, false);
        mFaceBitmaps[PART_RIGHT_ARM][FACE_BOTTOM] = crop(44 + rw, 16, rw, 4, false);
        mFaceBitmaps[PART_RIGHT_ARM][FACE_RIGHT] = crop(40, 20, 4, 12, false);
        mFaceBitmaps[PART_RIGHT_ARM][FACE_FRONT] = crop(40 + 4, 20, rw, 12, false);
        mFaceBitmaps[PART_RIGHT_ARM][FACE_LEFT] = crop(40 + 4 + rw, 20, 4, 12, false);
        mFaceBitmaps[PART_RIGHT_ARM][FACE_BACK] = crop(40 + 4 + rw + 4, 20, rw, 12, false);

        if (!isLegacy) {
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_TOP] = crop(44, 32, rw, 4, false);
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_BOTTOM] = crop(44 + rw, 32, rw, 4, false);
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_RIGHT] = crop(40, 36, 4, 12, false);
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_FRONT] = crop(40 + 4, 36, rw, 12, false);
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_LEFT] = crop(40 + 4 + rw, 36, 4, 12, false);
            mFaceBitmaps[PART_RIGHT_ARM_OVERLAY][FACE_BACK] = crop(40 + 4 + rw + 4, 36, rw, 12, false);
        }

        // 4. Left Arm
        if (isLegacy) {
            // Copy and mirror from Right Arm
            for (int f = 0; f < 6; f++) {
                if (mFaceBitmaps[PART_RIGHT_ARM][f] != null) {
                    mFaceBitmaps[PART_LEFT_ARM][f] = mirrorBitmap(mFaceBitmaps[PART_RIGHT_ARM][f]);
                }
            }
        } else {
            mFaceBitmaps[PART_LEFT_ARM][FACE_TOP] = crop(36, 48, rw, 4, false);
            mFaceBitmaps[PART_LEFT_ARM][FACE_BOTTOM] = crop(36 + rw, 48, rw, 4, false);
            mFaceBitmaps[PART_LEFT_ARM][FACE_RIGHT] = crop(32, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_ARM][FACE_FRONT] = crop(32 + 4, 52, rw, 12, false);
            mFaceBitmaps[PART_LEFT_ARM][FACE_LEFT] = crop(32 + 4 + rw, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_ARM][FACE_BACK] = crop(32 + 4 + rw + 4, 52, rw, 12, false);

            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_TOP] = crop(52, 48, rw, 4, false);
            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_BOTTOM] = crop(52 + rw, 48, rw, 4, false);
            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_RIGHT] = crop(48, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_FRONT] = crop(48 + 4, 52, rw, 12, false);
            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_LEFT] = crop(48 + 4 + rw, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_ARM_OVERLAY][FACE_BACK] = crop(48 + 4 + rw + 4, 52, rw, 12, false);
        }

        // 5. Right Leg
        mFaceBitmaps[PART_RIGHT_LEG][FACE_TOP] = crop(4, 16, 4, 4, false);
        mFaceBitmaps[PART_RIGHT_LEG][FACE_BOTTOM] = crop(8, 16, 4, 4, false);
        mFaceBitmaps[PART_RIGHT_LEG][FACE_RIGHT] = crop(0, 20, 4, 12, false);
        mFaceBitmaps[PART_RIGHT_LEG][FACE_FRONT] = crop(4, 20, 4, 12, false);
        mFaceBitmaps[PART_RIGHT_LEG][FACE_LEFT] = crop(8, 20, 4, 12, false);
        mFaceBitmaps[PART_RIGHT_LEG][FACE_BACK] = crop(12, 20, 4, 12, false);

        if (!isLegacy) {
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_TOP] = crop(4, 32, 4, 4, false);
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_BOTTOM] = crop(8, 32, 4, 4, false);
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_RIGHT] = crop(0, 36, 4, 12, false);
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_FRONT] = crop(4, 36, 4, 12, false);
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_LEFT] = crop(8, 36, 4, 12, false);
            mFaceBitmaps[PART_RIGHT_LEG_OVERLAY][FACE_BACK] = crop(12, 36, 4, 12, false);
        }

        // 6. Left Leg
        if (isLegacy) {
            for (int f = 0; f < 6; f++) {
                if (mFaceBitmaps[PART_RIGHT_LEG][f] != null) {
                    mFaceBitmaps[PART_LEFT_LEG][f] = mirrorBitmap(mFaceBitmaps[PART_RIGHT_LEG][f]);
                }
            }
        } else {
            mFaceBitmaps[PART_LEFT_LEG][FACE_TOP] = crop(20, 48, 4, 4, false);
            mFaceBitmaps[PART_LEFT_LEG][FACE_BOTTOM] = crop(24, 48, 4, 4, false);
            mFaceBitmaps[PART_LEFT_LEG][FACE_RIGHT] = crop(16, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG][FACE_FRONT] = crop(20, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG][FACE_LEFT] = crop(24, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG][FACE_BACK] = crop(28, 52, 4, 12, false);

            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_TOP] = crop(4, 48, 4, 4, false);
            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_BOTTOM] = crop(8, 48, 4, 4, false);
            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_RIGHT] = crop(0, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_FRONT] = crop(4, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_LEFT] = crop(8, 52, 4, 12, false);
            mFaceBitmaps[PART_LEFT_LEG_OVERLAY][FACE_BACK] = crop(12, 52, 4, 12, false);
        }
    }

    private Bitmap mirrorBitmap(Bitmap src) {
        Matrix m = new Matrix();
        m.setScale(-1, 1);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = x;
                mLastTouchY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastTouchX;
                float dy = y - mLastTouchY;
                mRotationY += dx * 0.5f;
                mRotationX -= dy * 0.5f;
                mLastTouchX = x;
                mLastTouchY = y;
                invalidate();
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSkinBitmap == null) return;

        List<Face3D> faces = new ArrayList<>();
        float armW = mIsAlex ? 3 : 4;

        // Add Base parts
        addCuboidFaces(faces, PART_HEAD, -4, -8, -4, 4, 0, 4, false);
        addCuboidFaces(faces, PART_TORSO, -4, 0, -2, 4, 12, 2, false);
        addCuboidFaces(faces, PART_RIGHT_ARM, 4, 0, -2, 4 + armW, 12, 2, false);
        addCuboidFaces(faces, PART_LEFT_ARM, -4 - armW, 0, -2, -4, 12, 2, false);
        addCuboidFaces(faces, PART_RIGHT_LEG, 0, 12, -2, 4, 24, 2, false);
        addCuboidFaces(faces, PART_LEFT_LEG, -4, 12, -2, 0, 24, 2, false);

        // Add Overlay parts if valid
        addCuboidFaces(faces, PART_HEAD_OVERLAY, -4, -8, -4, 4, 0, 4, true);
        addCuboidFaces(faces, PART_TORSO_OVERLAY, -4, 0, -2, 4, 12, 2, true);
        addCuboidFaces(faces, PART_RIGHT_ARM_OVERLAY, 4, 0, -2, 4 + armW, 12, 2, true);
        addCuboidFaces(faces, PART_LEFT_ARM_OVERLAY, -4 - armW, 0, -2, -4, 12, 2, true);
        addCuboidFaces(faces, PART_RIGHT_LEG_OVERLAY, 0, 12, -2, 4, 24, 2, true);
        addCuboidFaces(faces, PART_LEFT_LEG_OVERLAY, -4, 12, -2, 0, 24, 2, true);

        // 3D rotation and projection
        float scale = getHeight() / 32f;
        float centerX = getWidth() / 2f;
        // Shift centerY slightly upwards (higher up on screen) to make the entire body/feet perfectly visible
        float centerY = getHeight() / 2.8f;

        List<ProjectedFace> projected = new ArrayList<>();
        for (Face3D f : faces) {
            if (f.texture == null) continue;
            ProjectedFace pf = new ProjectedFace();
            pf.texture = f.texture;
            pf.avgZ = 0;
            for (int i = 0; i < 4; i++) {
                Point3D rotated = transformPoint(f.verts[i], f.partId, mRotationY, mRotationX);
                pf.verts2D[i * 2] = centerX + rotated.x * scale;
                pf.verts2D[i * 2 + 1] = centerY + rotated.y * scale;
                pf.avgZ += rotated.z;
            }
            pf.avgZ /= 4f;
            projected.add(pf);
        }

        // Sort by average Z (Painter's Algorithm)
        Collections.sort(projected, (p1, p2) -> Float.compare(p1.avgZ, p2.avgZ));

        // Render each face
        for (ProjectedFace pf : projected) {
            canvas.drawBitmapMesh(pf.texture, 1, 1, pf.verts2D, 0, null, 0, mPaint);
        }
    }

    private void addCuboidFaces(List<Face3D> faces, int partId, float bx1, float by1, float bz1, float bx2, float by2, float bz2, boolean isOverlay) {
        float inflation = isOverlay ? 0.35f : 0.0f;
        float x1 = bx1 - inflation;
        float x2 = bx2 + inflation;
        float y1 = by1 - inflation;
        float y2 = by2 + inflation;
        float z1 = bz1 - inflation;
        float z2 = bz2 + inflation;

        // FACE_FRONT
        if (mFaceBitmaps[partId][FACE_FRONT] != null) {
            faces.add(new Face3D(partId, FACE_FRONT, mFaceBitmaps[partId][FACE_FRONT], new Point3D[] {
                new Point3D(x1, y1, z2), new Point3D(x2, y1, z2),
                new Point3D(x1, y2, z2), new Point3D(x2, y2, z2)
            }));
        }
        // FACE_BACK
        if (mFaceBitmaps[partId][FACE_BACK] != null) {
            faces.add(new Face3D(partId, FACE_BACK, mFaceBitmaps[partId][FACE_BACK], new Point3D[] {
                new Point3D(x2, y1, z1), new Point3D(x1, y1, z1),
                new Point3D(x2, y2, z1), new Point3D(x1, y2, z1)
            }));
        }
        // FACE_TOP
        if (mFaceBitmaps[partId][FACE_TOP] != null) {
            faces.add(new Face3D(partId, FACE_TOP, mFaceBitmaps[partId][FACE_TOP], new Point3D[] {
                new Point3D(x1, y1, z1), new Point3D(x2, y1, z1),
                new Point3D(x1, y1, z2), new Point3D(x2, y1, z2)
            }));
        }
        // FACE_BOTTOM
        if (mFaceBitmaps[partId][FACE_BOTTOM] != null) {
            faces.add(new Face3D(partId, FACE_BOTTOM, mFaceBitmaps[partId][FACE_BOTTOM], new Point3D[] {
                new Point3D(x1, y2, z2), new Point3D(x2, y2, z2),
                new Point3D(x1, y2, z1), new Point3D(x2, y2, z1)
            }));
        }
        // FACE_LEFT
        if (mFaceBitmaps[partId][FACE_LEFT] != null) {
            // Swap Z coords to correct left-face skin texture mirroring/inversion
            faces.add(new Face3D(partId, FACE_LEFT, mFaceBitmaps[partId][FACE_LEFT], new Point3D[] {
                new Point3D(x1, y1, z2), new Point3D(x1, y1, z1),
                new Point3D(x1, y2, z2), new Point3D(x1, y2, z1)
            }));
        }
        // FACE_RIGHT
        if (mFaceBitmaps[partId][FACE_RIGHT] != null) {
            // Swap Z coords to correct right-face skin texture mirroring/inversion
            faces.add(new Face3D(partId, FACE_RIGHT, mFaceBitmaps[partId][FACE_RIGHT], new Point3D[] {
                new Point3D(x2, y1, z1), new Point3D(x2, y1, z2),
                new Point3D(x2, y2, z1), new Point3D(x2, y2, z2)
            }));
        }
    }

    private Point3D transformPoint(Point3D p, int partId, float yaw, float pitch) {
        Point3D r = new Point3D(p.x, p.y, p.z);

        // 1. Local part rotations for dynamic pose
        switch (partId) {
            case PART_HEAD:
            case PART_HEAD_OVERLAY:
                r = rotateY(r, 0, 0, 0, 8);
                r = rotateX(r, 0, 0, 0, 4);
                break;
            case PART_RIGHT_ARM:
            case PART_RIGHT_ARM_OVERLAY:
                r = rotateX(r, 6, 0, 0, -10);
                r = rotateZ(r, 6, 0, 0, 5);
                break;
            case PART_LEFT_ARM:
            case PART_LEFT_ARM_OVERLAY:
                r = rotateX(r, -6, 0, 0, 10);
                r = rotateZ(r, -6, 0, 0, -5);
                break;
            case PART_RIGHT_LEG:
            case PART_RIGHT_LEG_OVERLAY:
                r = rotateX(r, 2, 12, 0, 8);
                break;
            case PART_LEFT_LEG:
            case PART_LEFT_LEG_OVERLAY:
                r = rotateX(r, -2, 12, 0, -8);
                break;
        }

        // 2. Global rotation around model center (0, 10, 0)
        r = rotateX(r, 0, 10, 0, pitch);
        r = rotateY(r, 0, 10, 0, yaw);
        return r;
    }

    private Point3D rotateX(Point3D p, float px, float py, float pz, float angle) {
        float rad = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dy = p.y - py;
        float dz = p.z - pz;
        return new Point3D(p.x, py + dy * cos - dz * sin, pz + dy * sin + dz * cos);
    }

    private Point3D rotateY(Point3D p, float px, float py, float pz, float angle) {
        float rad = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dx = p.x - px;
        float dz = p.z - pz;
        return new Point3D(px + dx * cos + dz * sin, p.y, pz - dx * sin + dz * cos);
    }

    private Point3D rotateZ(Point3D p, float px, float py, float pz, float angle) {
        float rad = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dx = p.x - px;
        float dy = p.y - py;
        return new Point3D(px + dx * cos - dy * sin, py + dx * sin + dy * cos, p.z);
    }

    static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static class Face3D {
        int partId;
        int faceIndex;
        Bitmap texture;
        Point3D[] verts;

        Face3D(int partId, int faceIndex, Bitmap texture, Point3D[] verts) {
            this.partId = partId;
            this.faceIndex = faceIndex;
            this.texture = texture;
            this.verts = verts;
        }
    }

    static class ProjectedFace {
        Bitmap texture;
        float[] verts2D = new float[8];
        float avgZ;
    }
}
