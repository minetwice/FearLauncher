package net.kdt.pojavlaunch.presence;

import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Fear Launcher's Discord presence.
 *
 * <p>Discord renders an activity as three lines:
 * <pre>
 *   Playing "Fear Launcher"     <- the activity's name
 *   Powered by fear             <- the activity's details
 *   &lt;instance&gt; / Monster mode  <- the activity's state
 * </pre>
 * The name and the details are fixed; the state follows what the player is doing, so a
 * friend can see which instance is being played.
 *
 * <p>This takes the official route. Discord's Social SDK supports Rich Presence on
 * Android, so the launcher registers as an application and talks to the Discord app
 * installed on the phone. It deliberately does not use the user-token gateway approach
 * that several Android presence apps take - that asks the user for their Discord token
 * and works against Discord's terms, and that is not something to put in front of
 * people who trust the launcher with their game.
 *
 * <p>The publishing call itself is native: the SDK exposes presence in C, not in Java. The
 * SDK's callbacks are pumped from a thread in fear_presence.c, attached to the JVM and kept
 * off the UI thread.
 *
 * <p>Every message here goes to the launcher's own log as well as to logcat. The launcher's
 * log is the file anyone can actually read off a phone, and the question that matters -
 * how far the connection gets - has to be answerable from it.
 */
public final class FearPresence {

    private static final String TAG = "FearPresence";

    /**
     * Application id from Discord's developer portal. This is a public identifier - it
     * is not a secret, and no secret or token belongs anywhere near this class.
     */
    public static final String APPLICATION_ID = "1562555784322351234";

    /** The second line, always this. */
    public static final String DETAILS = "Powered by fear";

    /** The third line while the player is on the launcher rather than in a game. */
    public static final String STATE_DASHBOARD = "Monster mode";

    /** Preference key for the switch. On by default. */
    public static final String PREF_KEY = "fear_discord_presence";

    /** True once the native client has started, so it is started only once. */
    private static boolean sStarted = false;

    /** Last status written to the log, so the watcher only reports changes. */
    private static int sLastLoggedStatus = -2;

    static {
        try {
            // The SDK's own library is loaded FIRST, explicitly, and that order matters.
            // pojavexec links it, so loading pojavexec first pulls the SDK in as a
            // dependency - and Android only calls JNI_OnLoad for libraries loaded through
            // System.loadLibrary, never for dependencies. Its JNI plumbing would then stay
            // uninitialised and its first call back into Java would jump through a null
            // pointer, which is exactly the pc=0 crash that was seen. Loading it by name
            // here means JNI_OnLoad runs; the later load from the SDK's own init class is
            // then a harmless no-op.
            System.loadLibrary("discord_partner_sdk");
        } catch (Throwable t) {
            say("could not load the Discord SDK library; presence will stay off", t);
        }
        try {
            // The wrapper lives in pojavexec, the launcher's own native library, which is
            // already the one every other native call in the launcher goes through.
            System.loadLibrary("pojavexec");
        } catch (Throwable t) {
            say("could not load pojavexec; presence will stay off", t);
        }
    }

    private FearPresence() {}

    private static native boolean nativeStart(String appId);
    private static native void nativePump();
    private static native void nativeSetPlaying(String details, String state);
    private static native void nativeClear();
    private static native int nativeStatus();

    /**
     * Reports a message to both logcat and the launcher's log file. The second one is the
     * point: it is the only log a user can hand over, so anything worth knowing has to be
     * in it.
     */
    private static void say(String message, Throwable t) {
        try { Log.i(TAG, message, t); } catch (Throwable ignored) { }
        try { net.kdt.pojavlaunch.Logger.appendToLog("[Discord] " + message); } catch (Throwable ignored) { }
    }

    private static void say(String message) {
        say(message, null);
    }

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
     * The third line while a game runs: the instance name, falling back to the Minecraft
     * version. Kept short, because Discord shows it next to the party count and truncates
     * anything long.
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
            if (!sStarted) {
                say("Discord client did not start; presence stays off");
            } else {
                say("Discord client started; status " + describeStatus());
                watchStatus();
            }
            return sStarted;
        } catch (Throwable t) {
            say("could not start the Discord client", t);
            return false;
        }
    }

    /** The status as a number plus what it means, for the log. */
    private static String describeStatus() {
        try {
            int s = nativeStatus();
            String meaning;
            switch (s) {
                case 0: meaning = "disconnected"; break;
                case 1: meaning = "connecting"; break;
                case 2: meaning = "connected"; break;
                case 3: meaning = "ready"; break;
                case 4: meaning = "reconnecting"; break;
                case 5: meaning = "disconnecting"; break;
                case 6: meaning = "http wait"; break;
                case -1: meaning = "SDK not linked into this build"; break;
                default: meaning = "unknown"; break;
            }
            return s + " (" + meaning + ")";
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    /**
     * Watches the connection for half a minute and writes every change to the launcher's
     * log, so the file shows how far the client actually gets.
     */
    private static void watchStatus() {
        try {
            final java.util.concurrent.ScheduledExecutorService watcher =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "fear-presence-status");
                        t.setDaemon(true);
                        return t;
                    });
            watcher.scheduleAtFixedRate(() -> {
                try {
                    int s = nativeStatus();
                    if (s != sLastLoggedStatus) {
                        sLastLoggedStatus = s;
                        say("Discord status now " + describeStatus());
                    }
                } catch (Throwable ignored) { }
            }, 0, 3, java.util.concurrent.TimeUnit.SECONDS);
            watcher.schedule(watcher::shutdown, 30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            say("could not start the status watcher", t);
        }
    }

    private static void publish(String state) {
        try {
            if (!isEnabled()) {
                say("presence is off in settings; nothing is published");
                return;
            }
            if (!ensureStarted()) return;
            nativeSetPlaying(DETAILS, state);
            say("presence sent: details=\"" + DETAILS + "\" state=\"" + state
                    + "\" status " + describeStatus());
        } catch (Throwable t) {
            say("presence update skipped", t);
        }
    }

    /** What is being played. Never throws. */
    public static void setPlaying(String instanceName, String mcVersion) {
        publish(stateFor(instanceName, mcVersion));
    }

    /**
     * The launcher itself, for when no game is running. This is also where a finished
     * session lands: exiting a game puts the player back on the launcher, so the status
     * should say that rather than disappearing.
     */
    public static void setDashboard() {
        publish(STATE_DASHBOARD);
    }

    /**
     * Called when a game stops. Kept for the callers that already use it, and it now
     * shows the launcher rather than wiping the status.
     */
    public static void clear() {
        setDashboard();
    }
}
