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
 * <p>This class is the decision point, and now the injection point. It answers three
 * questions for a launch: whether the engine is switched on, whether a build exists for
 * the Minecraft version about to start, and therefore whether the engine is handed to the
 * game at all.
 *
 * <p>Everything here is inert by design. When the engine is off, or when no build matches
 * the version, this class does nothing and the session runs on the renderer the user
 * picked - Zink, Quasar, GLES2, LTW - exactly as it does today. Nothing in this file can
 * change how an existing renderer behaves; it only ever decides whether a separate engine
 * is used, and by default it decides not to.
 *
 * <p>The engine is handed over with {@code -Dfabric.addMods}, so Fabric loads it as a
 * built-in component: it never lands in the instance's mods folder and the user never
 * manages it. That argument is only produced when the direct-Vulkan render path is the
 * one in use - which is the next step - so today this injects nothing on any renderer.
 */
public final class FearMonster {

    private static final String TAG = "FearMonster";

    /** Where the engine builds live, one directory per Minecraft version. */
    private static final String ENGINE_ROOT = "fear_monster";

    /** The renderer id that means "the engine owns the frame". Until that path exists,
     *  nothing is ever injected. */
    public static final String RENDERER_ID = "fear_monster";

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
     * Whether the engine should be handed to the game this launch. All three conditions
     * have to hold: switched on, a build for this Minecraft version, and the direct-Vulkan
     * render path in use. The last one does not exist yet, so this is false everywhere
     * today - which is the point: it cannot disturb a working session.
     */
    public static boolean shouldInject(String mcVersion, String renderer) {
        try {
            return isEnabled()
                    && RENDERER_ID.equals(renderer)
                    && buildFor(mcVersion) != null;
        } catch (Throwable t) {
            return false;
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
                Log.i(TAG, "no Fear Monster build for Minecraft " + mcVersion
                        + "; this session runs on " + renderer + " as usual");
                return;
            }
            if (!RENDERER_ID.equals(renderer)) {
                Log.i(TAG, "Fear Monster build " + build.getName() + " is present for Minecraft "
                        + mcVersion + ", but this session uses " + renderer
                        + "; the engine stays out of the way");
                return;
            }
            Log.i(TAG, "Fear Monster build " + build.getName() + " will drive Minecraft " + mcVersion);
        } catch (Throwable t) {
            Log.w(TAG, "engine check failed; the session runs on its renderer as usual", t);
        }
    }
}
