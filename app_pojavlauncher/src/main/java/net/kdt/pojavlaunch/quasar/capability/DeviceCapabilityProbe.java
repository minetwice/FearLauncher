package net.kdt.pojavlaunch.quasar.capability;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

/**
 * DeviceCapabilityProbe - Probes device capabilities and returns populated CapabilityTable.
 */
public class DeviceCapabilityProbe {
    private static final String TAG = "DeviceCapabilityProbe";

    public static CapabilityTable probeCapabilities(Context context) {
        CapabilityTable table = new CapabilityTable();

        if (context != null) {
            PackageManager pm = context.getPackageManager();
            if (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
                table.hasVulkan = true;
                table.vulkanMajorVersion = 1;
                table.vulkanMinorVersion = 3;
            }
        }

        try {
            GLInfoUtils.GLInfo glInfo = GLInfoUtils.getGlInfo();
            if (glInfo != null) {
                table.isAdreno = glInfo.isAdreno();
                table.isMali = glInfo.renderer != null && (glInfo.renderer.contains("Mali") || glInfo.renderer.contains("mali"));
            }
        } catch (Throwable t) {
            Log.w(TAG, "GLInfo query failed: " + t.getMessage());
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
        }

        Log.i(TAG, "[Quasar] Capability Table Probed: " + table.toString());
        return table;
    }
}
