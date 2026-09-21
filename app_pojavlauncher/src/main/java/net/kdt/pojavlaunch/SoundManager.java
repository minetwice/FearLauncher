package net.kdt.pojavlaunch;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import git.artdeell.mojo.R;

public class SoundManager {
    private static SoundPool sSoundPool;
    private static int sClickSound;
    private static MediaPlayer sBackgroundMusic;

    public static void init(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        sSoundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();
    }

    private static android.media.ToneGenerator sTone;

    public static void playClick() {
        try {
            if (sTone == null) sTone = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 30);
            sTone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 60);
        } catch (Throwable ignored) {}
    }

    public static void playShine() {
        try {
            if (sTone == null) sTone = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 50);
            sTone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 160);
        } catch (Throwable ignored) {}
    }

    public static void startMusic(Context context) {
        // Background theme placeholder
    }

    public static void stopMusic() {
        if (sBackgroundMusic != null) {
            sBackgroundMusic.stop();
            sBackgroundMusic.release();
            sBackgroundMusic = null;
        }
    }
}
