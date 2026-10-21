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
 * <p>The SDK is native and is not wired up yet, so nothing here reaches Discord today.
 * What is here is the payload and the switch: the exact values Discord will be told,
 * and the user's choice about whether to tell it.
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

    private FearPresence() {}

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
     * Publishes the presence. Inert until the Social SDK is wired up: the values are
     * assembled and logged so the wiring has something exact to send, and nothing
     * leaves the device.
     */
    public static void setPlaying(String instanceName, String mcVersion) {
        try {
            if (!isEnabled()) {
                Log.i(TAG, "presence is off in settings; nothing is published");
                return;
            }
            Log.i(TAG, "presence payload ready: app=" + APPLICATION_ID
                    + " details=\"" + DETAILS + "\" state=\"" + stateFor(instanceName, mcVersion) + "\""
                    + " (the Social SDK wiring is the next step; nothing is sent yet)");
        } catch (Throwable t) {
            Log.w(TAG, "presence update skipped", t);
        }
    }

    /** Clears the presence when the game stops. Inert for the same reason. */
    public static void clear() {
        try {
            Log.i(TAG, "presence clear requested (the Social SDK wiring is the next step)");
        } catch (Throwable ignored) { }
    }
}
