package net.kdt.pojavlaunch.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;

import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FearEngine - the launcher's rendering director and live performance governor.
 *
 * <p>Everything that decides how a session renders used to be spread across the launch
 * path: the driver and ICD were picked inside {@code JREUtils}, the render scale came from
 * a preference read once at surface creation, the hot-thread pinning lived in
 * {@code JavaRunner}, and the game's own options were tuned by {@code FearPerformanceMode}.
 * Each piece was reasonable on its own, but nothing owned the whole picture, so nothing
 * could react when a session actually started to struggle.
 *
 * <p>FearEngine is that owner. It has two jobs:
 *
 * <ol>
 *   <li><b>Direct.</b> Work out what the device actually is - GPU vendor and model, GLES
 *       level, core count, RAM, refresh rate - and from that decide which driver the
 *       renderer should run on, which renderer suits the device, and how many pixels are
 *       worth drawing. The decision is written to the engine trace so it is never a
 *       mystery why a session ran the way it did.</li>
 *
 *   <li><b>Govern.</b> Once the game is running, watch the real presented-frame counter and
 *       the device's thermal status, and move the render resolution to hold the frame rate
 *       near a target. This is the one lever that actually decides frame time on a phone:
 *       the number of pixels. The governor steps the resolution down when the frame rate
 *       sags or the device gets hot, and back up when there is headroom - all while the
 *       game keeps running, using the surface's own live resize path.</li>
 * </ol>
 *
 * <p>Every move is logged and appended to {@code fear_engine_trace.txt} next to the lag
 * report. Nothing here is allowed to break a launch: every entry point is wrapped, and a
 * failure simply disables the engine for that session.
 *
 * <p>On what it cannot do: the frame rate ceiling is the hardware and the translation
 * layer, not the launcher. A desktop-OpenGL game running through Zink on a Mali part has a
 * floor on frame time that no amount of tuning removes. The engine's job is to get as close
 * to that ceiling as the device allows and to keep it there without stutter.
 */
public final class FearEngine {

    private static final String TAG = "FearEngine";
    private static final String TRACE_NAME = "fear_engine_trace.txt";

    /** How often the governor looks at the world. */
    private static final long TICK_MS = 2000L;
    /** Never draw less than this fraction of the surface. */
    private static final float FLOOR_SCALE = 0.50f;
    /** One resolution step. */
    private static final float STEP = 0.05f;
    /** Below this fraction of the target the session is treated as struggling. */
    private static final float SAG = 0.85f;
    /** Above this fraction of the target there is headroom to spend. */
    private static final float HEADROOM = 0.98f;
    /** Consecutive struggling windows before a step down. */
    private static final int SAG_TICKS = 2;
    /** Consecutive comfortable windows before a step up. */
    private static final int HEADROOM_TICKS = 4;
    /** Below this fraction of the target, one window is enough to act. */
    private static final float DESPERATE = 0.60f;

    /** The scale the launcher chose for this device, remembered so the engine can learn
     *  within a stable range instead of ratcheting its own ceiling down every session. */
    private static final String KEY_CEILING = "fear_engine_ceiling";
    /** Where the last session settled, so the next one starts there. */
    private static final String KEY_LEARNED = "fear_engine_learned_scale";
    private static volatile float sLastScale = 0f;

    private static volatile boolean sRunning;
    private static Thread sThread;
    private static volatile MinecraftGLSurface sSurface;
    private static File sTrace;
    private static String sRenderer = "?";

    private FearEngine() {}

    /** Called from the surface as it comes up, so the governor has something to resize. */
    public static void attachSurface(MinecraftGLSurface surface) {
        sSurface = surface;
    }

