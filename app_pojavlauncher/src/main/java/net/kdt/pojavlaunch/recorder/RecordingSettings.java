package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * The options the user picks for recording, kept in the default SharedPreferences so the
 * in-game menu, the overlay and the Dashboard all read the same values.
 *
 * Deliberately small: a quality (the short side of the output), a frame rate, a video
 * bitrate, and which audio sources to capture. Everything the encoder needs is derived
 * from these, so there is one source of truth.
 */
public final class RecordingSettings {

    public static final String PREF_QUALITY = "recorder_quality";        // 480 | 720 | 1080
    public static final String PREF_FPS = "recorder_fps";                // 30 | 60
    public static final String PREF_BITRATE_MBPS = "recorder_bitrate";   // 2 .. 40
    public static final String PREF_MIC = "recorder_mic";                // boolean
    public static final String PREF_INTERNAL_AUDIO = "recorder_internal_audio"; // boolean

    public static final int DEFAULT_QUALITY = 720;
    public static final int DEFAULT_FPS = 30;
    public static final int DEFAULT_BITRATE_MBPS = 8;

    private RecordingSettings() {}

    /** A resolved set of values, ready to hand to the encoder. */
    public static final class Config {
        public final int quality;
        public final int fps;
        public final int videoBitrate;
        public final boolean mic;
        public final boolean internalAudio;

        Config(int quality, int fps, int videoBitrate, boolean mic, boolean internalAudio) {
            this.quality = quality;
            this.fps = fps;
            this.videoBitrate = videoBitrate;
            this.mic = mic;
            this.internalAudio = internalAudio;
        }
    }

    public static Config load(Context context) {
        return new Config(
                quality(context),
                fps(context),
                bitrateMbps(context) * 1_000_000,
                isMicEnabled(context),
                isInternalAudioEnabled(context));
    }

    public static int quality(Context c) {
        int q = getInt(c, PREF_QUALITY, DEFAULT_QUALITY);
        return q <= 480 ? 480 : (q <= 720 ? 720 : 1080);
    }

    public static int fps(Context c) {
        int f = getInt(c, PREF_FPS, DEFAULT_FPS);
        return f >= 60 ? 60 : 30;
    }

    public static int bitrateMbps(Context c) {
        int b = getInt(c, PREF_BITRATE_MBPS, DEFAULT_BITRATE_MBPS);
        return Math.max(2, Math.min(40, b));
    }

    public static boolean isMicEnabled(Context c) {
        return getBool(c, PREF_MIC, true);
    }

    public static boolean isInternalAudioEnabled(Context c) {
        // Internal capture only exists from Android 10 (API 29). On older devices the
        // switch has nothing to turn on, so it reports off and the UI can hide it.
        if (android.os.Build.VERSION.SDK_INT < 29) return false;
        return getBool(c, PREF_INTERNAL_AUDIO, true);
    }

    public static void setMicEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(PREF_MIC, enabled).apply();
    }

    public static void setInternalAudioEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(PREF_INTERNAL_AUDIO, enabled).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    /**
     * Reads an int that may have been stored either as an int (our own UI) or as a string
     * (an androidx ListPreference), so a value written by either path is honoured.
     */
    private static int getInt(Context c, String key, int def) {
        SharedPreferences p = prefs(c);
        try {
            return p.getInt(key, def);
        } catch (ClassCastException e) {
            try {
                return Integer.parseInt(p.getString(key, String.valueOf(def)));
            } catch (Exception ignored) {
                return def;
            }
        }
    }

    private static boolean getBool(Context c, String key, boolean def) {
        SharedPreferences p = prefs(c);
        try {
            return p.getBoolean(key, def);
        } catch (ClassCastException e) {
            try {
                return Boolean.parseBoolean(p.getString(key, String.valueOf(def)));
            } catch (Exception ignored) {
                return def;
            }
        }
    }
}
