package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Environment;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * without any storage permission. A small {@code .profile_version} marker file next
 * to the config records which tuning version was last written: the config is
 * (re)written when it is absent or when the marker does not match the current
 * {@link #PROFILE_VERSION}, and left completely alone otherwise so a player's own
 * edits are never clobbered. Every failure is swallowed: a missing config simply
 * means MobileGlues uses its defaults.</p>
 */
public final class MobileGluesConfig {
    private static final String TAG = "MobileGluesConfig";
    private static final String DIR_NAME = "MobileGlues";
    private static final String CONFIG_NAME = "config.json";
    private static final String VERSION_NAME = ".profile_version";

    /**
     * Bump this whenever the tuned {@link #CONFIG_JSON} below changes. Devices that
     * already carry a config.json from an older build then receive the new tuning on
     * their next launch, while a config edited at the current version is preserved.
     */
    private static final int PROFILE_VERSION = 4;

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
     *
     * <p>{@code maxGlslCacheSize} is 256 rather than 128 so translated shaders are
     * compiled once and reused instead of being recompiled mid-fight - that
     * recompilation is exactly the hitch we are removing.</p>
     */
    private static final String CONFIG_JSON =
            "{\n"
            + "  \"enableANGLE\": 0,\n"
            + "  \"enableNoError\": 2,\n"
            + "  \"enableExtComputeShader\": 1,\n"
            + "  \"enableExtTimerQuery\": 1,\n"
            + "  \"enableExtDirectStateAccess\": 1,\n"
            + "  \"angleDepthClearFixMode\": 0,\n"
            + "  \"customGLVersion\": 0,\n"
            + "  \"fsr1Setting\": 0,\n"
            + "  \"hideMGEnvLevel\": 0,\n"
            + "  \"maxGlslCacheSize\": 256,\n"
            + "  \"multidrawOrder\": \"native,multiindirect,multibasevertex,multiarrays,indirect,basevertex,unroll,compute\"\n"
            + "}\n";

    /** Matches the numeric {@code fsr1Setting} value so it can be rewritten in place. */
    private static final Pattern FSR1_PATTERN =
            Pattern.compile("(\"fsr1Setting\"\\s*:\\s*)(-?\\d+)");

    private MobileGluesConfig() {
    }

    /**
     * Ensures the launcher-owned MobileGlues directory exists, (re)writes the tuned
     * {@code config.json} when it is absent or was written by a different profile
     * version, and points {@code MG_DIR_PATH} at it. A config whose stored profile
     * version matches is left untouched. Never throws.
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
            File versionFile = new File(dir, VERSION_NAME);

            if (!config.exists() || readVersion(versionFile) != PROFILE_VERSION) {
                writeConfig(config, CONFIG_JSON);
                writeVersion(versionFile);
                Log.i(TAG, "Wrote MobileGlues profile v" + PROFILE_VERSION + " config at "
                        + config.getAbsolutePath());
            } else {
                Log.i(TAG, "Keeping the existing MobileGlues config at " + config.getAbsolutePath());
            }

            Os.setenv("MG_DIR_PATH", dir.getAbsolutePath(), true);
            Log.i(TAG, "MG_DIR_PATH set to " + dir.getAbsolutePath());
            deleteLegacyExternalConfig();
        } catch (Throwable t) {
            Log.w(TAG, "Could not prepare the MobileGlues config; it will use its defaults", t);
        }
    }

    /**
     * Rewrites {@code config.json} with a new {@code fsr1Setting} value (0 Disabled,
     * 1 UltraQuality, 2 Quality, 3 Balanced, 4 Performance), preserving every other
     * key and keeping the profile version marker in sync so a later
     * {@link #prepare(Context)} does not undo the change. Used by the Performance
     * dashboard's FSR1 and Smooth PvP switches.
     *
     * @return true if the config was rewritten.
     */
    public static boolean setFsr1Setting(Context context, int preset) {
        try {
            File dir = new File(context.getFilesDir(), DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) return false;

            File config = new File(dir, CONFIG_NAME);
            String json = readFile(config);
            if (json == null) json = CONFIG_JSON;

            writeConfig(config, withFsr1Setting(json, preset));
            writeVersion(new File(dir, VERSION_NAME));
            Log.i(TAG, "Set fsr1Setting=" + preset + " in " + config.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Could not set the MobileGlues FSR1 setting", t);
            return false;
        }
    }

    /**
     * Deletes the stale {@code <externalStorage>/MG/config.json} that older builds
     * wrote. MobileGlues still reads that path and warns about its deprecated keys
     * ({@code multidrawMode}/{@code multidrawDisableBackends}) even though the launcher
     * no longer writes it. Only that one file is removed - never the directory, never
     * anything else on external storage. Never throws: unreadable storage is logged
     * and ignored.
     */
    private static void deleteLegacyExternalConfig() {
        try {
            File legacy = new File(new File(Environment.getExternalStorageDirectory(), "MG"),
                    CONFIG_NAME);
            if (legacy.isFile()) {
                if (legacy.delete()) {
                    Log.i(TAG, "Removed the stale external MobileGlues config at "
                            + legacy.getAbsolutePath());
                } else {
                    Log.w(TAG, "Could not remove the stale external MobileGlues config at "
                            + legacy.getAbsolutePath());
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not inspect the external MobileGlues config; continuing", t);
        }
    }

    private static void writeConfig(File config, String json) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(config)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Replaces the {@code fsr1Setting} value in {@code json}, or inserts it if absent. */
    private static String withFsr1Setting(String json, int preset) {
        Matcher matcher = FSR1_PATTERN.matcher(json);
        if (matcher.find()) {
            // Rebuild by hand rather than via replaceFirst: "$1" + a multi-digit preset
            // would be read as a group reference (e.g. "$11"), not group 1 then the value.
            return json.substring(0, matcher.start())
                    + matcher.group(1) + preset
                    + json.substring(matcher.end());
        }

        int brace = json.lastIndexOf('}');
        if (brace < 0) return json;
        String head = json.substring(0, brace);
        String separator = head.trim().endsWith("{") ? "\n  " : ",\n  ";
        return head + separator + "\"fsr1Setting\": " + preset + "\n" + json.substring(brace);
    }

    private static int readVersion(File versionFile) {
        String raw = readFile(versionFile);
        if (raw == null) return -1;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void writeVersion(File versionFile) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(versionFile)) {
            out.write(Integer.toString(PROFILE_VERSION).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readFile(File file) {
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0, read;
            while (offset < data.length && (read = in.read(data, offset, data.length - offset)) > 0) {
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
}
