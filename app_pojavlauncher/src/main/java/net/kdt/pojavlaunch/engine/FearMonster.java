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
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
 *
 * <p>What it did is written to <data>/fear_monster/trace.txt. Log.i only reaches logcat,
 * and the launcher's own log file does not exist until a game has started - so a fetch that
 * runs when the dashboard opens would otherwise leave no trace anyone could read off a
 * phone, and "did the build arrive?" would be unanswerable from the device.
 */
public final class FearMonster {

    private static final String TAG = "FearMonster";

    /** Where the engine builds live, one directory per version id. */
    private static final String ENGINE_ROOT = "fear_monster";

    /** The file a build is installed as, inside that directory. */
    private static final String BUILD_FILE = "engine.jar";

    /** What the engine did, for whoever has to work out why it did not. */
    private static final String TRACE_FILE = "trace.txt";

    /** Keep the trace from growing without bound; it is a diagnostic, not a record. */
    private static final long TRACE_LIMIT = 64 * 1024;

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

    /** The directory builds and the trace live in. */
    private static File rootDir() {
        return new File(Tools.DIR_DATA, ENGINE_ROOT);
    }

    /**
     * Records one line of what the engine did, for whoever has to read it off the device.
     * Never throws and never blocks anything: a diagnostic that can break the thing it is
     * describing is worse than no diagnostic.
     */
    private static void trace(String line) {
        try {
            Log.i(TAG, line);
        } catch (Throwable ignored) { }
        try {
            File root = rootDir();
            if (!root.isDirectory() && !root.mkdirs()) return;
            File file = new File(root, TRACE_FILE);
            if (file.length() > TRACE_LIMIT) file.delete();
            String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            try (FileWriter w = new FileWriter(file, true)) {
                w.write(stamp + "  " + line + "\n");
            }
        } catch (Throwable ignored) { }
    }

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
            File dir = new File(rootDir(), versionId);
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
                    if (parent != null && !parent.isEmpty()) {
                        trace("version " + versionId + " inherits from Minecraft " + parent);
                        return parent;
                    }
                }
            }
            trace("no version json at " + json + "; using the version id as the Minecraft version");
        } catch (Throwable t) {
            Log.w(TAG, "could not read the Minecraft version behind " + versionId, t);
            trace("could not read " + json + ": " + t);
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
            if (!isEnabled()) {
                trace("engine is off in settings; nothing is fetched");
                return;
            }
            extractNatives(versionId);
            if (versionId == null || versionId.isEmpty()) {
                trace("no version selected; nothing is fetched");
                return;
            }
            // No "a build is already installed, stop" here on purpose. An installed build can
            // be out of date, and the published one is rebuilt whenever the engine changes -
            // which is exactly when it matters, because a stale engine is one that installs
            // and then cannot load. The check happens in the background instead: one HEAD
            // request, and the download only when the sizes differ.
            if (sFetching) {
                trace("a fetch is already running");
                return;
            }
            sFetching = true;
            trace("checking the build for " + versionId);
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    fetch(versionId);
                } catch (Throwable t) {
                    Log.w(TAG, "could not fetch the engine build for " + versionId, t);
                    trace("fetch failed: " + t);
                } finally {
                    sFetching = false;
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "engine fetch could not be started", t);
            trace("fetch could not be started: " + t);
        }
    }

    /**
     * Copies the engine's LWJGL natives out of the APK's assets, into the directory the
     * launcher already puts on java.library.path.
     *
     * <p>The natives reach the APK as assets, under components/lwjgl-3.3.3-natives/&lt;abi&gt;/,
     * and Android only moves libraries from jni/&lt;abi&gt;/ into lib/&lt;abi&gt;/ - so as things
     * stand nothing extracts them and the engine cannot find its allocator at all:
     *
     * <pre>UnsatisfiedLinkError: Failed to locate library: liblwjgl_vma.so</pre>
     *
     * <p>The launcher's java.library.path already contains &lt;cache&gt;/natives/&lt;version id&gt;, so
     * a copy into there is the whole of it. This is the same thing PojavLauncher and
     * CopperLauncher do with their own copies of these files, for the same reason.
     */
    public static void extractNatives(String versionId) {
        try {
            if (versionId == null || versionId.isEmpty()) return;
            android.content.Context context =
                    net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
            if (context == null) {
                trace("no application context; LWJGL natives not extracted");
                return;
            }
            File dir = new File(Tools.DIR_CACHE, "natives/" + versionId);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                trace("could not create " + dir);
                return;
            }
            String assetDir = "components/lwjgl-3.3.3-natives/" + abiDir();
            String[] names = context.getAssets().list(assetDir);
            if (names == null || names.length == 0) {
                trace("no LWJGL natives in the APK under " + assetDir);
                return;
            }
            int copied = 0;
            for (String name : names) {
                File out = new File(dir, name);
                if (out.isFile() && out.length() > 0) continue;
                try (java.io.InputStream in = context.getAssets().open(assetDir + "/" + name);
                     java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
                }
                copied++;
            }
            trace("LWJGL natives: " + names.length + " in the APK, " + copied
                    + " copied to " + dir);
        } catch (Throwable t) {
            Log.w(TAG, "could not extract the LWJGL natives", t);
            trace("could not extract the LWJGL natives: " + t);
        }
    }

    /** The ABI whose natives this device needs. */
    private static String abiDir() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0 && abis[0] != null && !abis[0].isEmpty()) {
                return abis[0];
            }
        } catch (Throwable ignored) { }
        return "arm64-v8a";
    }

    private static void fetch(String versionId) {
        String mcVersion = mcVersionFor(versionId);
        if (mcVersion == null) {
            trace("no Minecraft version resolved; nothing fetched");
            return;
        }

        File dir = new File(rootDir(), versionId);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            trace("could not create " + dir);
            return;
        }

        String url = String.format(Locale.US, BUILD_URL, mcVersion);
        File out = new File(dir, BUILD_FILE);

        // Already current? One HEAD request answers it. A published engine is replaced
        // whenever the engine changes, so comparing against what is installed is what lets
        // a fixed engine actually reach a device that already has an older one.
        long remote = remoteSize(url);
        if (out.isFile() && remote > 0 && out.length() == remote) {
            trace("the build for Minecraft " + mcVersion + " is current (" + remote + " bytes)");
            return;
        }
        if (out.isFile()) {
            trace("the build for Minecraft " + mcVersion + " differs: installed " + out.length()
                    + " bytes, published " + remote + " bytes; fetching it again");
        }

        File part = new File(dir, BUILD_FILE + ".part");
        trace("downloading " + url);
        int code = download(url, part);
        if (code != 200) {
            trace("download failed: HTTP " + code);
            return;
        }

        // Renamed into place only once it is whole, so a half-written file is never left
        // where the launcher would pick it up as a build.
        if (out.exists()) out.delete();
        if (!part.renameTo(out)) {
            trace("could not move the build into place");
            part.delete();
            return;
        }

        trace("installed " + out + " for Minecraft " + mcVersion + " (" + out.length() + " bytes)");
        RendererCompatUtil.releaseRenderersCache();
    }

    /**
     * The size the published build reports, or -1 when it could not be asked.
     *
     * Used only to tell whether what is installed is still what is published. A failure
     * returns -1, which the caller treats as "unknown" and therefore as "fetch it", because
     * downloading a current file again is harmless while keeping a stale one is not.
     */
    private static long remoteSize(String url) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != java.net.HttpURLConnection.HTTP_OK) return -1;
            return connection.getContentLengthLong();
        } catch (Throwable t) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Streams a URL to a file. Returns the HTTP result, or -1 when it never got one. */
    private static int download(String url, File out) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            // Release assets redirect to storage; without this the body would be the
            // redirect page rather than the jar.
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code != java.net.HttpURLConnection.HTTP_OK) return code;
            try (java.io.InputStream in = connection.getInputStream();
                 java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
            }
            if (out.length() == 0) {
                trace("the download produced an empty file");
                out.delete();
                return -1;
            }
            return code;
        } catch (Throwable t) {
            Log.w(TAG, "engine download failed", t);
            trace("download threw: " + t);
            return -1;
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
                trace("engine is off in settings; this session runs on " + renderer + " as usual");
                return;
            }
            File build = buildFor(versionId);
            if (build == null) {
                trace("no build for " + versionId + "; this session runs on " + renderer
                        + " as usual - pick the Fear Monster renderer only after one arrives");
                return;
            }
            if (!RENDERER_ID.equals(renderer)) {
                trace("a build is installed for " + versionId + " but this session uses "
                        + renderer + "; the engine stays out of the way");
                return;
            }
            trace("the engine will drive " + versionId + " using " + build.getName());
        } catch (Throwable t) {
            Log.w(TAG, "engine check failed; the session runs on its renderer as usual", t);
            trace("engine check failed: " + t);
        }
    }
}
