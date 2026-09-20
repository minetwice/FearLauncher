package net.kdt.pojavlauncher.recorder;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import git.artdeell.mojo.R;

/**
 * Compact floating recorder bubble shown while recording.
 * - Draggable pill pinned to the screen edge.
 * - Tap to expand a glass panel with mic / pause-resume / stop controls.
 * - Auto-collapses after a few seconds.
 */
public final class FloatingRecorderUI {

    private static final int BUBBLE_DP = 52;
    private static final long PANEL_HIDE_DELAY = 5000L;

    private static FloatingRecorderUI sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final LinearLayout mRoot;
    private final LinearLayout mPanel;
    private final FrameLayout mBubble;
    private final View mStatusDot;
    private final ImageButton mMicBtn;
    private final ImageButton mPauseBtn;
    private final ImageButton mStopBtn;
    private final WindowManager.LayoutParams mParams;
    private ObjectAnimator mPulse;
    private boolean mAdded = false;
    private boolean mPanelVisible = false;

    private final Runnable mHidePanel = this::collapsePanel;

    public static void attach(Context context) {
        detach();
        try {
            sInstance = new FloatingRecorderUI(context.getApplicationContext());
            sInstance.show();
        } catch (Exception e) {
            android.util.Log.w("FearRecorder", "Floating UI unavailable (overlay permission?)", e);
            sInstance = null;
        }
    }

    public static void detach() {
        FloatingRecorderUI instance = sInstance;
        sInstance = null;
        if (instance != null) instance.remove();
    }

    public static void notifyState(Context context) {
        FloatingRecorderUI instance = sInstance;
        if (instance != null) {
            instance.applyState();
        } else {
            // UI was dismissed or never attached; reattach if recording continues
            int state = RecorderService.getState();
            if (state != RecorderService.STATE_IDLE) {
                attach(context);
            }
        }
    }

