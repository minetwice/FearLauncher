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

        // Load sounds if they exist in res/raw (placeholders for now)
        // sClickSound = sSoundPool.load(context, R.raw.ui_click, 1);
    }

    public static void playClick() {
        if (sSoundPool != null && sClickSound != 0) {
            sSoundPool.play(sClickSound, 1, 1, 0, 0, 1);
        }
    }

    public static void startMusic(Context context) {
        // Placeholder for background music
        // sBackgroundMusic = MediaPlayer.create(context, R.raw.background_music);
        // if (sBackgroundMusic != null) {
        //     sBackgroundMusic.setLooping(true);
        //     sBackgroundMusic.start();
        // }
    }

    public static void stopMusic() {
        if (sBackgroundMusic != null) {
            sBackgroundMusic.stop();
            sBackgroundMusic.release();
            sBackgroundMusic = null;
        }
    }
}
