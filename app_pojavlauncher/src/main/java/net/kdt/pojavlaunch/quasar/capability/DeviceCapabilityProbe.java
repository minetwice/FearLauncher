package net.kdt.pojavlaunch.quasar.capability;

import android.content.Context;
import android.util.Log;

/**
 * DeviceCapabilityProbe queries the device's actual GPU capabilities at runtime.
 *
 * It probes:
 * - Vulkan: vkGetPhysicalDeviceFeatures, vkGetPhysicalDeviceProperties, extension list
 * - GLES fallback: GL_EXTENSIONS, GL_MAX_* limits
 *
 * The result is a CapabilityTable that distinguishes Mali vs Adreno gaps
 * (e.g., compute shader support, multiple render targets, image atomics,
 * geometry shader emulation availability).
 *
 * TODO: Implement actual Vulkan probing via JNI/NDK. Currently uses Java-side
 * detection only (GL extensions string, Build.BRAND, etc.)
 */
public class DeviceCapabilityProbe {
    private static final String TAG = "Quasar-CapabilityProbe";

    /**
     * Probe the device's GPU capabilities.
     * @param context Android context
     * @return A CapabilityTable describing what the device supports
     */
    public CapabilityTable probe(Context context) {
        Log.i(TAG, "Probing device GPU capabilities...");
        CapabilityTable table = new CapabilityTable();

        // --- Detect GPU vendor from Build properties ---
        String gpuVendor = detectGpuVendor();
        table.setGpuVendor(gpuVendor);
        Log.i(TAG, "Detected GPU vendor: " + gpuVendor);

        // --- Detect Vulkan availability ---
        boolean hasVulkan = detectVulkan(context);
        table.setHasVulkan(hasVulkan);
        Log.i(TAG, "Vulkan available: " + hasVulkan);

        // TODO: If Vulkan is available, call vkGetPhysicalDeviceFeatures and
        // vkGetPhysicalDeviceProperties via JNI to populate:
        // - Geometry shader support
        // - Compute shader support
        // - Tessellation support
        // - Image load/store support
        // - SSBO support
        // - Multi-draw indirect support
        // - Max image units
        // - Max compute work group count/size
        //
        // For now, set conservative defaults based on vendor knowledge:

        if (hasVulkan) {
            // Vulkan 1.1+ devices generally support all features we need
            table.setVulkanApiVersion(0x402000); // Assume Vulkan 1.2 for now
            table.setHasGeometryShaders(true);
            table.setHasComputeShaders(true);
            table.setHasTessellation(true);
            table.setHasImageLoadStore(true);
            table.setHasSSBO(true);
            table.setHasMultiDrawIndirect(true);
        } else {
            // No Vulkan — fall back to GLES capabilities
            table.setVulkanApiVersion(0);
            table.setHasGeometryShaders(false);
            table.setHasComputeShaders(false);
            table.setHasTessellation(false);
            table.setHasImageLoadStore(false);
            table.setHasSSBO(false);
            table.setHasMultiDrawIndirect(false);

            // TODO: Probe GLES version and extension list
            Log.w(TAG, "Vulkan not available — GLES probing not yet implemented, using conservative defaults");
        }

        // --- Set vendor-specific capability gaps ---
        applyVendorSpecificGaps(table, gpuVendor);

        Log.i(TAG, "Capability probe complete: " + table.toString());
        return table;
    }

    /**
     * Detect the GPU vendor from system properties.
     * @return "mali", "adreno", "powervr", or "unknown"
     */
    private String detectGpuVendor() {
        String brand = android.os.Build.BRAND.toLowerCase();

        if (brand.contains("qualcomm") || brand.contains("samsung") && android.os.Build.HARDWARE.contains("qcom")) {
            return "adreno";
        }

        if (brand.contains("samsung") || brand.contains("mediatek")) {
            return "mali";
        }

        if (brand.contains("mediatek")) {
            return "powervr";
        }

        return "unknown";
    }

    /**
     * Detect if the device supports Vulkan.
     * Uses PackageManager to check for Vulkan feature flags.
     */
    private boolean detectVulkan(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 24) {
            return false;
        }
        return context.getPackageManager()
                .hasSystemFeature("android.hardware.vulkan.level");
    }

    /**
     * Apply vendor-specific capability gaps and workarounds.
     * This is where we handle known Mali vs Adreno differences.
     */
    private void applyVendorSpecificGaps(CapabilityTable table, String vendor) {
        switch (vendor) {
            case "mali":
                Log.i(TAG, "Applying Mali-specific capability adjustments");
                table.setHasImageAtomics(false);
                break;

            case "adreno":
                Log.i(TAG, "Applying Adreno-specific capability adjustments");
                table.setHasImageAtomics(true);
                break;

            case "powervr":
                Log.i(TAG, "Applying PowerVR-specific capability adjustments");
                if (!table.hasVulkan()) {
                    table.setHasComputeShaders(false);
                    table.setHasSSBO(false);
                }
                table.setHasImageAtomics(false);
                break;

            default:
                Log.w(TAG, "Unknown GPU vendor, using conservative defaults");
                table.setHasImageAtomics(false);
                break;
        }
    }
}