    private FloatingRecorderUI(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int bubble = dp(BUBBLE_DP);

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 0;
        mParams.y = 400;

        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setGravity(Gravity.END);
        LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mRoot.setLayoutParams(rootLp);

        // ---- expandable control panel ----
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.HORIZONTAL);
        mPanel.setGravity(Gravity.CENTER_VERTICAL);
        mPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xEE0F0204);
        panelBg.setCornerRadius(dp(18));
        panelBg.setStroke(dp(1), 0x66FF003C);
        mPanel.setBackground(panelBg);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.bottomMargin = dp(10);
        mPanel.setLayoutParams(panelLp);
        mPanel.setVisibility(View.GONE);

        mMicBtn = makePanelButton();
        mPauseBtn = makePanelButton();
        mStopBtn = makePanelButton();
        mPanel.addView(mMicBtn);
        mPanel.addView(mPauseBtn);
        mPanel.addView(mStopBtn);
        mRoot.addView(mPanel);

        // ---- bubble ----
        mBubble = new FrameLayout(context);
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(bubble, bubble);
        mBubble.setLayoutParams(bubbleLp);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(0xE6140208);
        bubbleBg.setStroke(dp(2), 0xFFFF003C);
        mBubble.setBackground(bubbleBg);

        ImageView cam = new ImageView(context);
        cam.setImageResource(R.drawable.ic_rec_video);
        cam.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cam.setPadding(dp(6), dp(6), dp(6), dp(6));
        cam.setColorFilter(0xFFFF003C);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cam.setLayoutParams(camLp);
        mBubble.addView(cam);

        mStatusDot = new View(context);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(12), dp(12));
        dotLp.gravity = Gravity.BOTTOM | Gravity.END;
        mStatusDot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.RED);
        mStatusDot.setBackground(dotBg);
        mBubble.addView(mStatusDot);
        mRoot.addView(mBubble);

        setupDrag();

        mMicBtn.setOnClickListener(v -> {
            boolean on = RecorderService.isMicEnabledStatic();
            send(on ? RecorderService.ACTION_MIC_OFF : RecorderService.ACTION_MIC_ON);
        });
        mPauseBtn.setOnClickListener(v -> {
            if (RecorderService.getState() == RecorderService.STATE_PAUSED) {
                send(RecorderService.ACTION_RESUME);
            } else {
                send(RecorderService.ACTION_PAUSE);
            }
        });
        mStopBtn.setOnClickListener(v -> send(RecorderService.ACTION_STOP));
    }

    private ImageButton makePanelButton() {
        ImageButton button = new ImageButton(mContext);
        int size = dp(44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(lp);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x26FFFFFF);
        button.setBackground(bg);
        return button;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDrag() {
        mBubble.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startParamsX, startParamsY;
            private boolean dragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startParamsX = mParams.x;
                        startParamsY = mParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (dragging || Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) {
                            dragging = true;
                            mParams.x = startParamsX + (int) dx;
                            mParams.y = Math.max(0, startParamsY + (int) dy);
                            try {
                                mWindowManager.updateViewLayout(mRoot, mParams);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            togglePanel();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void togglePanel() {
        if (mPanelVisible) {
            collapsePanel();
        } else {
            expandPanel();
        }
    }

    private void expandPanel() {
        mPanelVisible = true;
        mPanel.setVisibility(View.VISIBLE);
        mPanel.setAlpha(0f);
        mPanel.setScaleX(0.7f);
        mPanel.setScaleY(0.7f);
        mPanel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        applyState();
        // keep the expanded panel inside the screen
        mRoot.post(() -> {
            try {
                int maxX = getScreenWidth() - mRoot.getWidth() - dp(4);
                if (mParams.x > maxX) {
                    mParams.x = Math.max(0, maxX);
                    mWindowManager.updateViewLayout(mRoot, mParams);
                }
            } catch (Exception ignored) {
            }
        });
        scheduleHide();
    }

    private void collapsePanel() {
        mPanelVisible = false;
        mHandler.removeCallbacks(mHidePanel);
        mPanel.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(140)
                .withEndAction(() -> mPanel.setVisibility(View.GONE)).start();
    }

    private void scheduleHide() {
        mHandler.removeCallbacks(mHidePanel);
        mHandler.postDelayed(mHidePanel, PANEL_HIDE_DELAY);
    }

    private void applyState() {
        int state = RecorderService.getState();
        boolean micOn = RecorderService.isMicEnabledStatic();

        // status dot + pulse
        if (mPulse != null) mPulse.cancel();
        GradientDrawable dot = (GradientDrawable) mStatusDot.getBackground();
        if (state == RecorderService.STATE_RECORDING) {
            dot.setColor(0xFFFF1744);
            mPulse = ObjectAnimator.ofFloat(mStatusDot, View.ALPHA, 1f, 0.25f);
            mPulse.setDuration(650);
            mPulse.setRepeatCount(ObjectAnimator.INFINITE);
            mPulse.setRepeatMode(ObjectAnimator.REVERSE);
            mPulse.start();
        } else {
            dot.setColor(0xFF9E9E9E);
            mStatusDot.setAlpha(1f);
        }

        mMicBtn.setImageResource(micOn ? R.drawable.ic_rec_mic_on : R.drawable.ic_rec_mic_off);
        mMicBtn.setColorFilter(micOn ? 0xFFFF003C : 0xFF8A8A8A);
        mPauseBtn.setImageResource(state == RecorderService.STATE_PAUSED
                ? R.drawable.ic_rec_play : R.drawable.ic_rec_pause);
        mPauseBtn.setColorFilter(0xFFFFFFFF);
        mStopBtn.setImageResource(R.drawable.ic_rec_stop);
        mStopBtn.setColorFilter(0xFFFF1744);
    }

    private void send(String action) {
        Intent intent = new Intent(mContext, RecorderService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                mContext.startForegroundService(intent);
            } else {
                mContext.startService(intent);
            }
        } catch (Exception e) {
            // service is already a running foreground service; plain start as fallback
            try {
                mContext.startService(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private int getScreenWidth() {
        if (Build.VERSION.SDK_INT >= 30) {
            return mWindowManager.getCurrentWindowMetrics().getBounds().width();
        }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }

    private void show() {
        if (mAdded) return;
        // default position: right edge, vertically centered
        try {
            mParams.x = getScreenWidth() - dp(BUBBLE_DP) - dp(6);
            int height;
            if (Build.VERSION.SDK_INT >= 30) {
                height = mWindowManager.getCurrentWindowMetrics().getBounds().height();
            } else {
                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                mWindowManager.getDefaultDisplay().getMetrics(dm);
                height = dm.heightPixels;
            }
            mParams.y = height / 2 - dp(BUBBLE_DP);
            if (mParams.y < 0) mParams.y = 100;
        } catch (Exception ignored) {
        }
        mWindowManager.addView(mRoot, mParams);
        mAdded = true;
        applyState();
    }

    private void remove() {
        mHandler.removeCallbacksAndMessages(null);
        if (mPulse != null) mPulse.cancel();
        if (mAdded) {
            try {
                mWindowManager.removeView(mRoot);
            } catch (Exception ignored) {
            }
            mAdded = false;
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, mContext.getResources().getDisplayMetrics()));
    }
}
