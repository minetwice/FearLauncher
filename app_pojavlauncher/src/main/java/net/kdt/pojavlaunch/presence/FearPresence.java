package net.kdt.pojavlaunch.presence;

import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Fear Launcher's Discord presence.
 *
 * <p>Discord renders an activity as three lines:
 * <pre>
 *   Playing "Fear Launcher"     <- the application's name, set on Discord's side
 *   For monsters                <- the activity's details
 *   &lt;what is being played&gt;      <- the activity's state
 * </pre>
 * The launcher sets the second and third; the first comes from the registered
 * application, so it is whatever the application is named on Discord.
 *
 * <p>This takes the official route. Discord's Social SDK supports Rich Presence on
 * Android, so the launcher registers as an application and talks to the Discord app
 * installed on the phone. It deliberately does not use the user-token gateway approach
 * that several Android presence apps take - that asks the user for their Discord token
 * and works against Discord's terms, and that is not something to put in front of
 * people who trust the launcher with their game.
 *
 * <p>The publishing call itself is native: the SDK exposes presence in C, not in Java, so
 * the values assembled here are handed to fear_presence.c, which owns the Discord client.
 * Nothing here reaches Discord unless the SDK was linked into the build and the client
 * starts; when either fails it logs and presence stays off, because a social feature must
 * never be a reason a launch does not happen.
 */
public final class FearPresence {

    private static final String TAG = "FearPresence";

    /**
     * Application id from Discord's developer portal. This is a public identifier - it
     * is not a secret, and no secret or token belongs anywhere near this class.
     */
    public static final String APPLICATION_ID = "1562555784322351234";

    /** The second line, always this. */
    public static final String DETAILS = "For monsters";

    /** Preference key for the switch. On by default. */
    public static final String PREF_KEY = "fear_discord_presence";

    /** True once the native client has started, so it is started only once. */
    private static boolean sStarted = false;

    static {
        try {
            // The wrapper lives in pojavexec, the launcher's own native library, which is
            // already the one every other native call in the launcher goes through.
            System.loadLibrary("pojavexec");
        } catch (Throwable t) {
            Log.w(TAG, "could not load pojavexec; presence will stay off", t);
        }
    }

    private FearPresence() {}

    private static native boolean nativeStart(String appId);
    private static native void nativeSetPlaying(String details, String state);
    private static native void nativeClear();

    /** True when the user wants their presence shown. On by default. */
    public static boolean isEnabled() {
        try {
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            return prefs == null || prefs.getBoolean(PREF_KEY, true);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * The third line: what is actually being played. Kept short, because Discord shows
     * it next to the party count and truncates anything long.
     */
    public static String stateFor(String instanceName, String mcVersion) {
        try {
            if (instanceName != null && !instanceName.isEmpty()) return instanceName;
            if (mcVersion != null && !mcVersion.isEmpty()) return "Minecraft " + mcVersion;
        } catch (Throwable ignored) { }
        return "Minecraft";
    }

    /**
     * Starts the Discord client the first time it is needed, rather than from an
     * activity, so there is no extra lifecycle to keep in step.
     */
    private static boolean ensureStarted() {
        if (sStarted) return true;
        try {
            sStarted = nativeStart(APPLICATION_ID);
            if (!sStarted) Log.i(TAG, "Discord client did not start; presence stays off");
            return sStarted;
        } catch (Throwable t) {
            Log.w(TAG, "could not start the Discord client", t);
            return false;
        }
    }

    /** Publishes what is being played. Never throws. */
    public static void setPlaying(String instanceName, String mcVersion) {
        try {
            if (!isEnabled()) {
                Log.i(TAG, "presence is off in settings; nothing is published");
                return;
            }
            if (!ensureStarted()) return;
            String state = stateFor(instanceName, mcVersion);
            nativeSetPlaying(DETAILS, state);
            Log.i(TAG, "presence sent: details=\"" + DETAILS + "\" state=\"" + state + "\"");
        } catch (Throwable t) {
            Log.w(TAG, "presence update skipped", t);
        }
    }

    /** Clears the presence when the game stops. Never throws. */
    public static void clear() {
        try {
            if (!sStarted) return;
            nativeClear();
            Log.i(TAG, "presence cleared");
        } catch (Throwable ignored) { }
    }
}
