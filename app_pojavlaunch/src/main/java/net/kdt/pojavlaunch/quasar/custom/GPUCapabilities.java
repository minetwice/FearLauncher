package net.kdt.pojavlaunch.quasar.custom;

import java.util.*;

/**
 * GPU Capabilities Detection
 * Detects Mali, Adreno, and other GPU features
 */
public class GPUCapabilities {
    private String vendor = "Unknown";
    private String renderer = "Unknown";
    private String version = "Unknown";
    private String glslVersion = "Unknown";
    private boolean isMali = false;
    private boolean isAdreno = false;
    private boolean isMobile = false;
    private Set<String> extensions = new HashSet<>();
    
    public void detect() {
        try {
            this.vendor = System.getProperty("gl.vendor", "Unknown");
            this.renderer = System.getProperty("gl.renderer", "Unknown");
            this.version = System.getProperty("gl.version", "Unknown");
            this.glslVersion = System.getProperty("glsl.version", "Unknown");
            
            this.isMali = renderer.contains("Mali") || vendor.contains("ARM");
            this.isAdreno = renderer.contains("Adreno") || vendor.contains("QUALCOMM");
            this.isMobile = isMali || isAdreno || renderer.contains("PowerVR");
            
            detectExtensions();
            
        } catch (Exception e) {
            System.err.println("[GPUCapabilities] Detection failed: " + e.getMessage());
        }
    }
    
    private void detectExtensions() {
        try {
            String extString = System.getProperty("gl.extensions", "");
            if (!extString.isEmpty()) {
                this.extensions.addAll(Arrays.asList(extString.split(" ")));
            }
            
            // Add common mobile extensions
            if (isMobile) {
                extensions.add("GL_OES_standard_derivatives");
                extensions.add("GL_EXT_shader_texture_lod");
                extensions.add("GL_OES_texture_float");
                extensions.add("GL_OES_texture_half_float");
                extensions.add("GL_EXT_color_buffer_float");
                extensions.add("GL_OES_element_index_uint");
            }
            
        } catch (Exception e) {
            System.err.println("[GPUCapabilities] Extension detection failed: " + e.getMessage());
        }
    }
    
    public boolean supportsExtension(String ext) {
        return extensions.contains(ext);
    }
    
    public boolean supportsFramebufferFetch() {
        return supportsExtension("GL_EXT_shader_framebuffer_fetch");
    }
    
    public boolean supportsAtomicOperations() {
        return supportsExtension("GL_EXT_shader_atomic_int32");
    }
    
    public boolean supportsGeometryShaders() {
        return version.contains("ES 3.2") || version.contains("4.");
    }
    
    public boolean supportsComputeShaders() {
        return version.contains("ES 3.1") || version.contains("ES 3.2") || version.contains("4.");
    }
    
    public String getVendor() { return vendor; }
    public String getRenderer() { return renderer; }
    public String getVersion() { return version; }
    public String getGlslVersion() { return glslVersion; }
    public boolean isMali() { return isMali; }
    public boolean isAdreno() { return isAdreno; }
    public boolean isMobile() { return isMobile; }
    public Set<String> getExtensions() { return new HashSet<>(extensions); }
}
