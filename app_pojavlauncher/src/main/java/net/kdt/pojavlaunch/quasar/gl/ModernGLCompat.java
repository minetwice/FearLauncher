package net.kdt.pojavlaunch.quasar.gl;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.gl.dsa.DSAEmulator;
import net.kdt.pojavlaunch.quasar.shield.ExtensionVault;
import net.kdt.pojavlaunch.quasar.shield.ShaderShield;

/**
 * ModernGLCompat — extreme OpenGL 3.3–4.6 → Android GLES compatibility hub.
 */
public final class ModernGLCompat {
    private static final String TAG = "Quasar-ModernGL";

    private static volatile boolean active = false;
    private static volatile MobileFeatureBridge bridge;
    private static volatile GLVersionProfile profile;

    private ModernGLCompat() {}

    public static synchronized void activate(CapabilityTable caps) {
        if (active && bridge != null) {
            Log.w(TAG, "ModernGLCompat already active: " + bridge.summary());
            return;
        }
        int gles = caps != null ? caps.getGlesVersion() : 30;
        if (gles < 30) gles = 30;
        profile = GLVersionProfile.fromGlesVersion(gles, caps != null ? caps.getGpuVendor() : "unknown");
        bridge = new MobileFeatureBridge(caps, profile);

        boolean mali = caps != null && "mali".equalsIgnoreCase(caps.getGpuVendor());
        if (mali) {
            ShaderShield.setMode(ShaderShield.Mode.EXTREME);
        } else {
            ShaderShield.setMode(ShaderShield.Mode.BALANCED);
        }

        DSAEmulator.markActive();
        active = true;

        Log.i(TAG, "Activated: " + bridge.summary());
        Log.i(TAG, "Catalog features=" + OpenGLFeatureCatalog.size()
                + " ShaderShield=" + ShaderShield.getMode()
                + " ExtensionVault strip=" + ExtensionVault.STRIP_ALWAYS.size());
        Log.i(TAG, "Desktop→mobile claim: " + profile.toDesktopCompatString());
    }

    public static boolean isActive() {
        return active;
    }

    public static MobileFeatureBridge bridge() {
        return bridge;
    }

    public static GLVersionProfile profile() {
        return profile;
    }

    public static boolean shouldStripExtension(String extName) {
        if (bridge != null) return bridge.shouldStripExtension(extName);
        return ExtensionVault.shouldStrip(extName, true);
    }

    public static String joinExtension(String extName) {
        if (bridge != null) return bridge.joinExtensionOrNull(extName);
        return ExtensionVault.joinOrNull(extName);
    }

    public static String statusLine() {
        if (!active || bridge == null) return "ModernGLCompat=off";
        return bridge.summary() + " shield=" + ShaderShield.stats();
    }

    public static synchronized void deactivate() {
        active = false;
        bridge = null;
        profile = null;
        ShaderShield.clearCache();
        Log.i(TAG, "Deactivated");
    }
}
