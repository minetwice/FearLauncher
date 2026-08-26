package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * Zink / Turnip backend for Quasar.
 * Desktop GL (Iris) → Mesa Zink → Vulkan
 *   Adreno: Turnip (setUseTurnip)
 *   Mali: vendor Vulkan ICD under Zink
 */
public class ZinkBackend implements RenderBackend {
    private static final String TAG = "Quasar-ZinkBackend";
    private final CapabilityTable capabilityTable;
    private boolean initialized = false;
    private boolean turnip;

    public ZinkBackend(CapabilityTable capabilityTable) {
        this.capabilityTable = capabilityTable;
        String v = capabilityTable != null ? capabilityTable.getGpuVendor() : "";
        this.turnip = v != null && "adreno".equalsIgnoreCase(v);
    }

    @Override
    public String getBackendName() {
        return turnip ? "Zink+Turnip (Vulkan)" : "Zink (Vulkan)";
    }

    @Override
    public void init() {
        Log.i(TAG, "Initializing " + getBackendName()
                + " vendor=" + (capabilityTable != null ? capabilityTable.getGpuVendor() : "?"));
        initialized = true;
        Log.i(TAG, "Backend ready — GL calls route Mesa Zink → Vulkan");
    }

    @Override
    public boolean supportsFeature(String feature) {
        if (!initialized) return false;
        switch (feature) {
            case "geometry_shader":
                return capabilityTable.hasGeometryShaders();
            case "compute_shader":
                return capabilityTable.hasComputeShaders();
            case "image_load_store":
                return capabilityTable.hasImageLoadStore();
            case "ssbo":
                return capabilityTable.hasSSBO();
            case "tessellation":
                return capabilityTable.hasTessellation();
            case "multi_draw_indirect":
                return capabilityTable.hasMultiDrawIndirect();
            case "mrt_float":
            case "framebuffer_float":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void cleanup() {
        Log.i(TAG, "Cleaning up Zink backend...");
        initialized = false;
    }
}
