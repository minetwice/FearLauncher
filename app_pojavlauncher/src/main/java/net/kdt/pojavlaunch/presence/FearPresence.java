package net.kdt.pojavlaunch.presence;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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
 *
 * <p>This takes the official route. Discord's Social SDK supports Rich Presence on
 * Android, so the launcher registers as an application and talks to the Discord app
 * installed on the phone. It deliberately does not use the user-token gateway approach
 * that several Android presence apps take - that asks the user for their Discord token
 * and works against Discord's terms, and that is not something to put in front of
 * people who trust the launcher with their game.
 *
 * <p>The publishing call is native, and the SDK's callbacks are pumped from here on the
 * main looper. That is not a style choice: the SDK caches a JNIEnv when its library loads,
 * on the main thread, and RunCallbacks uses it. Pumping from the main thread reuses a valid
 * environment; pumping from any other thread makes the SDK jump through a null pointer
 * (pc=0 inside libdiscord_partner_sdk.so) and takes the game down.
 *
 * <p>The cost is that RunCallbacks blocks while it works, so the UI can freeze and Android
 * reports the app as not responding. The pump therefore runs as rarely as the SDK tolerates
 * rather than as often as it could. The real fix is to load the SDK's library on a thread of
 * our own and pump there, which needs the load order untangled first - see the pump comment.
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

    /** On unless the user turns it off. */
    private static final boolean ENABLED_BY_DEFAULT = true;

    /**
     * How often the SDK's callbacks are pumped. Slow on purpose: every pump blocks the main
     * thread for as long as RunCallbacks takes, so this is traded against the freeze. The
     * SDK only asks for its callbacks to run "periodically", and 750ms is comfortably inside
     * that while cutting the blocking by roughly eight times.
     */
    private static final long PUMP_INTERVAL_MS = 750L;

    /** True once the native client has started, so it is started only once. */
    private static boolean sStarted = false;

    /** Last status written to the log, so the watcher only reports changes. */
    private static int sLastLoggedStatus = -2;

    static {
        try {
            // The SDK's own library is loaded FIRST, explicitly. pojavexec links it, so
            // loading pojavexec first would pull it in as a dependency, and Android only
            // calls JNI_OnLoad for libraries loaded through System.loadLibrary.
            //
            // This is also what pins the callbacks to the main thread: the load happens
            // here, on whichever thread first touches this class, and the SDK caches that
            // thread's JNIEnv for use in RunCallbacks. Untangling it is the next step.
            System.loadLibrary("discord_partner_sdk");
        } catch (Throwable t) {
            say("could not load the Discord SDK library; presence will stay off", t);
        }
        try {
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
            return prefs == null || prefs.getBoolean(PREF_KEY, ENABLED_BY_DEFAULT);
        } catch (Throwable t) {
            return ENABLED_BY_DEFAULT;
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
     * Drives the SDK's callbacks from the main looper. The SDK uses a JNIEnv cached when its
     * library loaded - the main thread - so this is where the callbacks have to run. From
     * any other thread the SDK jumps through a null pointer and takes the game down.
     *
     * The interval is deliberately slow; see PUMP_INTERVAL_MS. Making the UI responsive
     * again means getting the load - and therefore the cached environment - onto a thread of
     * our own, not pumping from a foreign one.
     */
    private static void startPump() {
        try {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try { nativePump(); } catch (Throwable ignored) { }
                    handler.postDelayed(this, PUMP_INTERVAL_MS);
                }
            }, PUMP_INTERVAL_MS);
        } catch (Throwable t) {
            say("could not start the callback pump", t);
        }
    }

    private static boolean ensureStarted() {
        if (sStarted) return true;
        try {
            sStarted = nativeStart(APPLICATION_ID);
            if (!sStarted) {
                say("Discord client did not start; presence stays off");
            } else {
                say("Discord client started; status " + describeStatus());
                startPump();
                watchStatus();
            }
            return sStarted;
        } catch (Throwable t) {
            say("could not start the Discord client", t);
            return false;
        }
    }

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
            if (!isEnabled()) return;
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
     * Called when a game stops. Kept for the callers that already use it, and it shows the
     * launcher rather than wiping the status.
     */
    public static void clear() {
        setDashboard();
    }
}
