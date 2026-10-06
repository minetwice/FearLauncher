package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * FEAR: prepares the MobileGlues renderer that ships inside the APK.
 *
 * <p>MobileGlues reads its tuning from {@code $MG_DIR_PATH/config.json} and, when
 * {@code MG_DIR_PATH} is unset, falls back to a hard-coded "unsupported launcher"
 * profile that disables the compute shader and the GLSL cache. Pointing
 * {@code MG_DIR_PATH} at a directory we own is therefore what makes our tuned
 * config take effect at all.</p>
 *
 * <p>The directory lives under the app's private files dir, which is writable
 * without any storage permission, and the config is written only when absent so a
 * player's own edits are never clobbered. Every failure is swallowed: a missing
 * config simply means MobileGlues uses its defaults.</p>
 */
public final class MobileGluesConfig {
    private static final String TAG = "MobileGluesConfig";
    private static final String DIR_NAME = "MobileGlues";
    private static final String CONFIG_NAME = "config.json";

    /**
     * The tuned profile. Keys and ranges are taken from MobileGlues'
     * {@code config/config.cpp} and {@code config/settings.cpp}.
     *
     * <p>{@code multidrawOrder} is a comma-separated string, not a JSON array:
     * MobileGlues reads it with {@code config_get_string()}, which only accepts a
     * JSON string (an array is silently ignored and the built-in default order is
     * used instead). The tokens here are exactly the backend names the parser
     * accepts - native, multiindirect, multibasevertex, multiarrays, indirect,
     * basevertex, unroll, compute - listed best-first, which is MobileGlues' own
     * default global order.</p>
     */
    private static final String CONFIG_JSON =
            "{\n"
            + "  \"enableANGLE\": 0,\n"
            + "  \"enableNoError\": 0,\n"
            + "  \"enableExtComputeShader\": 1,\n"
            + "  \"enableExtTimerQuery\": 1,\n"
            + "  \"enableExtDirectStateAccess\": 1,\n"
            + "  \"angleDepthClearFixMode\": 0,\n"
            + "  \"customGLVersion\": 0,\n"
            + "  \"fsr1Setting\": 0,\n"
            + "  \"hideMGEnvLevel\": 0,\n"
            + "  \"maxGlslCacheSize\": 128,\n"
            + "  \"multidrawOrder\": \"native,multiindirect,multibasevertex,multiarrays,indirect,basevertex,unroll,compute\"\n"
            + "}\n";

    private MobileGluesConfig() {
    }

    /**
     * Ensures the launcher-owned MobileGlues directory exists, seeds the tuned
     * {@code config.json} if the user has not written one, and points
     * {@code MG_DIR_PATH} at it. Never throws.
     *
     * <p>Must be called before the MobileGlues native library is loaded, so the
     * library sees the environment variable on its first read.</p>
     */
    public static void prepare(Context context) {
        try {
            File dir = new File(context.getFilesDir(), DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                Log.w(TAG, "Could not create the MobileGlues directory " + dir.getAbsolutePath());
                return;
            }

            File config = new File(dir, CONFIG_NAME);
            if (!config.exists()) {
                try (FileOutputStream out = new FileOutputStream(config)) {
                    out.write(CONFIG_JSON.getBytes(StandardCharsets.UTF_8));
                }
                Log.i(TAG, "Wrote the tuned MobileGlues config at " + config.getAbsolutePath());
            } else {
                Log.i(TAG, "Keeping the existing MobileGlues config at " + config.getAbsolutePath());
            }

            Os.setenv("MG_DIR_PATH", dir.getAbsolutePath(), true);
            Log.i(TAG, "MG_DIR_PATH set to " + dir.getAbsolutePath());
        } catch (Throwable t) {
            Log.w(TAG, "Could not prepare the MobileGlues config; it will use its defaults", t);
        }
    }
}
