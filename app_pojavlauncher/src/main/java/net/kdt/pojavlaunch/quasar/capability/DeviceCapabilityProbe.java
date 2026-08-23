package net.kdt.pojavlaunch.quasar.capability;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

/**
 * DeviceCapabilityProbe - Probes device capabilities and returns populated CapabilityTable with conservative fallback.
 */
public class DeviceCapabilityProbe {
    private static final String TAG = "DeviceCapabilityProbe";

    public static CapabilityTable probeCapabilities(Context context) {
        CapabilityTable table = new CapabilityTable();

        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                if (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
                    table.hasVulkan = true;
                    table.vulkanMajorVersion = 1;
                    table.vulkanMinorVersion = 3;
                }
            } catch (Throwable t) {
                Log.w(TAG, "[Quasar] PackageManager query for Vulkan feature failed: " + t.getMessage());
            }
        }

        try {
            GLInfoUtils.GLInfo glInfo = GLInfoUtils.getGlInfo();
            if (glInfo != null && glInfo.renderer != null) {
                String renderer = glInfo.renderer.toLowerCase();
                if (renderer.contains("adreno")) {
                    table.isAdreno = true;
                } else if (renderer.contains("mali")) {
                    table.isMali = true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[Quasar] GLInfo query failed: " + t.getMessage());
        }

        if (table.isMali) {
            table.supportsComputeShaders = false; // Conservative Mali profile
            table.supportsGeometryShaders = false;
            table.supportsFloat16Color = true;
            table.supportsFloat32Depth = false; // Force Depth24 on Mali
            Log.i(TAG, "[Quasar] Profile: ARM Mali GPU detected. Applied conservative feature table.");
        } else if (table.isAdreno) {
            table.supportsComputeShaders = true;
            table.supportsGeometryShaders = true;
            table.supportsASTCTextures = true;
            table.supportsFloat32Depth = true;
            Log.i(TAG, "[Quasar] Profile: Qualcomm Adreno GPU detected. Enabled ASTC and compute extensions.");
        } else {
            // Conservative fallback for unknown / unrecognized GPU vendors (e.g. PowerVR, ImgTec, SwiftShader, Vivante)
            table.isMali = false;
            table.isAdreno = false;
            table.supportsComputeShaders = false;
            table.supportsGeometryShaders = false;
            table.supportsImageAtomics = false;
            table.supportsASTCTextures = false;
            table.supportsFloat16Color = false;
            table.supportsFloat32Depth = false;
            Log.w(TAG, "[Quasar] Profile: Unknown/Unrecognized GPU vendor detected. Applied safest conservative profile (all advanced features explicitly disabled).");
        }

        Log.i(TAG, "[Quasar] Capability Table Probed: " + table.toString());
        return table;
    }
}
