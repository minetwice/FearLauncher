package net.kdt.pojavlaunch.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

import java.io.File;

/**
 * Fear Monster - the direct-Vulkan engine, delivered as a launcher function instead of a mod.
 *
 * <p>The engine renders Minecraft straight to Vulkan, with no OpenGL translation layer in
 * the way, which is where its frame rate comes from. It hooks Minecraft's own classes, so
 * unlike a renderer it is tied to a Minecraft version: one build cannot serve every
 * version. That is exactly why it belongs in the launcher rather than in the user's mods
 * folder - the launcher knows which version is being launched, picks the matching build and
 * installs it, so nobody has to match versions by hand or even know the engine exists.
 *
 * <p>This class answers three questions for a launch: whether the engine is switched on,
 * whether a build exists for the Minecraft version about to start, and therefore whether the
 * engine is handed to the game at all. It also fetches the build when one is missing.
 *
 * <p>Everything here is inert by design. When the engine is off, or when no build matches
 * the version, this class does nothing and the session runs on the renderer the user
 * picked - Zink, Quasar, GLES2, LTW - exactly as it does today. Nothing in this file can
 * change how an existing renderer behaves; it only ever decides whether a separate engine
 * is used, and by default it decides not to.
 */
public final class FearMonster {

    private static final String TAG = "FearMonster";

    /** Where the engine builds live, one directory per version id. */
    private static final String ENGINE_ROOT = "fear_monster";

    /** The file a build is installed as, inside that directory. */
    private static final String BUILD_FILE = "engine.jar";

    /**
     * Where the engine builds are published. The name carries the Minecraft version the
     * build serves, which is the version it was compiled against and the only version it
     * can hook.
     */
    private static final String BUILD_URL =
            "https://github.com/minetwice/FearLauncher/releases/download/nightly/"
                    + "fear-monster-engine-%s.jar";

    /** The renderer id that means "the engine owns the frame". */
    public static final String RENDERER_ID = "fear_monster";

    /** True while a fetch is in flight, so only one ever runs. */
    private static volatile boolean sFetching = false;

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

    /**
     * The engine build for a version id, or null when there is none.
     *
     * <p>Keyed by version id, not by Minecraft version: that is what the launch knows and
     * what the injection in GameRunner asks with, so the two always agree.
     */
    public static File buildFor(String versionId) {
        if (versionId == null || versionId.isEmpty()) return null;
        try {
            File dir = new File(Tools.DIR_DATA, ENGINE_ROOT + "/" + versionId);
            if (!dir.isDirectory()) return null;
            File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars == null || jars.length == 0) return null;
            return jars[0];
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The Minecraft version behind a version id.
     *
     * <p>The launcher works in version ids, and for a modded install those carry the loader
     * too - fabric-loader-0.18.2-1.21.11, say - while the engine is built per Minecraft
     * version and published as 1.21.11. The version's own JSON says which Minecraft it
     * inherits from, and that is the honest answer. For a vanilla install there is nothing
     * to inherit from, and the version id is already the Minecraft version.
     */
    public static String mcVersionFor(String versionId) {
        if (versionId == null || versionId.isEmpty()) return null;
        File json = new File(Tools.DIR_GAME_NEW + "/versions/" + versionId + "/" + versionId + ".json");
        try {
            if (json.isFile()) {
                JsonObject root;
                try (java.io.FileReader reader = new java.io.FileReader(json)) {
                    root = Tools.GLOBAL_GSON.fromJson(reader, JsonObject.class);
                }
                if (root != null && root.has("inheritsFrom") && !root.get("inheritsFrom").isJsonNull()) {
                    String parent = root.get("inheritsFrom").getAsString();
                    if (parent != null && !parent.isEmpty()) return parent;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not read the Minecraft version behind " + versionId, t);
        }
        return versionId;
    }

    /**
     * Makes sure a build for this version is installed, fetching it if not.
     *
     * <p>Off the main thread, because it is a download. Only runs when the engine is
     * switched on, so a session on any other renderer never goes near the network for this.
     *
     * <p>When a build lands, the renderer cache is dropped: that cache is what decides
     * whether the Fear Monster entry is offered at all, and until a build exists it
     * deliberately is not - so without this the entry would stay hidden until the next
     * launch even though the engine had arrived.
     */
    public static void ensureBuildAsync(String versionId) {
        try {
            if (!isEnabled()) return;
            if (versionId == null || versionId.isEmpty()) return;
            if (sFetching) return;
            if (buildFor(versionId) != null) return;
            sFetching = true;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    fetch(versionId);
                } catch (Throwable t) {
                    Log.w(TAG, "could not fetch the engine build for " + versionId, t);
                } finally {
                    sFetching = false;
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "engine fetch could not be started", t);
        }
    }

    private static void fetch(String versionId) {
        String mcVersion = mcVersionFor(versionId);
        if (mcVersion == null) return;

        File dir = new File(Tools.DIR_DATA, ENGINE_ROOT + "/" + versionId);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "could not create " + dir);
            return;
        }

        String url = String.format(java.util.Locale.US, BUILD_URL, mcVersion);
        File part = new File(dir, BUILD_FILE + ".part");
        Log.i(TAG, "fetching the engine for Minecraft " + mcVersion + " from " + url);
        if (!download(url, part)) return;

        // Renamed into place only once it is whole, so a half-written file is never left
        // where the launcher would pick it up as a build.
        File out = new File(dir, BUILD_FILE);
        if (out.exists()) out.delete();
        if (!part.renameTo(out)) {
            Log.w(TAG, "could not move the engine build into place");
            part.delete();
            return;
        }

        Log.i(TAG, "engine installed for Minecraft " + mcVersion + " at " + out
                + " (" + out.length() + " bytes)");
        RendererCompatUtil.releaseRenderersCache();
    }

    /** Streams a URL to a file. Returns true only when the file is complete. */
    private static boolean download(String url, File out) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            // Release assets redirect to storage; without this the body would be the
            // redirect page rather than the jar.
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "engine download failed: HTTP " + code);
                return false;
            }
            try (java.io.InputStream in = connection.getInputStream();
                 java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
            }
            if (out.length() == 0) {
                Log.w(TAG, "engine download produced an empty file");
                out.delete();
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "engine download failed", t);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Whether the engine should be handed to the game this launch. All three conditions
     * have to hold: switched on, a build for this version, and the direct-Vulkan render
     * path in use.
     */
    public static boolean shouldInject(String versionId, String renderer) {
        try {
            return isEnabled()
                    && RENDERER_ID.equals(renderer)
                    && buildFor(versionId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Decides, once per launch, whether the engine runs for this session. Never throws,
     * and never touches the chosen renderer - it only reports what it found.
     */
    public static void prepare(Context context, File gameDir, String versionId, String renderer) {
        try {
            if (!isEnabled()) {
                Log.i(TAG, "engine is off in settings; this session runs on " + renderer + " as usual");
                return;
            }
            File build = buildFor(versionId);
            if (build == null) {
                Log.i(TAG, "no Fear Monster build for " + versionId
                        + "; this session runs on " + renderer + " as usual");
                return;
            }
            if (!RENDERER_ID.equals(renderer)) {
                Log.i(TAG, "Fear Monster build " + build.getName() + " is present for " + versionId
                        + ", but this session uses " + renderer
                        + "; the engine stays out of the way");
                return;
            }
            Log.i(TAG, "Fear Monster build " + build.getName() + " will drive " + versionId);
        } catch (Throwable t) {
            Log.w(TAG, "engine check failed; the session runs on its renderer as usual", t);
        }
    }
}
