package net.kdt.pojavlaunch.prefs;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;

import android.app.Activity;
import android.content.*;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.fearlauncher.fear.R;

public class LauncherPreferences {
    public static final String RENDERER_MH_DRIVE = "mh_drive";
    public static final String PREF_KEY_CURRENT_INSTANCE = "currentInstance";
    public static final String PREF_KEY_SKIP_NOTIFICATION_CHECK = "skipNotificationPermissionCheck";

    public static SharedPreferences DEFAULT_PREF;
    public static String PREF_RENDERER = "fear_v1";

	public static boolean PREF_IGNORE_NOTCH = false;
	public static float PREF_BUTTONSIZE = 100f;
	public static float PREF_MOUSESCALE = 1f;
	public static int PREF_LONGPRESS_TRIGGER = 300;
	public static String PREF_DEFAULTCTRL_PATH = Tools.CTRLDEF_FILE;
	public static String PREF_CUSTOM_JAVA_ARGS;
    public static boolean PREF_FORCE_ENGLISH = false;
    public static final String PREF_VERSION_REPOS = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static boolean PREF_DISABLE_GESTURES = false;
    public static boolean PREF_DISABLE_SWAP_HAND = false;
    public static float PREF_MOUSESPEED = 1f;
    public static int PREF_RAM_ALLOCATION;
    public static String PREF_DEFAULT_RUNTIME;
    public static boolean PREF_SUSTAINED_PERFORMANCE = false;
    public static boolean PREF_VIRTUAL_MOUSE_START = false;
    public static boolean PREF_USE_ALTERNATE_SURFACE = true;
    public static boolean PREF_JAVA_SANDBOX = true;
    public static float PREF_SCALE_FACTOR = 1f;

    public static boolean PREF_ENABLE_GYRO = false;
    public static float PREF_GYRO_SENSITIVITY = 1f;
    public static int PREF_GYRO_SAMPLE_RATE = 16;
    public static boolean PREF_GYRO_SMOOTHING = true;
    public static boolean PREF_GYRO_INVERT_X = false;
    public static boolean PREF_GYRO_INVERT_Y = false;

    public static boolean PREF_FORCE_VSYNC = false;

    /** FEARPATCH: auto-tune the game's options.txt for frame rate (non-root). */
    public static boolean PREF_PERFORMANCE_MODE = true;
    /** Stops the home screen's background clip and idle animations for a fluid UI. */
    public static boolean PREF_SMOOTH_LAUNCHER_UI = false;

    public static boolean PREF_USE_ANGLE = false;

    public static boolean PREF_BUTTON_ALL_CAPS = true;
    public static boolean PREF_DUMP_SHADERS = false;
    public static float PREF_DEADZONE_SCALE = 1f;
    public static boolean PREF_BIG_CORE_AFFINITY = false;
    public static boolean PREF_ZINK_PREFER_SYSTEM_DRIVER = false;
    
    public static boolean PREF_VERIFY_MANIFEST = true;
    public static String PREF_DOWNLOAD_SOURCE = "default";
    public static boolean PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = false;
    public static boolean PREF_VSYNC_IN_ZINK = false;

    public static boolean PREF_RAPID_START = true;
    public static boolean PREF_VERIFY_FILES = true;

    public static boolean PREF_FREEDRENO_SYSMEM = false;


