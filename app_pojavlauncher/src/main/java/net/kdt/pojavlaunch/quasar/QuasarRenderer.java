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
 * QuasarRenderer - Main entry point and lifecycle manager for Quasar rendering subsystem.
 * Every step in initialize() is individually wrapped in try-catch to ensure GL4ES_GLES fallback without throwing.
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

        // Step 1: probeCapabilities
        try {
            mCapabilityTable = DeviceCapabilityProbe.probeCapabilities(context);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 1 Failed: DeviceCapabilityProbe.probeCapabilities threw an error. Falling back to conservative table.", t);
            mCapabilityTable = new CapabilityTable();
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        // Step 2: selectBackend
        try {
            mSelectedBackend = BackendSelector.selectBackend(mCapabilityTable);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 2 Failed: BackendSelector.selectBackend threw an error. Falling back to GL4ES_GLES.", t);
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        // Step 3: new ShaderCache(...)
        try {
            mShaderCache = new ShaderCache(baseCacheDir);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 3 Failed: new ShaderCache(...) threw an error. Shader caching disabled.", t);
            mShaderCache = null;
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        // Step 4: new QuasarRenderSystem(...)
        try {
            mRenderSystem = new QuasarRenderSystem(mCapabilityTable);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 4 Failed: new QuasarRenderSystem(...) threw an error. Falling back to GL4ES_GLES.", t);
            mRenderSystem = null;
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        // Step 5: initializeIrisBridge()
        try {
            if (mRenderSystem != null) {
                mRenderSystem.initializeIrisBridge();
            }
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Step 5 Failed: initializeIrisBridge() threw an error. Falling back to GL4ES_GLES.", t);
            mSelectedBackend = BackendSelector.BackendType.GL4ES_GLES;
        }

        Log.i(TAG, "[Quasar] Initialization finished safely. Active Backend: " + mSelectedBackend);
    }

    public void configureEnvironment(Map<String, String> env, String cacheDir) {
        try {
            mSelectedBackend = BackendSelector.verifyAndApplyBackend(mSelectedBackend, env, cacheDir);
        } catch (Throwable t) {
            Log.e(TAG, "[Quasar] Error configuring environment. Forcing GL4ES fallback.", t);
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
