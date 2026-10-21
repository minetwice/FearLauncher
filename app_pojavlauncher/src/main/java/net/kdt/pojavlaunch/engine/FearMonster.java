package net.kdt.pojavlaunch.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

/**
 * Fear Monster - the direct-Vulkan engine, delivered as a launcher function instead of a mod.
 *
 * <p>The engine renders Minecraft straight to Vulkan, with no OpenGL translation layer in
 * the way, which is where its frame rate comes from. It hooks Minecraft's own classes, so
 * unlike a renderer it is tied to a Minecraft version: one build cannot serve every
 * version. That is exactly why it belongs in the launcher rather than in the user's mods
 * folder - the launcher knows which version is being launched and can pick the matching
 * build itself, so nobody has to match versions by hand or even know the engine exists.
 *
 * <p>This class is the decision point. It answers three questions for a launch:
 * whether the engine is switched on, whether a build exists for the Minecraft version
 * about to start, and therefore whether the engine runs at all this time.
 *
 * <p>Everything here is inert by design. When the engine is off, or when no build matches
 * the version, this class does nothing at all and the session runs on the renderer the
 * user picked - Zink, Quasar, LTW - exactly as it does today. Nothing in this file can
 * change how an existing renderer behaves; it only ever decides whether a separate engine
 * is used, and by default it decides not to.
 *
 * <p>The engine builds themselves, their injection into the game, and the direct-Vulkan
 * render path are the following steps. Until a build is present under
 * {@code <data>/fear_monster/<minecraft version>/}, this class finds nothing and every
 * session stays on its renderer.
 */
public final class FearMonster {

    private static final String TAG = "FearMonster";

    /** Where the engine builds live, one directory per Minecraft version. */
    private static final String ENGINE_ROOT = "fear_monster";

    private FearMonster() {}

    /** True when the user has the engine switched on. On by default. */
    public static boolean isEnabled() {
        try {
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            return prefs == null || prefs.getBoolean("fear_monster", true);
        } catch (Throwable t) {
            return true;
        }
    }

    /** The engine build for a Minecraft version, or null when there is none. */
    public static File buildFor(String mcVersion) {
        if (mcVersion == null || mcVersion.isEmpty()) return null;
        try {
            File dir = new File(Tools.DIR_DATA, ENGINE_ROOT + "/" + mcVersion);
            if (!dir.isDirectory()) return null;
            File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars == null || jars.length == 0) return null;
            return jars[0];
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Decides, once per launch, whether the engine runs for this session. Never throws,
     * and never touches the chosen renderer - it only reports what it found.
     */
    public static void prepare(Context context, File gameDir, String mcVersion, String renderer) {
        try {
            if (!isEnabled()) {
                Log.i(TAG, "engine is off in settings; this session runs on " + renderer + " as usual");
                return;
            }
            File build = buildFor(mcVersion);
            if (build == null) {
                // The common case until the engine builds ship: say so plainly and leave
                // everything alone. The renderer the user picked is untouched.
                Log.i(TAG, "no Fear Monster build for Minecraft " + mcVersion
                        + "; this session runs on " + renderer + " as usual");
                return;
            }
            Log.i(TAG, "Fear Monster build " + build.getName() + " available for Minecraft "
                    + mcVersion + " (renderer " + renderer + " is left untouched either way)");
        } catch (Throwable t) {
            Log.w(TAG, "engine check failed; the session runs on its renderer as usual", t);
        }
    }
}
