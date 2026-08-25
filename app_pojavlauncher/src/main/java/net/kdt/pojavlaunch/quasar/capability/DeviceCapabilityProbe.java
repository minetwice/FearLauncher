package net.kdt.pojavlaunch.quasar.capability;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * DeviceCapabilityProbe queries the device's actual GPU capabilities at runtime.
 *
 * It probes:
 * - Vulkan: vkGetPhysicalDeviceFeatures, vkGetPhysicalDeviceProperties, extension list
 * - GLES fallback: GL_EXTENSIONS, GL_MAX_* limits
 *
 * The Vulkan probe is implemented in native code (quasar_capability_probe.c)
 * which dlopens libvulkan.so, creates a VkInstance, and queries the first
 * physical device's features and properties. The result is returned as JSON.
 *
 * The result is a CapabilityTable that distinguishes Mali vs Adreno gaps
 * (e.g., compute shader support, multiple render targets, image atomics,
 * geometry shader emulation availability, noperspective interpolation).
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

        // --- Probe Vulkan via native code ---
        String vulkanJson = null;
        try {
            vulkanJson = nativeProbeVulkan();
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native probe not available (libquasar_probe.so not loaded), using fallback", e);
        }

        if (vulkanJson != null) {
            try {
                JSONObject json = new JSONObject(vulkanJson);
                boolean available = json.optBoolean("available", false);

                if (available) {
                    Log.i(TAG, "Vulkan probe successful, parsing results...");
                    table.setHasVulkan(true);
                    table.setVulkanApiVersion(json.optInt("apiVersion", 0));
                    table.setVulkanDeviceName(json.optString("deviceName", ""));
                    table.setGpuVendor(json.optString("gpuVendor", "unknown"));

                    // Feature flags from Vulkan
                    table.setHasGeometryShaders(json.optBoolean("geometryShader", false));
                    table.setHasTessellation(json.optBoolean("tessellationShader", false));
                    table.setHasMultiDrawIndirect(json.optBoolean("multiDrawIndirect", false));

                    // Image load/store = shaderStorageImageExtendedFormats || shaderStorageImageWriteWithoutFormat
                    table.setHasImageLoadStore(
                            json.optBoolean("shaderStorageImageExtendedFormats", false) ||
                            json.optBoolean("shaderStorageImageWriteWithoutFormat", false));

                    // SSBO = vertexPipelineStoresAndAtomics || fragmentStoresAndAtomics
                    table.setHasSSBO(
                            json.optBoolean("vertexPipelineStoresAndAtomics", false) ||
                            json.optBoolean("fragmentStoresAndAtomics", false));

                    // Compute shader — Vulkan 1.0+ always supports compute
                    table.setHasComputeShaders(true);

                    Log.i(TAG, "Vulkan device: " + table.getVulkanDeviceName()
                            + " (vendor=" + table.getGpuVendor()
                            + ", api=0x" + Integer.toHexString(table.getVulkanApiVersion()) + ")");
                    Log.i(TAG, "Features: geom=" + table.hasGeometryShaders()
                            + ", tess=" + table.hasTessellation()
                            + ", compute=" + table.hasComputeShaders()
                            + ", imgLoadStore=" + table.hasImageLoadStore()
                            + ", ssbo=" + table.hasSSBO()
                            + ", mdi=" + table.hasMultiDrawIndirect());

                    // Parse extensions array
                    if (json.has("extensions")) {
                        org.json.JSONArray extArray = json.getJSONArray("extensions");
                        String[] exts = new String[extArray.length()];
                        for (int i = 0; i < extArray.length(); i++) {
                            exts[i] = extArray.getString(i);
                        }
                        table.setVulkanExtensions(exts);
                        Log.i(TAG, "Found " + exts.length + " Vulkan device extensions");
                    }
                } else {
                    Log.w(TAG, "Vulkan not available: " + json.optString("error", "unknown"));
                    table.setHasVulkan(false);
                    // Fall back to Java-side detection
                    probeGlesFallback(table, context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse Vulkan probe JSON", e);
                table.setHasVulkan(false);
                probeGlesFallback(table, context);
            }
        } else {
            // Native probe not available — use Java-side fallback detection
            Log.w(TAG, "Native probe returned null, using Java-side fallback");
            probeGlesFallback(table, context);
        }

        // --- Apply vendor-specific gaps ---
        applyVendorSpecificGaps(table, table.getGpuVendor());

        Log.i(TAG, "Capability probe complete: " + table.toString());
        return table;
    }

    /**
     * Fallback detection using Java-side APIs when the native probe is unavailable.
     */
    private void probeGlesFallback(CapabilityTable table, Context context) {
        Log.i(TAG, "Using Java-side fallback capability detection...");

        // Detect GPU vendor from Build properties
        String gpuVendor = detectGpuVendorJava();
        table.setGpuVendor(gpuVendor);
        Log.i(TAG, "Detected GPU vendor (Java): " + gpuVendor);

        // Detect Vulkan availability via PackageManager
        boolean hasVulkan = detectVulkanJava(context);
        table.setHasVulkan(hasVulkan);
        Log.i(TAG, "Vulkan available (PackageManager): " + hasVulkan);

        if (hasVulkan) {
            // Assume Vulkan 1.1+ if PackageManager reports Vulkan support
            table.setVulkanApiVersion(0x401000);
            table.setHasGeometryShaders(true);
            table.setHasComputeShaders(true);
            table.setHasTessellation(true);
            table.setHasImageLoadStore(true);
            table.setHasSSBO(true);
            table.setHasMultiDrawIndirect(true);
        } else {
            table.setVulkanApiVersion(0);
            table.setHasGeometryShaders(false);
            table.setHasComputeShaders(false);
            table.setHasTessellation(false);
            table.setHasImageLoadStore(false);
            table.setHasSSBO(false);
            table.setHasMultiDrawIndirect(false);
            Log.w(TAG, "No Vulkan — using conservative GLES defaults");
        }
    }

    /**
     * Detect GPU vendor using Java system properties.
     */
    private String detectGpuVendorJava() {
        String brand = android.os.Build.BRAND.toLowerCase();
        if (brand.contains("qualcomm")) return "adreno";
        if (brand.contains("samsung") || brand.contains("mediatek")) return "mali";
        return "unknown";
    }

    /**
     * Detect Vulkan availability via PackageManager feature flags.
     */
    private boolean detectVulkanJava(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        return context.getPackageManager()
                .hasSystemFeature("android.hardware.vulkan.level");
    }

    /**
     * Apply vendor-specific capability gaps and workarounds.
     * Mali GLES drivers typically lack GL_NV_shader_noperspective_interpolation,
     * which Complementary / Solas shaderpacks request — mark it unsupported so
     * ShaderPreprocessor strips the extension and keyword.
     */
    private void applyVendorSpecificGaps(CapabilityTable table, String vendor) {
        switch (vendor) {
            case "mali":
            case "arm":
                Log.i(TAG, "Applying Mali-specific capability adjustments");
                table.setHasImageAtomics(false);
                table.setHasNoperspectiveInterpolation(false);
                break;
            case "adreno":
                Log.i(TAG, "Applying Adreno-specific capability adjustments");
                table.setHasImageAtomics(true);
                // Some Adreno GLES paths also reject NV noperspective; stay safe
                table.setHasNoperspectiveInterpolation(false);
                break;
            case "powervr":
                Log.i(TAG, "Applying PowerVR-specific capability adjustments");
                if (!table.hasVulkan()) {
                    table.setHasComputeShaders(false);
                    table.setHasSSBO(false);
                }
                table.setHasImageAtomics(false);
                table.setHasNoperspectiveInterpolation(false);
                break;
            case "software":
                Log.i(TAG, "Software renderer detected (llvmpipe/swiftshader)");
                table.setHasImageAtomics(false);
                table.setHasNoperspectiveInterpolation(false);
                break;
            default:
                Log.w(TAG, "Unknown GPU vendor, using conservative defaults");
                table.setHasImageAtomics(false);
                table.setHasNoperspectiveInterpolation(false);
                break;
        }
    }

    // --- Native method ---

    /**
     * Native Vulkan capability probe.
     * Returns a JSON string with device features and properties, or
     * {"available":false,"error":"..."} if Vulkan is not available.
     */
    private static native String nativeProbeVulkan();

    static {
        try {
            System.loadLibrary("quasar_probe");
            Log.i(TAG, "libquasar_probe.so loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "libquasar_probe.so not available — native probe disabled", e);
        }
    }
}
