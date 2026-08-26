package net.kdt.pojavlaunch.quasar;

import java.util.HashSet;
import java.util.Set;

public class GpuCapabilities {
    private String vendor;
    private String renderer;
    private String version;
    private boolean isMobile;
    private Set<String> extensions;
    
    public GpuCapabilities() { this.extensions = new HashSet<>(); }
    
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getRenderer() { return renderer; }
    public void setRenderer(String renderer) { this.renderer = renderer; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public boolean isMobile() { return isMobile; }
    public void setMobile(boolean mobile) { isMobile = mobile; }
    public Set<String> getExtensions() { return new HashSet<>(extensions); }
    public void addExtension(String extension) { extensions.add(extension); }
    public boolean hasExtension(String extension) { return extensions.contains(extension); }
    public boolean supportsFramebufferFetch() { return hasExtension("GL_EXT_shader_framebuffer_fetch"); }
    public boolean supportsAtomicOperations() { return hasExtension("GL_EXT_shader_atomic_int32"); }
    public boolean supportsGeometryShaders() { return version.contains("ES 3.2") || version.contains("4."); }
    public boolean supportsComputeShaders() { return version.contains("ES 3.1") || version.contains("ES 3.2") || version.contains("4."); }
}