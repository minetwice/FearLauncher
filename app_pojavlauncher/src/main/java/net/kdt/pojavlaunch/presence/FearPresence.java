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
 *
 * <p>This takes the official route. Discord's Social SDK supports Rich Presence on
 * Android, so the launcher registers as an application and talks to the Discord app
 * installed on the phone. It deliberately does not use the user-token gateway approach
 * that several Android presence apps take - that asks the user for their Discord token
 * and works against Discord's terms, and that is not something to put in front of
 * people who trust the launcher with their game.
 *
 * <p><b>The SDK's callbacks are deliberately not pumped.</b> Pumping them - calling
 * Discord_RunCallbacks periodically, as the SDK's documentation asks - crashes the process.
 * The hs_err backtrace shows it plainly:
 * <pre>
 *   C  [libdiscord_partner_sdk.so+0x245080]
 *   C  [libdiscord_partner_sdk.so+0x22425c]  Discord_RunCallbacks+0x70
 *   C  [libpojavexec.so+0x1a8e8]  Java_..._nativePump+0x2c
 *   siginfo: SIGSEGV, si_addr: 0x0000000000000000
 * </pre>
 * RunCallbacks dereferences a null pointer on its own, on whatever thread calls it - which
 * is why moving it between threads never helped, and why the load-order and JNI-environment
 * explanations were all wrong.
 *
 * <p>The activity itself is published before any of that and Discord displays it, so the
 * presence works without the pump. The pump is what comes after, and the pump is what dies.
 *
 * <p>If the status turns out to need RunCallbacks to update, the next step is the SDK's Java
 * RPC transport (DiscordRpcClient), which the SDK expects the app to establish and which this
 * launcher never has.
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

    /** True once the native client has started, so it is started only once. */
    private static boolean sStarted = false;

    /** Last status written to the log, so the watcher only reports changes. */
    private static int sLastLoggedStatus = -2;

    static {
        try {
            // The SDK's own library is loaded FIRST, explicitly. pojavexec links it, so
            // loading pojavexec first would pull it in as a dependency, and Android only
            // calls JNI_OnLoad for libraries loaded through System.loadLibrary.
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

    private static boolean ensureStarted() {
        if (sStarted) return true;
        try {
            sStarted = nativeStart(APPLICATION_ID);
            if (!sStarted) {
                say("Discord client did not start; presence stays off");
            } else {
                say("Discord client started; status " + describeStatus());
                // Deliberately no pump: Discord_RunCallbacks crashes the process. See the
                // class comment for the backtrace.
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

    /**
     * Watches the connection for half a minute and writes every change to the launcher's
     * log, so the file shows how far the client actually gets. This reads an int and cannot
     * be what crashes - the backtrace points at RunCallbacks, not at this.
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
