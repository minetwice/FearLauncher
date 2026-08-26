package net.kdt.pojavlaunch.quasar.shield;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ShaderShield — extreme multi-stage GLSL protection for Mali/Adreno.
 *
 * Architecture:
 *   CPU-side shield  →  50 specialized transforms  →  safe source  →  GPU
 *
 * Stages (ordered for maximum speed):
 *   0. Cache hit
 *   1. needsShield() probe — skip clean shaders
 *   2. Extension vault strip + join
 *   3. Keyword / interpolation
 *   4. Texture / legacy API
 *   5. Types / precision / highp inject
 *   6. Advanced / cleanup
 */
public final class ShaderShield {
    private static final String TAG = "Quasar-ShaderShield";
    private static final int CACHE_CAP = 256;

    private static final ConcurrentHashMap<Long, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();
    private static final AtomicLong SHIELDED = new AtomicLong();

    public enum Mode {
        FAST,
        BALANCED,
        EXTREME
    }

    private static volatile Mode mode = Mode.BALANCED;

    private ShaderShield() {}

    public static void setMode(Mode m) {
        if (m != null) mode = m;
    }

    public static Mode getMode() {
        return mode;
    }

    public static String protect(String source, CapabilityTable caps, String shaderName) {
        if (source == null) return null;
        if (source.isEmpty()) return source;

        long key = mixKey(source);
        String cached = CACHE.get(key);
        if (cached != null) {
            HITS.incrementAndGet();
            return cached;
        }
        MISSES.incrementAndGet();

        boolean gles = caps == null
                || "mali".equalsIgnoreCase(caps.getGpuVendor())
                || "adreno".equalsIgnoreCase(caps.getGpuVendor())
                || !caps.hasVulkan();

        if (!ShieldTransforms.needsShield(source) && !gles) {
            remember(key, source);
            return source;
        }

        String out = runPipeline(source, gles, mode);
        SHIELDED.incrementAndGet();
        remember(key, out);

        if (shaderName != null && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "protected " + shaderName + " (" + source.length() + "→" + out.length()
                    + " chars, mode=" + mode + ")");
        }
        return out;
    }

    public static String protectMobile(String source, String shaderName) {
        return protect(source, null, shaderName);
    }

    public static String stats() {
        return "ShaderShield hits=" + HITS.get() + " misses=" + MISSES.get()
                + " shielded=" + SHIELDED.get() + " cache=" + CACHE.size()
                + " mode=" + mode;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static String runPipeline(String s, boolean gles, Mode m) {
        s = ShieldTransforms.t01_stripHostileExtensions(s, gles);
        s = ShieldTransforms.t02_joinExtensions(s);
        s = ShieldTransforms.t03_softenVersion(s);
        s = ShieldTransforms.t04_ensureVersion(s);
        s = ShieldTransforms.t06_requireToWarn(s);
        s = ShieldTransforms.t07_dropDisableNoise(s);
        s = ShieldTransforms.t05_midShaderExtensionComment(s);

        s = ShieldTransforms.t11_noperspective(s);
        s = ShieldTransforms.t13_smoothStrip(s);
        s = ShieldTransforms.t15_sampleStrip(s);
        s = ShieldTransforms.t16_preciseStrip(s);
        s = ShieldTransforms.t19_subroutineStrip(s);
        s = ShieldTransforms.t20_patchStrip(s);

        if (m == Mode.FAST) {
            s = ShieldTransforms.t36_injectHighp(s);
            s = ShieldTransforms.t37_promoteMediump(s);
            s = ShieldTransforms.t09_collapseExtComments(s);
            s = ShieldTransforms.t48_collapseBlank(s);
            return ShieldTransforms.t50_finalize(s);
        }

        s = ShieldTransforms.t21_texture2D(s);
        s = ShieldTransforms.t22_texture2DLod(s);
        s = ShieldTransforms.t23_texture2DGrad(s);
        s = ShieldTransforms.t24_textureCube(s);
        s = ShieldTransforms.t25_textureCubeLod(s);
        s = ShieldTransforms.t26_shadow2D(s);
        s = ShieldTransforms.t27_shadow2DLod(s);
        s = ShieldTransforms.t28_texture3D(s);

        s = ShieldTransforms.t31_doubleToFloat(s);
        s = ShieldTransforms.t32_dvec(s);
        s = ShieldTransforms.t33_dmat(s);
        s = ShieldTransforms.t34_uint64(s);
        s = ShieldTransforms.t35_int64(s);
        s = ShieldTransforms.t36_injectHighp(s);
        s = ShieldTransforms.t37_promoteMediump(s);
        s = ShieldTransforms.t38_coherent(s);
        s = ShieldTransforms.t39_volatileRestrict(s);
        s = ShieldTransforms.t40_rwOnly(s);

        if (m == Mode.EXTREME) {
            s = ShieldTransforms.t14_centroidStrip(s);
            s = ShieldTransforms.t17_attributeToIn(s);
            s = ShieldTransforms.t18_varyingToOut(s);
            s = ShieldTransforms.t29_ftransform(s);
            s = ShieldTransforms.t41_shared(s);
            s = ShieldTransforms.t43_atomicUint(s);
            s = ShieldTransforms.t44_earlyTests(s);
            s = ShieldTransforms.t46_stripBindingOptional(s, true);
        }

        s = ShieldTransforms.t08_injectBanner(s);
        s = ShieldTransforms.t09_collapseExtComments(s);
        s = ShieldTransforms.t48_collapseBlank(s);
        s = ShieldTransforms.t49_trimTrailing(s);
        return ShieldTransforms.t50_finalize(s);
    }

    private static long mixKey(String s) {
        long h = s.length() * 0x9E3779B97F4A7C15L;
        int step = Math.max(1, s.length() / 32);
        for (int i = 0; i < s.length(); i += step) {
            h ^= (s.charAt(i) & 0xFFL) * 0xC2B2AE3D27D4EB4FL;
            h = Long.rotateLeft(h, 13);
        }
        if (s.length() > 0) {
            h ^= s.charAt(s.length() - 1);
        }
        return h;
    }

    private static void remember(long key, String value) {
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, value);
    }
}