    public static void loadPreferences(Context ctx) {
        //Required for CTRLDEF_FILE and MultiRT
        Tools.initStorageConstants(ctx);
        boolean isDevicePowerful = isDevicePowerful(ctx);

        PREF_RENDERER = DEFAULT_PREF.getString("renderer", "fear_v1");
        // One-time fix: older builds persisted "opengles2" - the old
        // pref_video.xml defaultValue - as the renderer. That id is no longer in
        // the renderer list, so the picker showed nothing selected and the launch
        // fell back to a different renderer. Move it to LTW (the launcher
        // default); the user can still change it by hand afterwards.
        if ("opengles2".equals(PREF_RENDERER)) {
            PREF_RENDERER = "opengles3_ltw";
            DEFAULT_PREF.edit().putString("renderer", PREF_RENDERER).apply();
        }
        // One-time move to MobileGlues: it is now the launcher default and ships
        // inside the APK, so an existing user who never picked a renderer (still on
        // the old LTW default) gets it automatically. Only migrate when the native
        // library is actually present, and never touch a renderer the user chose.
        if (!DEFAULT_PREF.getBoolean("fear_renderer_mobileglues_migrated", false)) {
            if ("opengles3_ltw".equals(PREF_RENDERER)
                    && new File(Tools.NATIVE_LIB_DIR, "libmobileglues.so").exists()) {
                PREF_RENDERER = "fear_v1";
                DEFAULT_PREF.edit().putString("renderer", PREF_RENDERER).apply();
            }
            DEFAULT_PREF.edit().putBoolean("fear_renderer_mobileglues_migrated", true).apply();
        }
        PREF_BUTTONSIZE = DEFAULT_PREF.getInt("buttonscale", 100);
        PREF_MOUSESCALE = DEFAULT_PREF.getInt("mousescale", 100)/100f;
        PREF_MOUSESPEED = ((float)DEFAULT_PREF.getInt("mousespeed",100))/100f;
        PREF_IGNORE_NOTCH = DEFAULT_PREF.getBoolean("ignoreNotch", false);
		PREF_LONGPRESS_TRIGGER = DEFAULT_PREF.getInt("timeLongPressTrigger", 300);
		PREF_DEFAULTCTRL_PATH = DEFAULT_PREF.getString("defaultCtrl", Tools.CTRLDEF_FILE);
        PREF_FORCE_ENGLISH = DEFAULT_PREF.getBoolean("force_english", false);
        PREF_DISABLE_GESTURES = DEFAULT_PREF.getBoolean("disableGestures",false);
        PREF_DISABLE_SWAP_HAND = DEFAULT_PREF.getBoolean("disableDoubleTap", false);
        PREF_RAM_ALLOCATION = DEFAULT_PREF.getInt("allocation", findBestRAMAllocation(ctx));
        // One-time lift: an allocation saved before that rescale is stuck at the old
        // 2048 ceiling and would never pick the new value up on its own. It can still be
        // changed by hand afterwards.
        if (!DEFAULT_PREF.getBoolean("fear_ram_rescaled", false)) {
            int suggested = findBestRAMAllocation(ctx);
            if (PREF_RAM_ALLOCATION < suggested) {
                PREF_RAM_ALLOCATION = suggested;
                DEFAULT_PREF.edit().putInt("allocation", suggested).apply();
            }
            DEFAULT_PREF.edit().putBoolean("fear_ram_rescaled", true).apply();
        }
        // One-time reset: older builds shipped pref_video.xml with
        // android:defaultValue="true" for vsync_in_zink, and PreferenceManager
        // persists XML defaults into SharedPreferences on first run. On any device
        // that has ever launched the app the key is therefore stored as true, which
        // beats the getBoolean(key, false) fallback below and forces the GL swap
        // interval (FEAR_VSYNC_IN_ZINK=1), capping the game at the panel refresh.
        // Clear it once so the new false default actually takes effect; the user can
        // still turn it back on by hand afterwards.
        if (!DEFAULT_PREF.getBoolean("fear_vsync_in_zink_reset", false)) {
            PREF_VSYNC_IN_ZINK = false;
            DEFAULT_PREF.edit().putBoolean("vsync_in_zink", false).apply();
            DEFAULT_PREF.edit().putBoolean("fear_vsync_in_zink_reset", true).apply();
        }
        PREF_CUSTOM_JAVA_ARGS = DEFAULT_PREF.getString("javaArgs", "");
        PREF_SUSTAINED_PERFORMANCE = DEFAULT_PREF.getBoolean("sustainedPerformance", isDevicePowerful);
        PREF_VIRTUAL_MOUSE_START = DEFAULT_PREF.getBoolean("mouse_start", false);
        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful);
        PREF_JAVA_SANDBOX = DEFAULT_PREF.getBoolean("java_sandbox", true);
        // One-time: pick the render-resolution scale from the device GPU for anyone who
        // has never touched the resolution slider, so a mid-range GPU gets a real
        // reduction without the user having to hunt for the slider. The GL renderer
        // string is the same source of truth the launcher already logs as
        // "Graphics device: ..." (GLInfoUtils), not a new detection path. A value the
        // user has already chosen is never overwritten, and the slider still wins from
        // then on. The choice is remembered by its own one-time flag.
        if (!DEFAULT_PREF.getBoolean("fear_gpu_scale_applied", false)) {
            if (!DEFAULT_PREF.contains("resolutionRatio")) {
                String gpuRenderer = GLInfoUtils.getGlInfo().renderer;
                int gpuScale = gpuTierResolutionScale(gpuRenderer);
                DEFAULT_PREF.edit().putInt("resolutionRatio", gpuScale).apply();
                Log.i("LauncherPreferences", "GPU-tier resolution: renderer=\"" + gpuRenderer
                        + "\" -> " + gpuScale + "% of the surface");
            }
            DEFAULT_PREF.edit().putBoolean("fear_gpu_scale_applied", true).apply();
        }
        float resolutionScale = DEFAULT_PREF.getInt("resolutionRatio",
                findBestResolution(ctx, isDevicePowerful))/100f;
        if (DEFAULT_PREF.getBoolean("performance_mode", true)) {
            // Performance mode draws no more pixels than a 720-class surface. The number of
            // pixels is by far the biggest lever on frame time on a phone, and this is the
            // one place the launcher can cut it without touching the game's own settings.
            // The resolution slider still lets you go lower than the cap. The GPU tier is
            // the better signal for what the device can draw, so it raises (never lowers)
            // that ceiling and the GPU-tier scale picked above is never capped away.
            float cap = Math.max(findBestResolution(ctx, false),
                    gpuTierResolutionScale(GLInfoUtils.getGlInfo().renderer)) / 100f;
            if (resolutionScale > cap) resolutionScale = cap;
        }
        PREF_SCALE_FACTOR = resolutionScale;
        PREF_ENABLE_GYRO = DEFAULT_PREF.getBoolean("enableGyro", false);
        PREF_GYRO_SENSITIVITY = ((float)DEFAULT_PREF.getInt("gyroSensitivity", 100))/100f;
        PREF_GYRO_SAMPLE_RATE = DEFAULT_PREF.getInt("gyroSampleRate", 16);
        PREF_GYRO_SMOOTHING = DEFAULT_PREF.getBoolean("gyroSmoothing", true);
        PREF_GYRO_INVERT_X = DEFAULT_PREF.getBoolean("gyroInvertX", false);
        PREF_GYRO_INVERT_Y = DEFAULT_PREF.getBoolean("gyroInvertY", false);
        PREF_PERFORMANCE_MODE = DEFAULT_PREF.getBoolean("performance_mode", true);
        PREF_SMOOTH_LAUNCHER_UI = DEFAULT_PREF.getBoolean("smooth_launcher_ui", false);
        // Default the VSync switch off while performance mode is on: syncing to the panel
        // caps frames no matter what the game asks for, which is the opposite of what a
        // performance mode is for. A deliberate choice by the user still wins.
        PREF_FORCE_VSYNC = DEFAULT_PREF.getBoolean("force_vsync",
                isDevicePowerful && !PREF_PERFORMANCE_MODE);
        PREF_USE_ANGLE = DEFAULT_PREF.getBoolean("use_angle", false);
        PREF_BUTTON_ALL_CAPS = DEFAULT_PREF.getBoolean("buttonAllCaps", true);
        PREF_DUMP_SHADERS = DEFAULT_PREF.getBoolean("dump_shaders", false);
        PREF_DEADZONE_SCALE = ((float) DEFAULT_PREF.getInt("gamepad_deadzone_scale", 100))/100f;
        PREF_BIG_CORE_AFFINITY = DEFAULT_PREF.getBoolean("bigCoreAffinity", false);
        PREF_ZINK_PREFER_SYSTEM_DRIVER = DEFAULT_PREF.getBoolean("zinkPreferSystemDriver", false);
        PREF_DOWNLOAD_SOURCE = DEFAULT_PREF.getString("downloadSource", "default");
        PREF_VERIFY_MANIFEST = DEFAULT_PREF.getBoolean("verifyManifest", true);
        PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = DEFAULT_PREF.getBoolean(PREF_KEY_SKIP_NOTIFICATION_CHECK, false);
        PREF_VSYNC_IN_ZINK = DEFAULT_PREF.getBoolean("vsync_in_zink", false);
        PREF_VERIFY_FILES = DEFAULT_PREF.getBoolean("checkGameFiles", true);
        PREF_RAPID_START = DEFAULT_PREF.getBoolean("fastStartupCheck", true);
        PREF_FREEDRENO_SYSMEM = DEFAULT_PREF.getBoolean("freedrenoSysmem", false);

