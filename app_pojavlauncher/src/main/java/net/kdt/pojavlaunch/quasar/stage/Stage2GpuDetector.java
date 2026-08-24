package net.kdt.pojavlaunch.quasar.stage;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.capability.DeviceCapabilityProbe;

/**
 * Stage 2: GPU Detector & Capability Inspection.
 *
 * Probes and displays GPU details (Mali, Adreno, PowerVR, etc.), Vulkan/GLES capabilities,
 * and device-specific limitations for shaders.
 */
public class Stage2GpuDetector {
    private static final String TAG = "Quasar-Stage2GpuDetector";
    private final CapabilityTable capabilityTable;

    public Stage2GpuDetector(Context context) {
        DeviceCapabilityProbe probe = new DeviceCapabilityProbe();
        this.capabilityTable = probe.probe(context);
        logGpuInfo();
    }

    public Stage2GpuDetector(CapabilityTable capabilityTable) {
        this.capabilityTable = capabilityTable != null ? capabilityTable : new CapabilityTable();
        logGpuInfo();
    }

    private void logGpuInfo() {
        Log.i(TAG, "[Stage 2] Active GPU Vendor: " + capabilityTable.getGpuVendor());
        Log.i(TAG, "[Stage 2] Device Name: " + capabilityTable.getVulkanDeviceName());
        Log.i(TAG, "[Stage 2] Vulkan Available: " + capabilityTable.hasVulkan() + " (API version: 0x" + Integer.toHexString(capabilityTable.getVulkanApiVersion()) + ")");
        Log.i(TAG, "[Stage 2] Features - Mali/Adreno optimized profile: " + capabilityTable.getProfileKey());
    }

    public CapabilityTable getCapabilityTable() {
        return capabilityTable;
    }

    public boolean isMaliGpu() {
        return capabilityTable.getGpuVendor().toLowerCase().contains("mali");
    }

    public boolean isAdrenoGpu() {
        return capabilityTable.getGpuVendor().toLowerCase().contains("adreno");
    }

    public String getGpuVendorInfo() {
        return capabilityTable.getGpuVendor() + " (" + capabilityTable.getVulkanDeviceName() + ")";
    }
}
