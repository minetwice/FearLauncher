package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.shield.ShaderShield;

/**
 * ShaderPreprocessor — thin façade over {@link ShaderShield}.
 *
 * All heavy Mali/Adreno GLSL adaptation (50 transform units, extension vault,
 * cache, keyword strip, highp inject) lives in the shield package.
 */
public class ShaderPreprocessor {
    private static final String TAG = "Quasar-ShaderPreproc";

    public static String preprocess(String source, CapabilityTable capabilities, String shaderName) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        String out = ShaderShield.protect(source, capabilities, shaderName);
        if (out != null && out.length() != source.length()) {
            Log.i(TAG, "Preprocessed " + (shaderName != null ? shaderName : "shader")
                    + " via ShaderShield (" + source.length() + "→" + out.length() + ")");
        }
        return out;
    }

    public static String preprocessForMobile(String source, String shaderName) {
        return preprocess(source, null, shaderName);
    }
}
