package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * Chooses Zink/Turnip when any Vulkan is available; GL4ES only as hard fallback.
 * LTW is never selected from Quasar Java — JREUtils only uses it if Vulkan preload fails.
 */
public class BackendSelector {
    private static final String TAG = "Quasar-BackendSelector";

    public static RenderBackend select(CapabilityTable table) {
        boolean vk = table != null && table.hasVulkan();
        int api = table != null ? table.getVulkanApiVersion() : 0;
        Log.i(TAG, "Selecting backend. Vulkan=" + vk + " api=0x" + Integer.toHexString(api));

        if (vk || api > 0) {
            Log.i(TAG, "Selecting Zink/Turnip backend");
            return new ZinkBackend(table);
        }
        Log.i(TAG, "No Vulkan — GL4ES fallback (shaders limited)");
        return new GL4ESBackend(table);
    }
}
