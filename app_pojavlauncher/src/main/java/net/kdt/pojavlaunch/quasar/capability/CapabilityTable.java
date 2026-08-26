package net.kdt.pojavlaunch.quasar.capability;

import android.util.Log;

/** Device GPU capability profile — expanded for modern OpenGL → GLES. */
public class CapabilityTable implements java.io.Serializable {
    private static final long serialVersionUID = 3L;
    private static final String TAG = "CapabilityTable";

    private String gpuVendor = "unknown";
    private boolean hasVulkan = false;
    private int vulkanApiVersion = 0;
    private String vulkanDeviceName = "";
    private String[] vulkanExtensions = new String[0];

    private boolean hasGeometryShaders = false;
    private boolean hasComputeShaders = false;
    private boolean hasTessellation = false;
    private boolean hasImageLoadStore = false;
    private boolean hasSSBO = false;
    private boolean hasMultiDrawIndirect = false;
    private boolean hasImageAtomics = false;
    private boolean hasNoperspectiveInterpolation = false;

    private boolean hasTextureCubeArray = false;
    private boolean hasTextureBuffer = false;
    private boolean hasDrawBuffersIndexed = false;
    private boolean hasSampleShading = false;
    private boolean hasBufferStorage = false;
    private boolean hasBaseInstance = false;
    private boolean hasTimerQuery = false;
    private boolean hasClipControl = false;
    private boolean hasCullDistance = false;
    private boolean hasShaderSubgroup = false;
    private boolean hasTextureView = false;
    private boolean hasAnisotropic = true;
    private boolean hasKHRDebug = false;

    private int glesVersion = 30;
    private String[] glesExtensions = new String[0];

    private int maxTextureUnits = 16;
    private int maxImageUnits = 0;
    private int maxComputeWorkGroupCount = 0;
    private int maxRenderTargets = 4;
    private int maxUniformBlockSize = 16384;
    private int maxSSBOBindings = 0;
    private int maxVertexAttribs = 16;

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
    public boolean hasNoperspectiveInterpolation() { return hasNoperspectiveInterpolation; }
    public void setHasNoperspectiveInterpolation(boolean v) { this.hasNoperspectiveInterpolation = v; }
    public boolean hasTextureCubeArray() { return hasTextureCubeArray; }
    public void setHasTextureCubeArray(boolean v) { this.hasTextureCubeArray = v; }
    public boolean hasTextureBuffer() { return hasTextureBuffer; }
    public void setHasTextureBuffer(boolean v) { this.hasTextureBuffer = v; }
    public boolean hasDrawBuffersIndexed() { return hasDrawBuffersIndexed; }
    public void setHasDrawBuffersIndexed(boolean v) { this.hasDrawBuffersIndexed = v; }
    public boolean hasSampleShading() { return hasSampleShading; }
    public void setHasSampleShading(boolean v) { this.hasSampleShading = v; }
    public boolean hasBufferStorage() { return hasBufferStorage; }
    public void setHasBufferStorage(boolean v) { this.hasBufferStorage = v; }
    public boolean hasBaseInstance() { return hasBaseInstance; }
    public void setHasBaseInstance(boolean v) { this.hasBaseInstance = v; }
    public boolean hasTimerQuery() { return hasTimerQuery; }
    public void setHasTimerQuery(boolean v) { this.hasTimerQuery = v; }
    public boolean hasClipControl() { return hasClipControl; }
    public void setHasClipControl(boolean v) { this.hasClipControl = v; }
    public boolean hasCullDistance() { return hasCullDistance; }
    public void setHasCullDistance(boolean v) { this.hasCullDistance = v; }
    public boolean hasShaderSubgroup() { return hasShaderSubgroup; }
    public void setHasShaderSubgroup(boolean v) { this.hasShaderSubgroup = v; }
    public boolean hasTextureView() { return hasTextureView; }
    public void setHasTextureView(boolean v) { this.hasTextureView = v; }
    public boolean hasAnisotropic() { return hasAnisotropic; }
    public void setHasAnisotropic(boolean v) { this.hasAnisotropic = v; }
    public boolean hasKHRDebug() { return hasKHRDebug; }
    public void setHasKHRDebug(boolean v) { this.hasKHRDebug = v; }
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
    public int getMaxUniformBlockSize() { return maxUniformBlockSize; }
    public void setMaxUniformBlockSize(int v) { this.maxUniformBlockSize = v; }
    public int getMaxSSBOBindings() { return maxSSBOBindings; }
    public void setMaxSSBOBindings(int v) { this.maxSSBOBindings = v; }
    public int getMaxVertexAttribs() { return maxVertexAttribs; }
    public void setMaxVertexAttribs(int v) { this.maxVertexAttribs = v; }

    public String getProfileKey() {
        return gpuVendor + "_vk" + vulkanApiVersion + "_es" + glesVersion
                + "_gs" + (hasGeometryShaders ? 1 : 0)
                + "_cs" + (hasComputeShaders ? 1 : 0)
                + "_ts" + (hasTessellation ? 1 : 0)
                + "_ils" + (hasImageLoadStore ? 1 : 0)
                + "_ssbo" + (hasSSBO ? 1 : 0)
                + "_mdi" + (hasMultiDrawIndirect ? 1 : 0)
                + "_npi" + (hasNoperspectiveInterpolation ? 1 : 0)
                + "_tca" + (hasTextureCubeArray ? 1 : 0)
                + "_dbi" + (hasDrawBuffersIndexed ? 1 : 0);
    }

    public boolean isFeatureSupported(String featureName) {
        if (featureName == null) return false;
        switch (featureName) {
            case "geometry_shader": return hasGeometryShaders;
            case "compute_shader": return hasComputeShaders;
            case "tessellation": return hasTessellation;
            case "image_load_store": return hasImageLoadStore;
            case "ssbo": return hasSSBO;
            case "multi_draw_indirect": return hasMultiDrawIndirect;
            case "image_atomics": return hasImageAtomics;
            case "noperspective": return hasNoperspectiveInterpolation;
            case "texture_cube_array": return hasTextureCubeArray;
            case "texture_buffer": return hasTextureBuffer;
            case "draw_buffers_blend": return hasDrawBuffersIndexed;
            case "sample_shading": return hasSampleShading;
            case "buffer_storage": return hasBufferStorage;
            case "base_instance": return hasBaseInstance;
            case "timer_query": return hasTimerQuery;
            case "clip_control": return hasClipControl;
            case "cull_distance": return hasCullDistance;
            case "shader_subgroup": return hasShaderSubgroup;
            case "texture_view": return hasTextureView;
            case "anisotropic": return hasAnisotropic;
            case "debug_output": return hasKHRDebug;
            default:
                Log.w(TAG, "Unknown feature: " + featureName);
                return false;
        }
    }

    @Override
    public String toString() {
        return "CapabilityTable{vendor=" + gpuVendor + ", es=" + glesVersion
                + ", vulkan=" + hasVulkan + "(v" + vulkanApiVersion + ")"
                + ", geom=" + hasGeometryShaders + ", compute=" + hasComputeShaders
                + ", tess=" + hasTessellation + ", image=" + hasImageLoadStore
                + ", ssbo=" + hasSSBO + ", mdi=" + hasMultiDrawIndirect
                + ", npi=" + hasNoperspectiveInterpolation + ", mrt=" + maxRenderTargets + "}";
    }
}
