package net.kdt.pojavlauncher.recorder;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import git.artdeell.mojo.R;

/**
 * In-dashboard video player: plays a recording full-screen with
 * play/pause, a real STOP button, a seek bar and a time readout.
 */
public final class RecordingPlayerDialog {

    private RecordingPlayerDialog() {
    }

    public static void show(final Context context, final RecordingStore.Entry entry) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_recorder_player);

        final VideoView videoView = dialog.findViewById(R.id.rec_player_video);
        Button playPause = dialog.findViewById(R.id.rec_player_play);
        Button stop = dialog.findViewById(R.id.rec_player_stop);
        Button close = dialog.findViewById(R.id.rec_player_close);
        final SeekBar seek = dialog.findViewById(R.id.rec_player_seek);
        final TextView time = dialog.findViewById(R.id.rec_player_time);

        if (entry.uri != null) videoView.setVideoURI(entry.uri);
        else if (entry.path != null) videoView.setVideoPath(entry.path);

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                try {
                    if (videoView.isPlaying()) {
                        int pos = videoView.getCurrentPosition();
                        int dur = videoView.getDuration();
                        if (dur > 0) seek.setProgress(pos * 1000 / dur);
                        time.setText(fmt(pos) + " / " + fmt(dur));
                    }
                } catch (Exception ignored) {
                }
                handler.postDelayed(this, 500);
            }
        };

        videoView.setOnPreparedListener(mp -> {
            try {
                mp.setLooping(false);
                videoView.start();
                playPause.setText("PAUSE");
            } catch (Exception ignored) {
            }
        });
        videoView.setOnCompletionListener(mp -> playPause.setText("PLAY"));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    try {
                        int dur = videoView.getDuration();
                        if (dur > 0) videoView.seekTo(p * dur / 1000);
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        playPause.setOnClickListener(v -> {
            try {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    playPause.setText("PLAY");
                } else {
                    videoView.start();
                    playPause.setText("PAUSE");
                }
            } catch (Exception ignored) {
            }
        });

        stop.setOnClickListener(v -> {
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
            }
            playPause.setText("PLAY");
            time.setText("STOPPED — close and reopen to play again");
        });

        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(tick);
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
            }
        });

        dialog.show();
        handler.post(tick);
    }

    private static String fmt(int ms) {
        return String.format(java.util.Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60);
    }
}
