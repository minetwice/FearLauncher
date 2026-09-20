package net.kdt.pojavlauncher.recorder;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import git.artdeell.mojo.R;

/**
 * The Fear Recorder dashboard: audio source selection, quality chips, live
 * timer and start/stop controls, styled like the rest of the launcher UI.
 */
public final class RecorderDashboard {

    private RecorderDashboard() {
    }

    public static void show(final Context context) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_recorder_dashboard);

        View backBtn = dialog.findViewById(R.id.rec_back_btn);
        View closeBtn = dialog.findViewById(R.id.rec_close_btn);
        final Button[] filters = {
                dialog.findViewById(R.id.rec_filter_quality),
                dialog.findViewById(R.id.rec_filter_fps),
                dialog.findViewById(R.id.rec_filter_audio),
                dialog.findViewById(R.id.rec_filter_tune)};
        final View[] trays = {
                dialog.findViewById(R.id.rec_tray_quality),
                dialog.findViewById(R.id.rec_tray_fps),
                dialog.findViewById(R.id.rec_tray_audio),
                dialog.findViewById(R.id.rec_tray_tune)};
        TextView statusText = dialog.findViewById(R.id.rec_status_text);
        TextView timerText = dialog.findViewById(R.id.rec_timer_text);
        View statusDot = dialog.findViewById(R.id.rec_status_dot);
        Button selBoth = dialog.findViewById(R.id.rec_sel_both);
        Button selMic = dialog.findViewById(R.id.rec_sel_mic);
        Button selDevice = dialog.findViewById(R.id.rec_sel_device);
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
            startBtn.setCompoundDrawablesWithIntrinsicBounds(state == RecorderService.STATE_IDLE
                    ? R.drawable.rec_ic_play : R.drawable.rec_ic_stop, 0, 0, 0);
            pauseBtn.setVisibility(state == RecorderService.STATE_IDLE ? View.GONE : View.VISIBLE);
            pauseBtn.setText(state == RecorderService.STATE_PAUSED ? "RESUME" : "PAUSE");
            pauseBtn.setCompoundDrawablesWithIntrinsicBounds(state == RecorderService.STATE_PAUSED
                    ? R.drawable.rec_ic_play : R.drawable.rec_ic_pause, 0, 0, 0);

            boolean devOk = device && Build.VERSION.SDK_INT >= 29;
            selBoth.setBackgroundResource(mic && devOk
                    ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            selMic.setBackgroundResource(mic && !devOk
                    ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
            selDevice.setBackgroundResource(devOk && !mic
                    ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);

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

        // filter bar: tap a chip, its tray slides open below it; tap again to fold it
        for (int i = 0; i < filters.length; i++) {
            final int idx = i;
            filters[i].setOnClickListener(v -> {
                chipClick.onClick(v);
                boolean opening = trays[idx].getVisibility() != View.VISIBLE;
                for (int j = 0; j < filters.length; j++) {
                    if (j == idx) {
                        if (opening) expandTray(context, trays[j]);
                        else collapseTray(context, trays[j]);
                    } else {
                        collapseTray(context, trays[j]);
                    }
                    filters[j].setBackgroundResource(j == idx && opening
                            ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
                }
            });
        }
        q720.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 720; updater[0].run(); });
        q1080.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 1080; updater[0].run(); });
        qMax.setOnClickListener(v -> { chipClick.onClick(v); quality[0] = 0; updater[0].run(); });
        fps30.setOnClickListener(v -> { chipClick.onClick(v); fps[0] = 30; updater[0].run(); });
        fps60.setOnClickListener(v -> { chipClick.onClick(v); fps[0] = 60; updater[0].run(); });
        br8.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 8_000_000; updater[0].run(); });
        br16.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 16_000_000; updater[0].run(); });
        br24.setOnClickListener(v -> { chipClick.onClick(v); bitrate[0] = 24_000_000; updater[0].run(); });

        // audio source selection: BOTH / MIC ONLY / DEVICE ONLY
        View.OnClickListener audioSelection = v -> {
            chipClick.onClick(v);
            boolean mic;
            boolean device;
            if (v == selMic) {
                mic = true;
                device = false;
            } else if (v == selDevice) {
                mic = false;
                device = true;
            } else {
                mic = true;
                device = true;
            }
            if (device && Build.VERSION.SDK_INT < 29) {
                Toast.makeText(context, "Device audio capture needs Android 10+", Toast.LENGTH_SHORT).show();
                device = false;
            }
            if (RecorderService.getState() == RecorderService.STATE_IDLE) {
                micOn[0] = mic;
                deviceOn[0] = device;
            } else {
                if (RecorderService.isMicEnabledStatic() != mic)
                    sendAction(context, mic ? RecorderService.ACTION_MIC_ON : RecorderService.ACTION_MIC_OFF);
                if (RecorderService.isDeviceAudioEnabledStatic() != device)
                    sendAction(context, device ? RecorderService.ACTION_DEVICE_ON : RecorderService.ACTION_DEVICE_OFF);
            }
            updater[0].run();
        };
        selBoth.setOnClickListener(audioSelection);
        selMic.setOnClickListener(audioSelection);
        selDevice.setOnClickListener(audioSelection);

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

        // ---- my recordings ----
        LinearLayout recordingsList = dialog.findViewById(R.id.rec_recordings_list);
        TextView recordingsEmpty = dialog.findViewById(R.id.rec_recordings_empty);
        if (recordingsList != null) {
            populateRecordings(context, recordingsList, recordingsEmpty);
        }

        dialog.show();
        updater[0].run();
        timerHandler.post(tick);
    }

    private static void populateRecordings(final Context context, LinearLayout list, TextView empty) {
        list.removeAllViews();
        java.util.List<RecordingStore.Entry> recordings = RecordingStore.list(context);
        if (recordings.isEmpty()) {
            if (empty != null) empty.setVisibility(View.VISIBLE);
            return;
        }
        if (empty != null) empty.setVisibility(View.GONE);
        for (final RecordingStore.Entry entry : recordings) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(context, 12), dp(context, 10), dp(context, 8), dp(context, 10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x1AFFFFFF);
            bg.setCornerRadius(dp(context, 10));
            bg.setStroke(1, 0x33FF003C);
            item.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(context, 8);
            item.setLayoutParams(lp);

            LinearLayout infoBox = new LinearLayout(context);
            infoBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoBox.setLayoutParams(infoLp);

            TextView name = new TextView(context);
            name.setText(entry.name);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(11);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            infoBox.addView(name);

            TextView info = new TextView(context);
            info.setText(formatEntryInfo(entry));
            info.setTextColor(0x80FFFFFF);
            info.setTextSize(9);
            infoBox.addView(info);
            item.addView(infoBox);

            Button play = makeListButton(context, "PLAY", R.drawable.rec_ic_play);
            play.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                RecordingPlayerDialog.show(context, entry);
            });
            item.addView(play);

            Button export = makeListButton(context, "EXPORT", R.drawable.rec_ic_export);
            export.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                ExportBoardDialog.show(context, entry);
            });
            item.addView(export);

            list.addView(item);
        }
    }

    private static void expandTray(Context context, View tray) {
        if (tray.getVisibility() == View.VISIBLE) return;
        tray.setAlpha(0f);
        tray.setTranslationY(-dp(context, 12));
        tray.setVisibility(View.VISIBLE);
        tray.animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    private static void collapseTray(Context context, View tray) {
        if (tray.getVisibility() != View.VISIBLE) return;
        tray.animate().alpha(0f).translationY(-dp(context, 12)).setDuration(160)
                .withEndAction(() -> tray.setVisibility(View.GONE)).start();
    }

    private static Button makeListButton(Context context, String label, int iconRes) {
        Button b = new Button(context);
        b.setText(label);
        b.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        b.setTextColor(0xFFFF003C);
        b.setTextSize(9);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FF003C);
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(1, 0x66FF003C);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 34));
        lp.leftMargin = dp(context, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private static String formatEntryInfo(RecordingStore.Entry e) {
        String dur = e.durationMs > 0
                ? String.format(java.util.Locale.US, "%02d:%02d · ",
                        e.durationMs / 60000, (e.durationMs / 1000) % 60) : "";
        return dur + String.format(java.util.Locale.US, "%.1f MB", e.sizeBytes / (1024.0 * 1024.0));
    }

    private static int dp(Context context, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                context.getResources().getDisplayMetrics()));
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
