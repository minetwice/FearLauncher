package net.kdt.pojavlaunch.quasar.capability;

import java.io.Serializable;

/**
 * CapabilityTable - Comprehensive feature matrix for tracking Mali / Adreno GPU gaps and extensions.
 */
public class CapabilityTable implements Serializable {
    public boolean hasVulkan = false;
    public int vulkanMajorVersion = 1;
    public int vulkanMinorVersion = 3;

    public boolean isAdreno = false;
    public boolean isMali = false;

    // Feature Flags
    public boolean supportsComputeShaders = true;
    public boolean supportsGeometryShaders = false;
    public boolean supportsImageAtomics = false;
    public boolean supportsASTCTextures = false;
    public boolean supportsFloat16Color = true;
    public boolean supportsFloat32Depth = true;

    // Hardware Limits
    public int maxDrawBuffers = 8;
    public int maxTextureUnits = 16;
    public int maxTextureSize = 16384;
    public int maxRenderbufferSize = 16384;

    @Override
    public String toString() {
        return "CapabilityTable{" +
                "hasVulkan=" + hasVulkan +
                ", vkVersion=" + vulkanMajorVersion + "." + vulkanMinorVersion +
                ", isAdreno=" + isAdreno +
                ", isMali=" + isMali +
                ", compute=" + supportsComputeShaders +
                ", geometry=" + supportsGeometryShaders +
                ", drawBuffers=" + maxDrawBuffers +
                ", float16Color=" + supportsFloat16Color +
                '}';
    }
}
