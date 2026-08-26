package net.kdt.pojavlaunch.quasar;

import java.util.HashSet;
import java.util.Set;

public class ExtensionDetector {
    private GpuCapabilities cachedCapabilities;
    
    public GpuCapabilities detectCapabilities() {
        if (cachedCapabilities != null) {
            return cachedCapabilities;
        }
        
        GpuCapabilities caps = new GpuCapabilities();
        String glVendor = System.getProperty("gl.vendor", "");
        String glRenderer = System.getProperty("gl.renderer", "");
        String glVersion = System.getProperty("gl.version", "");
        
        caps.setVendor(glVendor);
        caps.setRenderer(glRenderer);
        caps.setVersion(glVersion);
        caps.setMobile(glRenderer.contains("Mali") || glRenderer.contains("Adreno"));
        
        detectExtensions(caps);
        cachedCapabilities = caps;
        return caps;
    }
    
    private void detectExtensions(GpuCapabilities caps) {
        String extensions = System.getProperty("gl.extensions", "");
        if (extensions.isEmpty()) return;
        
        String[] extArray = extensions.split(" ");
        for (String ext : extArray) {
            if (!ext.isEmpty()) {
                caps.addExtension(ext);
            }
        }
    }
    
    public void clearCache() {
        cachedCapabilities = null;
    }
}