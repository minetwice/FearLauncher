package net.kdt.pojavlauncher.recorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import git.artdeell.mojo.R;

public class FloatingRecorderUI {
    private WindowManager windowManager;
    private View floatingView;
    private Context context;

    public FloatingRecorderUI(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        createFloatingUI();
    }

    private void createFloatingUI() {
        floatingView = LayoutInflater.from(context).inflate(R.layout.layout_floating_recorder, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 0;
        params.y = 100;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        Button pauseButton = floatingView.findViewById(R.id.pauseButton);
        Button resumeButton = floatingView.findViewById(R.id.resumeButton);
        Button stopButton = floatingView.findViewById(R.id.stopButton);

        pauseButton.setOnClickListener(v -> sendRecorderAction("PAUSE"));
        resumeButton.setOnClickListener(v -> sendRecorderAction("RESUME"));
        stopButton.setOnClickListener(v -> sendRecorderAction("STOP"));

        windowManager.addView(floatingView, params);
    }

    private void sendRecorderAction(String action) {
        Intent intent = new Intent(context, RecorderService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    public void removeFloatingUI() {
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }
}