        String argLwjglLibname = "-Dorg.lwjgl.opengl.libname=";
        for (String arg : JREUtils.parseJavaArguments(PREF_CUSTOM_JAVA_ARGS)) {
            if (arg.startsWith(argLwjglLibname)) {
                // purge arg
                DEFAULT_PREF.edit().putString("javaArgs",
                    PREF_CUSTOM_JAVA_ARGS.replace(arg, "")).apply();
            }
        }
        if(DEFAULT_PREF.contains("defaultRuntime")) {
            PREF_DEFAULT_RUNTIME = DEFAULT_PREF.getString("defaultRuntime","");
        }else{
            if(MultiRTUtils.getRuntimes().isEmpty()) {
                PREF_DEFAULT_RUNTIME = "";
                return;
            }
            PREF_DEFAULT_RUNTIME = MultiRTUtils.getRuntimes().get(0).name;
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultRuntime",LauncherPreferences.PREF_DEFAULT_RUNTIME).apply();
        }
    }

    /**
     * This functions aims at finding the best default RAM amount,
     * according to the RAM amount of the physical device.
     * Put not enough RAM ? Minecraft will lag and crash.
     * Put too much RAM ?
     * The GC will lag, android won't be able to breathe properly.
     * @param ctx Context needed to get the total memory of the device.
     * @return The best default value found.
     */
    private static int findBestRAMAllocation(Context ctx){
        int deviceRam = Tools.getTotalDeviceMemory(ctx);
        if (deviceRam < 1024) return 296;
        if (deviceRam < 1536) return 448;
        if (deviceRam < 2048) return 656;
        // Limit the max for 32 bits devices more harshly
        if (is32BitsDevice()) return 696;

        if (deviceRam < 3064) return 936;
        if (deviceRam < 4096) return 1144;
        if (deviceRam < 6144) return 1536;
        // FEARPATCH: the old table stopped at 2048 however much the device had. A 1.21
        // instance with Iris and a mod list wants far more than that, and the shortfall
        // showed up as GC time while the mods were loading. Scale with the device, and
        // keep roughly a quarter in reserve for the OS, the GL driver and the native
        // allocations the JVM heap does not cover.
        if (deviceRam < 8192) return 3072;
        return 4096;
    }

    /**
     * Picks a render-resolution scale (as a percent of the surface) from the device's
     * GPU, read from the GL renderer string.
     *
     * <p>The renderer string is the same source of truth the launcher already logs as
     * {@code Graphics device: ...} (see {@link GLInfoUtils}), so no new detection path is
     * introduced. A high-end GPU is left almost alone, a mid-range one gets a real
     * reduction, and anything unrecognised gets a middle value. The result is clamped to
     * 50..100 so a mapping mistake can never ask for an absurd surface.</p>
     */
    private static int gpuTierResolutionScale(String renderer) {
        String lower = renderer == null ? "" : renderer.toLowerCase(Locale.US);
        int scale;
        if (lower.contains("immortalis") || lower.contains("xclipse")
                || isAdrenoSeries(lower, '7') || isAdrenoSeries(lower, '8')) {
            scale = 90; // high-end GPU
        } else if (isAdrenoSeries(lower, '6')
                || lower.contains("mali-g6")
                || lower.contains("mali-g57") || lower.contains("mali-g68")
                || lower.contains("mali-g77") || lower.contains("mali-g78")) {
            scale = 70; // mid-range GPU
        } else if (lower.contains("mali-g7")) {
            // Mali-G7xx (G710/G715/...); G77/G78 are handled as mid-range above.
            scale = 90; // high-end GPU
        } else {
            scale = 80; // unknown / everything else
        }
        return Math.max(50, Math.min(100, scale));
    }

    /** True when the renderer names an Adreno of the given hundreds series (e.g. '7' -> 7xx). */
    private static boolean isAdrenoSeries(String lowerRenderer, char series) {
        int adreno = lowerRenderer.indexOf("adreno");
        if (adreno < 0) return false;
        for (int i = adreno + "adreno".length(); i < lowerRenderer.length(); i++) {
            char c = lowerRenderer.charAt(i);
            if (Character.isDigit(c)) return c == series;
        }
        return false;
    }

    /// Find a correct resolution for the device
    ///
    /// Some devices are shipped with a ridiculously high resolution, which can cause performance issues
    /// This function will try to find a resolution that is good enough for the device
    private static int findBestResolution(Context context, boolean isDevicePowerful) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int minSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        int targetSide = isDevicePowerful ? 1080 : 720;
        if (minSide <= targetSide) return 100; // No need to scale down

        float ratio = (100f * targetSide / minSide);
        // The value must match the seekbar values
        int increment = context.getResources().getInteger(R.integer.resolution_seekbar_increment);
        return (int) (Math.ceil(ratio / increment) * increment);
    }

    /// Check if the device is considered powerful.
    /// Powerful devices will have some energy saving tweaks enabled by default
    private static boolean isDevicePowerful(Context context) {
        if (SDK_INT < Build.VERSION_CODES.Q) return false;
        if (Tools.getTotalDeviceMemory(context) <= 4096) return false;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (Math.min(metrics.widthPixels, metrics.heightPixels) < 1080) return false;
        if (Runtime.getRuntime().availableProcessors() <= 4) return false;
        if (hasAllCoreSameFreq()) return false;
        return true;
    }

    private static boolean hasAllCoreSameFreq() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        try {
            String freq0 = Tools.read("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String freqX = Tools.read("/sys/devices/system/cpu/cpu" + (coreCount - 1) + "/cpufreq/cpuinfo_max_freq");
            if(freq0.equals(freqX)) return true;
        } catch (IOException e) {
            Log.e("LauncherPreferences", "Failed to read CPU frequencies", e);
        }
        return false;
    }

    /** Check if the device has a display cutout */
    public static boolean hasNotch(Activity activity) {
        if (Build.VERSION.SDK_INT < P) return false;
        try {
            final Rect cutout;
            if(SDK_INT >= Build.VERSION_CODES.S){
                cutout = activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            } else {
                cutout = activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            }
            return cutout.width() != 0 || cutout.height() != 0;
        }catch (Exception e){
            Log.i("NOTCH DETECTION", "No notch detected, or the device if in split screen mode");
            return false;
        }
    }
}