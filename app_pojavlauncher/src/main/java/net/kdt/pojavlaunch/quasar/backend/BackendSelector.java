package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * BackendSelector chooses the best render backend based on the device's
 * capability table.
 *
 * Selection logic:
 * - If Vulkan is available AND supports the required features -> ZinkBackend
 * - If Vulkan is not available or insufficient -> GL4ESBackend (fallback)
 */
public class BackendSelector {
    private static final String TAG = "Quasar-BackendSelector";

    /**
     * Select the best backend for the given device capabilities.
     * @param table The device capability table
     * @return The selected RenderBackend
     */
    public static RenderBackend select(CapabilityTable table) {
        Log.i(TAG, "Selecting backend. Vulkan available: " + table.hasVulkan()
                + ", API version: " + table.getVulkanApiVersion());

        if (table.hasVulkan() && table.getVulkanApiVersion() >= 0x401000) {
            // Vulkan 1.1+ — Zink should work
            Log.i(TAG, "Vulkan 1.1+ available, selecting Zink backend");
            return new ZinkBackend(table);
        }

        // Fallback to GL4ES
        Log.i(TAG, "Vulkan not available or too old, selecting GL4ES fallback backend");
        return new GL4ESBackend(table);
    }
}
