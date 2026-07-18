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

    public static void playClick() {
        // Fallback standard touch click played in the View layer
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
