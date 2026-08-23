package net.kdt.pojavlaunch.quasar;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.Map;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.capability.DeviceCapabilityProbe;
import net.kdt.pojavlaunch.quasar.backend.BackendSelector;
import net.kdt.pojavlaunch.quasar.transpile.ShaderCache;
import net.kdt.pojavlaunch.quasar.iris.QuasarRenderSystem;

/**
 * QuasarRenderer - Entry point and lifecycle manager with step-by-step try-catch error recovery.
 */
public class QuasarRenderer {
    private static final String TAG = "QuasarRenderer";
    private static QuasarRenderer sInstance;

    private CapabilityTable mCapabilityTable;
    private ShaderCache mShaderCache;
    private QuasarRenderSystem mRenderSystem;
    private BackendSelector.BackendType mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;

    public static synchronized QuasarRenderer getInstance() {
        if (sInstance == null) {
            sInstance = new QuasarRenderer();
        }
        return sInstance;
    }

    public void initialize(Context context, File baseCacheDir) {
        Log.i(TAG, "[Quasar] Starting Quasar Translation Subsystem initialization...");

        // Step 1: Device Capability Probe
        try {
            mCapabilityTable = DeviceCapabilityProbe.probeCapabilities(context);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 1 Failed: DeviceCapabilityProbe threw an error. Using fallback capability table.", t);
            mCapabilityTable = new CapabilityTable();
        }

        // Step 2: Backend Selection
        try {
            mSelectedBackend = BackendSelector.selectBackend(mCapabilityTable);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 2 Failed: BackendSelector error. Falling back to GL4ES_GLES.", t);
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        // Step 3: Shader Cache Setup
        try {
            mShaderCache = new ShaderCache(baseCacheDir);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 3 Failed: ShaderCache initialization error.", t);
            mShaderCache = null;
        }

        // Step 4: Iris RenderSystem Bridge Initialization
        try {
            mRenderSystem = new QuasarRenderSystem(mCapabilityTable);
            mRenderSystem.initializeIrisBridge();
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 4 Failed: Iris bridge error. Re-enabling safe fallback state.", t);
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        Log.i(TAG, "[Quasar] Initialization finished. Active Backend: " + mSelectedBackend);
    }

    public void configureEnvironment(Map<String, String> env, String cacheDir) {
        try {
            mSelectedBackend = BackendSelector.verifyAndApplyBackend(mSelectedBackend, env, cacheDir);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Error applying backend environment. Forcing GL4ES fallback.", t);
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
            BackendSelector.applyBackendEnvironment(mSelectedBackend, env, cacheDir);
        }
    }

    public CapabilityTable getCapabilityTable() {
        return mCapabilityTable;
    }

    public ShaderCache getShaderCache() {
        return mShaderCache;
    }

    public QuasarRenderSystem getRenderSystem() {
        return mRenderSystem;
    }

    public BackendSelector.BackendType getSelectedBackend() {
        return mSelectedBackend;
    }
}
