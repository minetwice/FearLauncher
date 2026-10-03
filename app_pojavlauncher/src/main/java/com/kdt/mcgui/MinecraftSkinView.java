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
    private boolean mShowHeadOnly = false;

    /** Character poses the home screen / viewer can switch between. */
    public static final int POSE_STANDING = 0;
    public static final int POSE_T = 1;
    public static final int POSE_FLY = 2;
    private int mPose = POSE_STANDING;

    public void setPose(int pose) {
        if (pose != mPose) {
            mPose = pose;
            invalidate();
        }
    }

    public int getPose() { return mPose; }

    public void setShowHeadOnly(boolean headOnly) {
        mShowHeadOnly = headOnly;
        invalidate();
    }

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

    public static final String DEFAULT_STEVE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAFhElEQVR4Xu1a328UVRjdR6UUKJFIKL90oa4Vs1TwgRItUvlljMYSjVIDBAzUloZkg5pIDFWJUE3UpyaQiokJSWM0PPjrwcAjT/2fPufc2TN++82dGcruTrfbOcnJ3L33u7f3nPvd2dm5LZUyMLitV8Bdm59wV5bBHRt65JNj1VTa8VYcKLi6Y72jNgCEyMlDe+Wbk/ujK9hVBlD8wJY1iQaUy+UGoq4rDODq223Auq43QItFBoB6O6wKA3ziswwYqwx0jwEU/vzWta6Mu781wMeuMYDCNZkZ+ivPRztex6HSv0ZAiHquv8eJfam8QV7cGbK6s1de2B7eBypbe9yNELGogwGMQx/0xRho57g0Kuk5ws4nd2gDwD3b1zkhELhvV5+7kvh8eHCjjAxucnEwhDHogzqOow1Ie46w88kdbtKbw1ULhQfC+rHS/Nwrf89ekvvf1+Th3Izc/64WlK/Ib5+ekv27N7oYxKIP+kbbJRgTY0Nw2nOEnU/ucCu1JUjlbevCVe0PtwLK1Wf75NcvLsif16fd9feZaZmvfSj/ztZk/uM3HRGDWPRBX5QxFsbE56znCDuf3MFUDbfBWik//aTceH9Y5s8fkeHKU048MPbKrPx19aITjzIAUxCDWPRBX4yhx9RiuT30drDzyR3RZIMVC9O2T66PDcncmdfk5qkDgbij8sfVc/LPV7Xo6+3BzUvyy+QxmQme+RGDWPRBX4zB1acBPvEdYwDTHml7brQqP02+LbfPvx79oPnx9Ggg8JB8dvjlBv5w+lX58uQBF4NY9EFfjOG2QH07UHzSc4SdT+7grzeKnr943K3otx8clCvHq3L5yB6ZHh10YsHP39onX7970BFlxCD254kTru/tj94Ir/XxaCQzgKRxdj7LjvdOLIpmpVJpoI2PYXEx9jAUPRQFbaWzZ9OZhYWFcBwwKGNLvjO0O7ra8CWjFQbYR2KyJQZQfJ2r24AgA7R49zeaRSsMsKnfzi3QtAF7KzUBB54Zd9ejw3cbqNts+/DQrJSuXfuf4+NSunVLSnfuuBsmvip580Sdo44HUQdR5L17Ifk5KZ4xtp39ySxQ4OMYALo/OjEREmVOvG5CJJ4T0vGgFq3JcXQsBbIPDbXtj2NAksCsdu8EOUlMEBmhRep40E5Ykyusx7eG2XY7RhayBGa1u7TnBFDWf5wG6LqkeMZBoC5bgTqb8soAHYN9rxkJITF5LZpl1utYGmBFq/54KCNvBA9f+P2hiTodY9ut3hh8BlCczoBEA7QYmKEN8AnThnEL+PrUU91nwNzl8Ugg61tqgM2AtPaYILv63LPWAKatR7Q2RIuzBuDqM8DV12Os3hgoLElgpgF2D2YZYL8FfLEJBtgVtgb4tojVG8OjGJDWHjOAgqwofk6L91CLSzLAZsiSDBgZGREwSSDrGTc1NdXASBCuzABSr36SATqeMSwH/bQ4a4AzQRnAdr1FrN4YKCxJYFa73iK4WgNBd7NUBuibKttdDLdJQN6EkwygSF+GLJsBvgxqMCEQlhbPGF1nV7jhBhjQd49YkgEFChQoUKBAgQIFChQoUKBAgQLNounDVbwVauXhZ95o2gBz/r/6DNAZsNiGf4BoN1pqwEIb/gWm1eDLTf2WV9O+BOXbXjJ6nU7qV+m+/yewZwfLjUc1IKndidLnBDz9pRG6XZ8krSQD0tpjBmhxMCGtvSsNaPX5f7uhxfkEWgPs8bo+1PCd7fkOPjTtfHIHxfmOupZiQNbRV0cbkJbiWe3WAH2sZQ1ghvBYbEUZQNp2e7hJYVEmqNX3bRE7n9yRJTCr3aa4zwBtUscZwFPjJIGsTzpdbhB/Jr7HG7JDGdQxWyDr+Dyr3bfCOgtWhQGkE6pucK5sMqTVBvwH+QeX13iz8VkAAAAASUVORK5CYII=";
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
        mPaint.setDither(true);
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

    // Idle animation angles, driven from the fragment. The head yaws about the
    // neck and the arms lift out to the sides; both are zero at rest.
    private float mAnimHeadYaw = 0f;
    private float mAnimArmRaise = 0f;

    /** Sets the idle gesture pose. headYaw is degrees about the neck, armRaise is degrees out to the sides. */
    public void setAnimationAngles(float headYaw, float armRaise) {
        if (mAnimHeadYaw == headYaw && mAnimArmRaise == armRaise) return;
        mAnimHeadYaw = headYaw;
        mAnimArmRaise = armRaise;
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
            case MotionEvent.ACTION_UP:
                // Dragging spins the model; a tap fires performClick (there is no
                // click listener on the home model any more, so it is a no-op).
                performClick();
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
        if (!mShowHeadOnly) {
            addCuboidFaces(faces, PART_TORSO, -4, 0, -2, 4, 12, 2, false);
            addCuboidFaces(faces, PART_RIGHT_ARM, 4, 0, -2, 4 + armW, 12, 2, false);
            addCuboidFaces(faces, PART_LEFT_ARM, -4 - armW, 0, -2, -4, 12, 2, false);
            addCuboidFaces(faces, PART_RIGHT_LEG, 0, 12, -2, 4, 24, 2, false);
            addCuboidFaces(faces, PART_LEFT_LEG, -4, 12, -2, 0, 24, 2, false);
        }

        // Add Overlay parts if valid
        addCuboidFaces(faces, PART_HEAD_OVERLAY, -4, -8, -4, 4, 0, 4, true);
        if (!mShowHeadOnly) {
            addCuboidFaces(faces, PART_TORSO_OVERLAY, -4, 0, -2, 4, 12, 2, true);
            addCuboidFaces(faces, PART_RIGHT_ARM_OVERLAY, 4, 0, -2, 4 + armW, 12, 2, true);
            addCuboidFaces(faces, PART_LEFT_ARM_OVERLAY, -4 - armW, 0, -2, -4, 12, 2, true);
            addCuboidFaces(faces, PART_RIGHT_LEG_OVERLAY, 0, 12, -2, 4, 24, 2, true);
            addCuboidFaces(faces, PART_LEFT_LEG_OVERLAY, -4, 12, -2, 0, 24, 2, true);
        }

        // 3D rotation and projection
        float scale = mShowHeadOnly ? (getHeight() / 9.5f) : (getHeight() / 34f);
        float centerX = getWidth() / 2f;
        // The body spans y = -8 (top of head) .. 24 (feet) = 32 units, so its own
        // centre sits 8 units above the origin. Offset by exactly that so head and
        // feet both land inside the view instead of the legs sliding off the bottom.
        float centerY = mShowHeadOnly
                ? (getHeight() / 2f + 3.8f * scale)
                : (getHeight() / 2f - 8f * scale);

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
            // Swap Z coords to correct bottom-face skin texture mirroring/inversion
            faces.add(new Face3D(partId, FACE_BOTTOM, mFaceBitmaps[partId][FACE_BOTTOM], new Point3D[] {
                new Point3D(x1, y2, z1), new Point3D(x2, y2, z1),
                new Point3D(x1, y2, z2), new Point3D(x2, y2, z2)
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

        // 1. Local part rotations, per pose.
        switch (mPose) {
            case POSE_T:
                // Arms straight out to the sides.
                switch (partId) {
                    case PART_RIGHT_ARM:
                    case PART_RIGHT_ARM_OVERLAY:
                        r = rotateZ(r, 6, 0, 0, -90);
                        break;
                    case PART_LEFT_ARM:
                    case PART_LEFT_ARM_OVERLAY:
                        r = rotateZ(r, -6, 0, 0, 90);
                        break;
                }
                break;
            case POSE_FLY:
                // Arms tucked forward, legs together, body pitched flat.
                switch (partId) {
                    case PART_RIGHT_ARM:
                    case PART_RIGHT_ARM_OVERLAY:
                        r = rotateX(r, 6, 0, 0, -82);
                        r = rotateZ(r, 6, 0, 0, -6);
                        break;
                    case PART_LEFT_ARM:
                    case PART_LEFT_ARM_OVERLAY:
                        r = rotateX(r, -6, 0, 0, -82);
                        r = rotateZ(r, -6, 0, 0, 6);
                        break;
                    case PART_RIGHT_LEG:
                    case PART_RIGHT_LEG_OVERLAY:
                        r = rotateX(r, 2, 12, 0, 8);
                        break;
                    case PART_LEFT_LEG:
                    case PART_LEFT_LEG_OVERLAY:
                        r = rotateX(r, -2, 12, 0, 8);
                        break;
                }
                r = rotateX(r, 0, 10, 0, 78);
                break;
            case POSE_STANDING:
            default:
                // Limbs hang straight, with just enough spread to clear the torso.
                switch (partId) {
                    case PART_HEAD:
                    case PART_HEAD_OVERLAY:
                        break;
                    case PART_RIGHT_ARM:
                    case PART_RIGHT_ARM_OVERLAY:
                        r = rotateZ(r, 6, 0, 0, -3);
                        break;
                    case PART_LEFT_ARM:
                    case PART_LEFT_ARM_OVERLAY:
                        r = rotateZ(r, -6, 0, 0, 3);
                        break;
                    case PART_RIGHT_LEG:
                    case PART_RIGHT_LEG_OVERLAY:
                        r = rotateZ(r, 2, 12, 0, -1);
                        break;
                    case PART_LEFT_LEG:
                    case PART_LEFT_LEG_OVERLAY:
                        r = rotateZ(r, -2, 12, 0, 1);
                        break;
                }
                break;
        }

        // 1b. Idle gesture - only while standing, so it never fights the T or fly pose.
        if (mPose == POSE_STANDING && (mAnimHeadYaw != 0f || mAnimArmRaise != 0f)) {
            switch (partId) {
                case PART_HEAD:
                case PART_HEAD_OVERLAY:
                    r = rotateY(r, 0, 0, 0, mAnimHeadYaw);
                    break;
                case PART_RIGHT_ARM:
                case PART_RIGHT_ARM_OVERLAY:
                    r = rotateZ(r, 6, 0, 0, -mAnimArmRaise);
                    break;
                case PART_LEFT_ARM:
                case PART_LEFT_ARM_OVERLAY:
                    r = rotateZ(r, -6, 0, 0, mAnimArmRaise);
                    break;
            }
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
