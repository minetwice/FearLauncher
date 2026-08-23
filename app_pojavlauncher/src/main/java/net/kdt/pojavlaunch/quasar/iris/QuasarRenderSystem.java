package net.kdt.pojavlaunch.quasar.iris;

import android.util.Log;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * QuasarRenderSystem - Implementation of Iris GL Abstraction interface with graceful feature degradation.
 */
public class QuasarRenderSystem {
    private static final String TAG = "QuasarRenderSystem";
    private final CapabilityTable mCaps;

    public QuasarRenderSystem(CapabilityTable caps) {
        this.mCaps = caps;
    }

    public void initializeIrisBridge() {
        Log.i(TAG, "[Quasar] QuasarRenderSystem Iris GL Abstraction Bridge Active.");
        if (mCaps != null) {
            if (!mCaps.supportsComputeShaders) {
                Log.w(TAG, "[Quasar] Graceful Degradation: Compute shaders unsupported on this GPU profile. Disabling compute passes.");
            }
            if (!mCaps.supportsGeometryShaders) {
                Log.w(TAG, "[Quasar] Graceful Degradation: Geometry shaders unsupported. Falling back to CPU/transform passes.");
            }
        }
    }

    public boolean isPassSupported(String passName) {
        if ("compute_pass".equalsIgnoreCase(passName)) {
            return mCaps != null && mCaps.supportsComputeShaders;
        }
        if ("geometry_pass".equalsIgnoreCase(passName)) {
            return mCaps != null && mCaps.supportsGeometryShaders;
        }
        return true;
    }
}
