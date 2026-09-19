package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import git.artdeell.mojo.R;

public class RecorderOverlayView {
    private final Context mContext;
    private final RecorderService mService;
    private WindowManager mWindowManager;
    private View mOverlayView;
    private WindowManager.LayoutParams mParams;

    private ImageView mBtnPauseResume;
    private ImageView mBtnStop;
    private TextView mTvTimer;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mSecondsRecorded = 0;
    private final Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (RecorderService.isRecording() && !RecorderService.isPaused()) {
                mSecondsRecorded++;
                updateTimerText();
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    public RecorderOverlayView(Context context, RecorderService service) {
        this.mContext = context;
        this.mService = service;
    }

    public void show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(mContext)) {
                // Cannot draw over other apps without overlay permission
                return;
            }
        }

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mOverlayView = createOverlayLayout();

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 50;
        mParams.y = 100;

        try {
            mWindowManager.addView(mOverlayView, mParams);
            mHandler.post(mTimerRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hide() {
        mHandler.removeCallbacks(mTimerRunnable);
        if (mWindowManager != null && mOverlayView != null) {
            try {
                mWindowManager.removeView(mOverlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mOverlayView = null;
        }
    }

    public void onRecordingPaused() {
        if (mBtnPauseResume != null) {
            mBtnPauseResume.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    public void onRecordingResumed() {
        if (mBtnPauseResume != null) {
            mBtnPauseResume.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private View createOverlayLayout() {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int padPx = dpToPx(8);
        root.setPadding(padPx, padPx, padPx, padPx);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6121216")); // Glassy Dark Theme
        bg.setCornerRadius(dpToPx(16));
        bg.setStroke(dpToPx(1), Color.parseColor("#44FFFFFF"));
        root.setBackground(bg);

        // Recording indicator dot
        View dot = new View(mContext);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(10));
        dotParams.setMargins(dpToPx(4), 0, dpToPx(8), 0);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.RED);
        dot.setBackground(dotBg);
        root.addView(dot);

        // Timer TextView
        mTvTimer = new TextView(mContext);
        mTvTimer.setTextColor(Color.WHITE);
        mTvTimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvTimer.setText("00:00");
        mTvTimer.setPadding(0, 0, dpToPx(8), 0);
        root.addView(mTvTimer);

        // Pause/Resume Button
        mBtnPauseResume = new ImageView(mContext);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
        btnParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        mBtnPauseResume.setLayoutParams(btnParams);
        mBtnPauseResume.setImageResource(android.R.drawable.ic_media_pause);
        mBtnPauseResume.setColorFilter(Color.WHITE);
        mBtnPauseResume.setOnClickListener(v -> {
            if (RecorderService.isPaused()) {
                mService.resumeRecording();
            } else {
                mService.pauseRecording();
            }
        });
        root.addView(mBtnPauseResume);

        // Stop Button
        mBtnStop = new ImageView(mContext);
        mBtnStop.setLayoutParams(btnParams);
        mBtnStop.setImageResource(android.R.drawable.ic_delete); // Standard icon fallback
        mBtnStop.setColorFilter(Color.RED);
        mBtnStop.setOnClickListener(v -> mService.stopRecording());
        root.addView(mBtnStop);

        // Make floating overlay touch draggable
        root.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (mWindowManager != null && mOverlayView != null) {
                            mWindowManager.updateViewLayout(mOverlayView, mParams);
                        }
                        return true;
                }
                return false;
            }
        });

        return root;
    }

    private void updateTimerText() {
        if (mTvTimer != null) {
            int mins = mSecondsRecorded / 60;
            int secs = mSecondsRecorded % 60;
            mTvTimer.setText(String.format("%02d:%02d", mins, secs));
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                mContext.getResources().getDisplayMetrics()
        );
    }
}