    /** Called just before the JVM starts. Safe to call more than once. */
    public static synchronized void start(Context context, File gameDir, String renderer) {
        if (sRunning) return;
        try {
            sRenderer = renderer == null ? "?" : renderer;
            sTrace = gameDir == null ? null : new File(gameDir, TRACE_NAME);
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            if (prefs != null && !prefs.getBoolean("fear_engine", true)) {
                Log.i(TAG, "engine disabled by preference");
                return;
            }
            sRunning = true;
            final Context app = context.getApplicationContext();
            final DeviceProfile profile = DeviceProfile.of(app);
            final String plan = DriverDirector.plan(profile, sRenderer);
            appendTrace("=== FearEngine session " + now() + " ===\n"
                    + profile.describe() + "\n" + plan + "\n");
            Log.i(TAG, "device: " + profile.describe());
            Log.i(TAG, "plan: " + plan);

            sThread = new Thread(() -> govern(app, profile), "fear-engine");
            sThread.setDaemon(true);
            sThread.start();
        } catch (Throwable t) {
            sRunning = false;
            Log.w(TAG, "engine could not start, continuing without it", t);
        }
    }

    public static void stop() {
        sRunning = false;
        Thread t = sThread;
        sThread = null;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException ignored) { }
        }
        // Remember where the session settled so the next one starts there.
        try {
            float scale = sLastScale;
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            if (scale > 0f && prefs != null) {
                prefs.edit().putInt(KEY_LEARNED, Math.round(scale * 100f)).apply();
                appendTrace(String.format(Locale.US, "governor: session settled at %.0f%%\n", scale * 100));
            }
        } catch (Throwable ignored) { }
    }

    // ---- the governor -----------------------------------------------------------------

    private static void govern(Context context, DeviceProfile profile) {
        // The ceiling is whatever the launcher had already chosen for this device, so the
        // engine can never draw more pixels than the user or the GPU tier asked for.
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        // The ceiling is the scale the launcher chose for this device, recorded once and
        // then left alone. Using the live preference instead would ratchet the ceiling
        // down every session: the engine writes its result back to that preference, so
        // after one hard session the device would be permanently stuck at the low scale
        // with no way back up.
        float ceiling = LauncherPreferences.PREF_SCALE_FACTOR;
        if (ceiling <= 0f) ceiling = 0.7f;
        if (prefs != null) {
            int stored = prefs.getInt(KEY_CEILING, -1);
            if (stored > 0) ceiling = stored / 100f;
            else prefs.edit().putInt(KEY_CEILING, Math.round(ceiling * 100f)).apply();
        }
        float floor = Math.max(FLOOR_SCALE, ceiling - 0.30f);

        // Start where the last session settled rather than at the ceiling, so a device
        // that cannot hold the target does not spend the first minutes of every session
        // relearning it. Clamped to this session's range, so a changed GPU tier or a
        // hand-set scale overrides it.
        float scale = ceiling;
        if (prefs != null) {
            int learned = prefs.getInt(KEY_LEARNED, -1);
            if (learned > 0) {
                float want = clamp(learned / 100f, floor, ceiling);
                if (want < ceiling - 0.001f) {
                    scale = want;
                    applyScale(scale);
                    appendTrace(String.format(Locale.US,
                            "governor: starting at the learned %.0f%% (ceiling %.0f%%)\n",
                            scale * 100, ceiling * 100));
                }
            }
        }

        double target = profile.targetFps;
        long lastFrames = -1L;
        long lastWall = 0L;
        int sagTicks = 0;
        int headroomTicks = 0;

        appendTrace(String.format(Locale.US,
                "governor: target %.0f fps, scale %.0f%%..%.0f%%, starting at %.0f%%\n",
                target, floor * 100, ceiling * 100, scale * 100));

        while (sRunning) {
            try {
                long frames = presentedFrames();
                if (frames < 0) {
                    // No presented-frame counter means no honest frame rate, and acting on a
                    // guessed one is exactly the mistake that once drove the scale to its
                    // floor for no reason. Stand down.
                    appendTrace("governor: no presented-frame counter, standing down\n");
                    return;
                }
                long now = System.currentTimeMillis();
                if (lastFrames < 0 || lastWall == 0) {
                    lastFrames = frames;
                    lastWall = now;
                    Thread.sleep(TICK_MS);
                    continue;
                }
                double dt = (now - lastWall) / 1000.0;
                double fps = dt > 0 ? (frames - lastFrames) / dt : -1;
                lastFrames = frames;
                lastWall = now;

                int thermal = thermalStatus(context);

                if (fps < 0) {
                    Thread.sleep(TICK_MS);
                    continue;
                }

                if (thermal >= 3) {
                    // The device is throttling itself. Give it fewer pixels immediately -
                    // waiting for the frame rate to sag first only makes the heat worse.
                    if (scale > floor) {
                        float next = Math.max(floor, scale - STEP);
                        appendTrace(String.format(Locale.US,
                                "governor: thermal %d, scale %.0f%% -> %.0f%%\n",
                                thermal, scale * 100, next * 100));
                        scale = next;
                        applyScale(scale);
                    }
                    sagTicks = 0;
                    headroomTicks = 0;
                } else if (fps < target * SAG) {
                    sagTicks++;
                    headroomTicks = 0;
                    // Far below target: do not wait out the hysteresis, drop twice at once.
                    // Two seconds of single-step waiting is two seconds the session spends
                    // unplayable, and the first correction is always the most valuable.
                    boolean desperate = fps < target * DESPERATE;
                    if ((sagTicks >= SAG_TICKS || desperate) && scale > floor) {
                        float next = Math.max(floor, scale - (desperate ? STEP * 2f : STEP));
                        appendTrace(String.format(Locale.US,
                                "governor: %.0f fps under %.0f, scale %.0f%% -> %.0f%%\n",
                                fps, target * SAG, scale * 100, next * 100));
                        scale = next;
                        applyScale(scale);
                        sagTicks = 0;
                    }
                } else if (fps > target * HEADROOM) {
                    headroomTicks++;
                    sagTicks = 0;
                    if (headroomTicks >= HEADROOM_TICKS && scale < ceiling && thermal <= 1) {
                        float next = Math.min(ceiling, scale + STEP);
                        appendTrace(String.format(Locale.US,
                                "governor: %.0f fps with headroom, scale %.0f%% -> %.0f%%\n",
                                fps, scale * 100, next * 100));
                        scale = next;
                        applyScale(scale);
                        headroomTicks = 0;
                    }
                } else {
                    sagTicks = 0;
                    headroomTicks = 0;
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                Log.w(TAG, "governor tick failed", t);
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static long presentedFrames() {
        try {
            return JREUtils.getPresentedFrameCount();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm == null ? -1 : pm.getCurrentThermalStatus();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Moves the render resolution, live. */
    private static void applyScale(float scale) {
        try {
            sLastScale = scale;
            LauncherPreferences.PREF_SCALE_FACTOR = scale;
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            if (prefs != null) {
                // resolutionRatio persists the choice; fear_auto_scale_last marks the engine
                // as the writer, so the one-time "a bad run drove the scale to the floor"
                // repair in LauncherPreferences knows this was a deliberate move.
                int pct = Math.round(scale * 100f);
                prefs.edit()
                        .putInt("resolutionRatio", pct)
                        .putInt("fear_auto_scale_last", pct)
                        .apply();
            }
            MinecraftGLSurface surface = sSurface;
            if (surface != null) {
                // refreshSize re-reads PREF_SCALE_FACTOR and asks the surface provider for a
                // new buffer size, which is the same path a settings change uses.
                surface.post(surface::refreshSize);
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not apply the render scale", t);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void appendTrace(String line) {
        Log.i(TAG, line.trim());
        File f = sTrace;
        if (f == null) return;
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    // ---- what the device is -----------------------------------------------------------

    /** A description of the hardware the engine is deciding for. */
    public static final class DeviceProfile {
        public String gpuVendor = "?";
        public String gpuModel = "?";
        public String gpuClass = "unknown";   // adreno | mali | powervr | other
        public int glesMajor;
        public int cores;
        public int ramMb;
        public double refreshHz = 60;
        public double targetFps = 60;
        public boolean vulkan;
        public String tier = "mid";           // low | mid | high

        static DeviceProfile of(Context context) {
            DeviceProfile p = new DeviceProfile();
            try {
                p.cores = Runtime.getRuntime().availableProcessors();
            } catch (Throwable ignored) { }
            try {
                GLInfoUtils.GLInfo gl = GLInfoUtils.getGlInfo();
                p.gpuVendor = gl.vendor == null ? "?" : gl.vendor;
                p.gpuModel = gl.renderer == null ? "?" : gl.renderer;
                p.glesMajor = gl.glesMajorVersion;
                if (gl.isAdreno()) p.gpuClass = "adreno";
                else if (gl.isArm()) p.gpuClass = "mali";
                else if (p.gpuModel.toLowerCase(Locale.US).contains("powervr")) p.gpuClass = "powervr";
            } catch (Throwable ignored) { }
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    p.ramMb = (int) (mi.totalMem / (1024L * 1024L));
                }
            } catch (Throwable ignored) { }
            try {
                android.view.WindowManager wm = (android.view.WindowManager)
                        context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && wm.getDefaultDisplay() != null) {
                    p.refreshHz = wm.getDefaultDisplay().getRefreshRate();
                }
            } catch (Throwable ignored) { }
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                p.vulkan = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
                        && pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
            } catch (Throwable ignored) { }
            try {
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                if (dm != null && p.refreshHz <= 0) p.refreshHz = 60;
            } catch (Throwable ignored) { }

            // The target is whatever the panel can actually show, not a hardcoded 60. On a
            // 120 Hz panel the governor should aim at 120 rather than fighting the user's
            // own display; on a 60 Hz panel nothing changes. The engine never caps the
            // game's own maxFps - that stays at the game's ceiling. This only decides
            // where the governor stops spending pixels on the target.
            p.targetFps = Math.max(30, Math.min(120, p.refreshHz));

            if (p.cores >= 8 && p.ramMb >= 6000) p.tier = "high";
            else if (p.cores <= 4 || p.ramMb < 3000) p.tier = "low";
            else p.tier = "mid";
            return p;
        }

        public String describe() {
            return String.format(Locale.US,
                    "device: gpu=%s (%s, class=%s, gles %d), cores=%d, ram=%d MB, refresh=%.0f Hz, "
                            + "vulkan=%s, tier=%s, target=%.0f fps",
                    gpuModel, gpuVendor, gpuClass, glesMajor, cores, ramMb, refreshHz,
                    vulkan, tier, targetFps);
        }
    }

    // ---- which driver the renderer should run on --------------------------------------

    /**
     * Turns the device profile into a driver decision. The launcher still performs the
     * actual native call - this is where the choice is made and written down, so there is
     * one place that knows why a device runs on the driver it does.
     */
    static final class DriverDirector {
        static String plan(DeviceProfile p, String renderer) {
            StringBuilder sb = new StringBuilder();
            sb.append("driver plan for renderer '").append(renderer).append("':\n");
            switch (p.gpuClass) {
                case "adreno":
                    // Adreno's own Vulkan driver is closed and old; Mesa's Turnip is the
                    // open one that Zink actually wants.
                    sb.append("  gpu: Adreno -> prefer Mesa Turnip ICD under Zink");
                    if (p.tier.equals("low")) sb.append(" (low tier: keep the system driver if Turnip is missing)");
                    sb.append('\n');
                    break;
                case "mali":
                    // Mali's vendor Vulkan driver is what Zink has been validated against
                    // here; PanVK is still too young to be a default.
                    sb.append("  gpu: Mali -> system Vulkan driver under Zink, PanVK only if asked\n");
                    break;
                default:
                    sb.append("  gpu: unknown -> system Vulkan driver under Zink\n");
                    break;
            }
            if (!p.vulkan) {
                sb.append("  no Vulkan: Zink cannot run, an OpenGL ES renderer is the only option\n");
            }
            if (renderer != null && renderer.startsWith("plugin:")) {
                sb.append("  renderer is a plugin, the plugin owns its own driver\n");
            }
            sb.append(String.format(Locale.US, "  draw: %s tier, target %.0f fps, panel %.0f Hz\n",
                    p.tier, p.targetFps, p.refreshHz));
            return sb.toString();
        }
    }
}
