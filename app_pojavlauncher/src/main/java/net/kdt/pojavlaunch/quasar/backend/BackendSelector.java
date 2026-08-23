package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;
import java.util.Map;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * BackendSelector - Chooses between Zink (Vulkan) and GL4ES (GLES) backends at runtime.
 */
public class BackendSelector {
    private static final String TAG = "BackendSelector";

    public enum BackendType {
        ZINK_VULKAN,
        GL4ES_GLES
    }

    public static BackendType selectBackend(CapabilityTable caps) {
        if (caps != null && caps.hasVulkan) {
            Log.i(TAG, "[Quasar] Selected Primary Backend: ZINK_VULKAN (OpenGL-over-Vulkan)");
            return BackendType.ZINK_VULKAN;
        } else {
            Log.i(TAG, "[Quasar] Selected Fallback Backend: GL4ES_GLES (Direct GLES)");
            return BackendType.GL4ES_GLES;
        }
    }

    public static void applyBackendEnvironment(BackendType type, Map<String, String> env, String cacheDir) {
        if (type == BackendType.ZINK_VULKAN) {
            ZinkBackend.configureEnvironment(env, cacheDir);
        } else {
            GL4ESBackend.configureEnvironment(env);
        }
    }
}
