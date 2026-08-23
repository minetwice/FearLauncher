package net.kdt.pojavlaunch.quasar.capability;

import android.util.Log;

/**
 * CapabilityTable represents the device's GPU capability profile.
 * It is serializable so it can be cached alongside transpiled shaders.
 *
 * The table distinguishes Mali vs Adreno gaps and is used to:
 * - Select the render backend (Zink vs GL4ES)
 * - Determine which shader passes can run (full vs degraded)
 * - Key the shader cache (different devices need different transpiled output)
 */
public class CapabilityTable implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private static final String TAG = "CapabilityTable";

    // GPU vendor
    private String gpuVendor = "unknown";

    // Vulkan capabilities
    private boolean hasVulkan = false;
    private int vulkanApiVersion = 0;
    private String vulkanDeviceName = "";
    private String[] vulkanExtensions = new String[0];

    // Feature support flags
    private boolean hasGeometryShaders = false;
    private boolean hasComputeShaders = false;
    private boolean hasTessellation = false;
    private boolean hasImageLoadStore = false;
    private boolean hasSSBO = false;
    private boolean hasMultiDrawIndirect = false;
    private boolean hasImageAtomics = false;

    // GLES fallback capabilities
    private int glesVersion = 0;
    private String[] glesExtensions = new String[0];

    // Max limits
    private int maxTextureUnits = 16;
    private int maxImageUnits = 0;
    private int maxComputeWorkGroupCount = 0;
    private int maxRenderTargets = 4;

    // --- Getters and Setters ---

    public String getGpuVendor() { return gpuVendor; }
    public void setGpuVendor(String gpuVendor) { this.gpuVendor = gpuVendor; }

    public boolean hasVulkan() { return hasVulkan; }
    public void setHasVulkan(boolean hasVulkan) { this.hasVulkan = hasVulkan; }

    public int getVulkanApiVersion() { return vulkanApiVersion; }
    public void setVulkanApiVersion(int version) { this.vulkanApiVersion = version; }

    public String getVulkanDeviceName() { return vulkanDeviceName; }
    public void setVulkanDeviceName(String name) { this.vulkanDeviceName = name; }

    public String[] getVulkanExtensions() { return vulkanExtensions; }
    public void setVulkanExtensions(String[] exts) { this.vulkanExtensions = exts; }

    public boolean hasGeometryShaders() { return hasGeometryShaders; }
    public void setHasGeometryShaders(boolean v) { this.hasGeometryShaders = v; }

    public boolean hasComputeShaders() { return hasComputeShaders; }
    public void setHasComputeShaders(boolean v) { this.hasComputeShaders = v; }

    public boolean hasTessellation() { return hasTessellation; }
    public void setHasTessellation(boolean v) { this.hasTessellation = v; }

    public boolean hasImageLoadStore() { return hasImageLoadStore; }
    public void setHasImageLoadStore(boolean v) { this.hasImageLoadStore = v; }

    public boolean hasSSBO() { return hasSSBO; }
    public void setHasSSBO(boolean v) { this.hasSSBO = v; }

    public boolean hasMultiDrawIndirect() { return hasMultiDrawIndirect; }
    public void setHasMultiDrawIndirect(boolean v) { this.hasMultiDrawIndirect = v; }

    public boolean hasImageAtomics() { return hasImageAtomics; }
    public void setHasImageAtomics(boolean v) { this.hasImageAtomics = v; }

    public int getGlesVersion() { return glesVersion; }
    public void setGlesVersion(int v) { this.glesVersion = v; }

    public String[] getGlesExtensions() { return glesExtensions; }
    public void setGlesExtensions(String[] exts) { this.glesExtensions = exts; }

    public int getMaxTextureUnits() { return maxTextureUnits; }
    public void setMaxTextureUnits(int v) { this.maxTextureUnits = v; }

    public int getMaxImageUnits() { return maxImageUnits; }
    public void setMaxImageUnits(int v) { this.maxImageUnits = v; }

    public int getMaxComputeWorkGroupCount() { return maxComputeWorkGroupCount; }
    public void setMaxComputeWorkGroupCount(int v) { this.maxComputeWorkGroupCount = v; }

    public int getMaxRenderTargets() { return maxRenderTargets; }
    public void setMaxRenderTargets(int v) { this.maxRenderTargets = v; }

    /**
     * Generate a unique key for this capability profile.
     * Used as part of the shader cache key.
     */
    public String getProfileKey() {
        return gpuVendor + "_vk" + vulkanApiVersion
                + "_gs" + (hasGeometryShaders ? 1 : 0)
                + "_cs" + (hasComputeShaders ? 1 : 0)
                + "_ts" + (hasTessellation ? 1 : 0)
                + "_ils" + (hasImageLoadStore ? 1 : 0)
                + "_ssbo" + (hasSSBO ? 1 : 0)
                + "_mdi" + (hasMultiDrawIndirect ? 1 : 0);
    }

    /**
     * Check if a required feature is supported.
     */
    public boolean isFeatureSupported(String featureName) {
        switch (featureName) {
            case "geometry_shader": return hasGeometryShaders;
            case "compute_shader": return hasComputeShaders;
            case "tessellation": return hasTessellation;
            case "image_load_store": return hasImageLoadStore;
            case "ssbo": return hasSSBO;
            case "multi_draw_indirect": return hasMultiDrawIndirect;
            case "image_atomics": return hasImageAtomics;
            default:
                Log.w(TAG, "Unknown feature: " + featureName);
                return false;
        }
    }

    @Override
    public String toString() {
        return "CapabilityTable{"
                + "vendor=" + gpuVendor
                + ", vulkan=" + hasVulkan + "(v" + vulkanApiVersion + ")"
                + ", geomShaders=" + hasGeometryShaders
                + ", compute=" + hasComputeShaders
                + ", tessellation=" + hasTessellation
                + ", imageLoadStore=" + hasImageLoadStore
                + ", ssbo=" + hasSSBO
                + ", multiDrawIndirect=" + hasMultiDrawIndirect
                + ", imageAtomics=" + hasImageAtomics
                + "}";
    }
}
