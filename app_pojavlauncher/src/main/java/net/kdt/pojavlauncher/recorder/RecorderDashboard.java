package net.kdt.pojavlauncher.recorder;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import git.artdeell.mojo.R;

/**
 * The Fear Recorder dashboard: audio sources, quality chips, live timer
 * and start/stop controls, styled like the rest of the launcher UI.
 */
public final class RecorderDashboard {

    private RecorderDashboard() {
    }

    public static void show(final Context context) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_recorder_dashboard);

        View backBtn = dialog.findViewById(R.id.rec_back_btn);
        View closeBtn = dialog.findViewById(R.id.rec_close_btn);
        TextView statusText = dialog.findViewById(R.id.rec_status_text);
        TextView timerText = dialog.findViewById(R.id.rec_timer_text);
        View statusDot = dialog.findViewById(R.id.rec_status_dot);
        Button deviceBtn = dialog.findViewById(R.id.rec_btn_device_audio);
        Button micBtn = dialog.findViewById(R.id.rec_btn_mic);
        Button q720 = dialog.findViewById(R.id.rec_q_720);
        Button q1080 = dialog.findViewById(R.id.rec_q_1080);
        Button qMax = dialog.findViewById(R.id.rec_q_max);
        Button fps30 = dialog.findViewById(R.id.rec_fps_30);
        Button fps60 = dialog.findViewById(R.id.rec_fps_60);
        Button br8 = dialog.findViewById(R.id.rec_br_8);
        Button br16 = dialog.findViewById(R.id.rec_br_16);
        Button br24 = dialog.findViewById(R.id.rec_br_24);
        Button startBtn = dialog.findViewById(R.id.rec_btn_start);
        Button pauseBtn = dialog.findViewById(R.id.rec_btn_pause);

        final int[] quality = {1080};
        final int[] fps = {30};
        final int[] bitrate = {16_000_000};
        final boolean[] micOn = {RecorderService.isMicEnabledStatic()};
        final boolean[] deviceOn = {RecorderService.isDeviceAudioEnabledStatic()
                && Build.VERSION.SDK_INT >= 29};

        View.OnClickListener back = v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            dialog.dismiss();
        };
        backBtn.setOnClickListener(back);
        closeBtn.setOnClickListener(back);

        final Runnable[] updater = new Runnable[1];
        updater[0] = () -> {
            int state = RecorderService.getState();
            boolean mic = RecorderService.isMicEnabledStatic();
            boolean device = RecorderService.isDeviceAudioEnabledStatic();
            micOn[0] = mic;
            deviceOn[0] = device && Build.VERSION.SDK_INT >= 29;

            if (state == RecorderService.STATE_RECORDING) {
                statusText.setText("RECORDING");
                statusText.setTextColor(0xFFFF1744);
            } else if (state == RecorderService.STATE_PAUSED) {
                statusText.setText("PAUSED");
                statusText.setTextColor(0xFFFFC107);
            } else {
                statusText.setText("READY TO RECORD");
                statusText.setTextColor(0xFFFFFFFF);
            }
            if (statusDot.getBackground() != null) {
                statusDot.getBackground().setTint(state == RecorderService.STATE_PAUSED
                        ? 0xFF9E9E9E : 0xFFFF003C);
            }

            startBtn.setText(state == RecorderService.STATE_IDLE ? "START RECORDING" : "STOP RECORDING");
            pauseBtn.setVisibility(state == RecorderService.STATE_IDLE ? View.GONE : View.VISIBLE);
            pauseBtn.setText(state == RecorderService.STATE_PAUSED ? "RESUME" : "PAUSE");

            micBtn.setBackgroundResource(mic ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            deviceBtn.setBackgroundResource(device ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);

            int q = quality[0];
            q720.setBackgroundResource(q == 720 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            q1080.setBackgroundResource(q == 1080 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            qMax.setBackgroundResource(q == 0 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            int f = fps[0];
            fps30.setBackgroundResource(f == 30 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            fps60.setBackgroundResource(f == 60 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            int b = bitrate[0];
            br8.setBackgroundResource(b == 8_000_000 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            br16.setBackgroundResource(b == 16_000_000 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            br24.setBackgroundResource(b == 24_000_000 ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        };

        View.OnClickListener chipClick = v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
        };
        q720.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 720; updater[0].run(); });
        q1080.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 1080; updater[0].run(); });
        qMax.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 0; updater[0].run(); });
        fps30.setOnClickListener(v -> { chipClick.onClick(v); fps[0] = 30; updater[0].run(); });
        fps60.setOnClickListener(v -> { chipClick.onClick(v); fps[0] = 60; updater[0].run(); });
        br8.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 8_000_000; updater[0].run(); });
        br16.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 16_000_000; updater[0].run(); });
        br24.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 24_000_000; updater[0].run(); });

        micBtn.setOnClickListener(v -> {
            chipClick.onClick(v);
            if (RecorderService.getState() == RecorderService.STATE_IDLE) {
                micOn[0] = !micOn[0];
                micBtn.setBackgroundResource(micOn[0] ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            } else {
                sendAction(context, RecorderService.isMicEnabledStatic()
                        ? RecorderService.ACTION_MIC_OFF : RecorderService.ACTION_MIC_ON);
            }
        });

        deviceBtn.setOnClickListener(v -> {
            chipClick.onClick(v);
            if (Build.VERSION.SDK_INT < 29) {
                Toast.makeText(context, "Device audio capture needs Android 10+", Toast.LENGTH_SHORT).show();
                return;
            }
            if (RecorderService.getState() == RecorderService.STATE_IDLE) {
                deviceOn[0] = !deviceOn[0];
                deviceBtn.setBackgroundResource(deviceOn[0] ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            } else {
                sendAction(context, RecorderService.isDeviceAudioEnabledStatic()
                        ? RecorderService.ACTION_DEVICE_OFF : RecorderService.ACTION_DEVICE_ON);
            }
        });

        pauseBtn.setOnClickListener(v -> {
            chipClick.onClick(v);
            if (RecorderService.getState() == RecorderService.STATE_PAUSED) {
                sendAction(context, RecorderService.ACTION_RESUME);
            } else {
                sendAction(context, RecorderService.ACTION_PAUSE);
            }
        });

        startBtn.setOnClickListener(v -> {
            chipClick.onClick(v);
            if (RecorderService.getState() != RecorderService.STATE_IDLE) {
                sendAction(context, RecorderService.ACTION_STOP);
                return;
            }
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                new AlertDialog.Builder(context)
                        .setTitle("Floating Controls")
                        .setMessage("Allow 'Display over other apps' for Fear Launcher so the recorder bubble stays visible while you play. Recording also works without it — controls remain in the notification bar.")
                        .setPositiveButton("Open Settings", (d, w) ->
                                context.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:" + context.getPackageName()))))
                        .setNegativeButton("Skip", (d, w) ->
                                launchPermission(context, micOn[0], deviceOn[0], quality[0], fps[0], bitrate[0]))
                        .show();
                return;
            }
            launchPermission(context, micOn[0], deviceOn[0], quality[0], fps[0], bitrate[0]);
        });

        Handler timerHandler = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                long ms = RecorderService.getRecordedMs();
                timerText.setText(String.format(java.util.Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60));
                timerHandler.postDelayed(this, 500);
            }
        };

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                updater[0].run();
            }
        };
        IntentFilter stateFilter = new IntentFilter(RecorderService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, stateFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, stateFilter);
        }

        dialog.setOnDismissListener(d -> {
            timerHandler.removeCallbacks(tick);
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        });

        dialog.show();
        updater[0].run();
        timerHandler.post(tick);
    }

    private static void sendAction(Context context, String action) {
        Intent svc = new Intent(context, RecorderService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            try {
                context.startService(svc);
            } catch (Exception ignored) {
            }
        }
    }

    private static void launchPermission(Context context, boolean mic, boolean device,
                                         int qualitySel, int fpsSel, int bitrateSel) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = Math.max(dm.widthPixels, 1);
        int screenH = Math.max(dm.heightPixels, 1);
        double scale = (qualitySel == 0) ? 1.0d
                : Math.min(1.0d, (double) qualitySel / Math.max(screenW, screenH));
        int w = ((int) (screenW * scale)) & ~1;
        int h = ((int) (screenH * scale)) & ~1;
        if (w < 2) w = 2;
        if (h < 2) h = 2;
        Intent intent = new Intent(context, RecorderPermissionActivity.class);
        intent.putExtra("mic", mic);
        intent.putExtra("device", device);
        intent.putExtra("width", w);
        intent.putExtra("height", h);
        intent.putExtra("fps", fpsSel);
        intent.putExtra("bitrate", bitrateSel);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
