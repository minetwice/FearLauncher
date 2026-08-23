package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;
import java.util.Map;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.utils.JREUtils;

/**
 * BackendSelector - Chooses between Zink (Vulkan) and GL4ES (GLES) backends with runtime verification.
 */
public class BackendSelector {
    private static final String TAG = "BackendSelector";

    public enum BackendType {
        ZINK_VULKAN,
        GL4ES_GLES
    }

    public static BackendType selectBackend(CapabilityTable caps) {
        if (caps != null && caps.hasVulkan) {
            Log.i(TAG, "[Quasar] Selected Primary Backend Candidate: ZINK_VULKAN (OpenGL-over-Vulkan)");
            return BackendType.ZINK_VULKAN;
        } else {
            Log.i(TAG, "[Quasar] Selected Fallback Backend: GL4ES_GLES (Direct GLES)");
            return BackendType.GL4ES_GLES;
        }
    }

    public static boolean verifyZinkInitialization() {
        try {
            JREUtils.preloadVulkan();
            String platform = JREUtils.probeEGLPlatform();
            boolean ok = platform != null;
            if (ok) {
                Log.i(TAG, "[Quasar] Zink Runtime Verification PASSED (EGL Platform: " + platform + ")");
            } else {
                Log.w(TAG, "[Quasar] Zink Runtime Verification FAILED: probeEGLPlatform returned null.");
            }
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "[Quasar] Zink Runtime Verification FAILED with exception: " + t.getMessage());
            return false;
        }
    }

    public static BackendType verifyAndApplyBackend(BackendType requested, Map<String, String> env, String cacheDir) {
        if (requested == BackendType.ZINK_VULKAN) {
            if (verifyZinkInitialization()) {
                ZinkBackend.configureEnvironment(env, cacheDir);
                return BackendType.ZINK_VULKAN;
            } else {
                Log.w(TAG, "[Quasar] Zink verification failed during environment configuration. Retrying with GL4ES_GLES fallback.");
                GL4ESBackend.configureEnvironment(env);
                return BackendType.GL4ES_GLES;
            }
        } else {
            GL4ESBackend.configureEnvironment(env);
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
