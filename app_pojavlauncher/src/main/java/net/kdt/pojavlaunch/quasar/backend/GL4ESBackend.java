package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * GL4ESBackend implements rendering through GL4ES as a fallback.
 * GL4ES bridges desktop OpenGL to OpenGL ES, which is available on all Android devices.
 *
 * This backend is used when Vulkan is not viable (no Vulkan support, or extensions
 * are too limited for Zink to function correctly).
 *
 * Limitations:
 * - No geometry shaders (GL4ES only bridges to GLES)
 * - No image load/store
 * - Limited shader compatibility — shaderpacks requiring these features will
 *   be degraded (the affected passes will be disabled, not crashed)
 */
public class GL4ESBackend implements RenderBackend {
    private static final String TAG = "Quasar-GL4ESBackend";
    private final CapabilityTable capabilityTable;
    private boolean initialized = false;

    public GL4ESBackend(CapabilityTable capabilityTable) {
        this.capabilityTable = capabilityTable;
    }

    @Override
    public String getBackendName() {
        return "GL4ES (Fallback)";
    }

    @Override
    public void init() {
        Log.i(TAG, "Initializing GL4ES fallback backend...");
        // GL4ES is already loaded by PojavLauncher's existing renderer setup
        initialized = true;
        Log.i(TAG, "GL4ES backend initialized");
    }

    @Override
    public boolean supportsFeature(String feature) {
        if (!initialized) return false;
        switch (feature) {
            case "geometry_shader":
                return false;
            case "compute_shader":
                return capabilityTable.hasComputeShaders();
            case "image_load_store":
                return false;
            case "ssbo":
                return capabilityTable.hasSSBO();
            case "tessellation":
                return false;
            case "multi_draw_indirect":
                return false;
            default:
                Log.w(TAG, "Unknown feature query: " + feature);
                return false;
        }
    }

    @Override
    public void cleanup() {
        Log.i(TAG, "Cleaning up GL4ES backend...");
        initialized = false;
        Log.i(TAG, "GL4ES backend cleaned up");
    }
}
