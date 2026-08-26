package net.kdt.pojavlaunch.quasar.gl.emu;

import net.kdt.pojavlaunch.quasar.gl.MobileFeatureBridge;
import net.kdt.pojavlaunch.quasar.gl.MobileFeatureBridge.Outcome;
import net.kdt.pojavlaunch.quasar.gl.ModernGLCompat;

/**
 * Runtime helpers: given a desktop feature name, decide how shaders / API
 * should behave on this phone.
 */
public final class FeatureEmulator {

    private FeatureEmulator() {}

    public static boolean canUseNative(String feature) {
        MobileFeatureBridge b = ModernGLCompat.bridge();
        if (b == null) return false;
        Outcome o = b.outcome(feature);
        return o == Outcome.PASSTHROUGH || o == Outcome.JOIN;
    }

    public static boolean mustStrip(String feature) {
        MobileFeatureBridge b = ModernGLCompat.bridge();
        if (b == null) return true;
        return b.outcome(feature) == Outcome.STRIP;
    }

    public static boolean mustEmulate(String feature) {
        MobileFeatureBridge b = ModernGLCompat.bridge();
        if (b == null) return false;
        return b.outcome(feature) == Outcome.EMULATE;
    }

    public static String targetEsGlslVersion() {
        if (ModernGLCompat.profile() != null) {
            return "#version " + ModernGLCompat.profile().shadingLanguage;
        }
        return "#version 300 es";
    }

    public static boolean demoteDoublePrecision() {
        return true;
    }

    public static boolean disableGeometryStage() {
        return mustStrip("geometry_shader") && !canUseNative("geometry_shader");
    }

    public static boolean disableTessellationStage() {
        return mustStrip("tessellation_shader") && !canUseNative("tessellation_shader");
    }

    public static boolean disableComputeStage() {
        return mustStrip("compute_shader") && !canUseNative("compute_shader");
    }
}
