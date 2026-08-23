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
 */
public class QuasarRenderer {
    private static final String TAG = "QuasarRenderer";
    private static QuasarRenderer sInstance;

    private CapabilityTable mCapabilityTable;
    private ShaderCache mShaderCache;
    private QuasarRenderSystem mRenderSystem;
    private BackendSelector.BackendType mSelectedBackend;

    public static synchronized QuasarRenderer getInstance() {
        if (sInstance == null) {
            sInstance = new QuasarRenderer();
        }
        return sInstance;
    }

    public void initialize(Context context, File baseCacheDir) {
        Log.i(TAG, "[Quasar] Initializing Quasar Translation Subsystem...");
        mCapabilityTable = DeviceCapabilityProbe.probeCapabilities(context);
        mSelectedBackend = BackendSelector.selectBackend(mCapabilityTable);
        mShaderCache = new ShaderCache(baseCacheDir);
        mRenderSystem = new QuasarRenderSystem(mCapabilityTable);
        mRenderSystem.initializeIrisBridge();
    }

    public void configureEnvironment(Map<String, String> env, String cacheDir) {
        BackendSelector.applyBackendEnvironment(mSelectedBackend, env, cacheDir);
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
}
