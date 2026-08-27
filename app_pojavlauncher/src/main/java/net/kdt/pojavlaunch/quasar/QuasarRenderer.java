package net.kdt.pojavlaunch.quasar;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.backend.BackendSelector;
import net.kdt.pojavlaunch.quasar.backend.RenderBackend;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.capability.DeviceCapabilityProbe;
import net.kdt.pojavlaunch.quasar.core.QuasarCore;
import net.kdt.pojavlaunch.quasar.gl.ModernGLCompat;
import net.kdt.pojavlaunch.quasar.gl.OpenGLFeatureCatalog;
import net.kdt.pojavlaunch.quasar.transpile.ShaderCache;

public class QuasarRenderer {
    private static final String TAG = "QuasarRenderer";
    private static QuasarRenderer instance;

    private CapabilityTable capabilityTable;
    private RenderBackend activeBackend;
    private ShaderCache shaderCache;
    private boolean initialized = false;

    private QuasarRenderer() {}

    public static synchronized QuasarRenderer getInstance() {
        if (instance == null) instance = new QuasarRenderer();
        return instance;
    }

    public void initialize(Context context) {
        if (initialized) {
            Log.w(TAG, "Quasar already initialized, skipping");
            return;
        }
        Log.i(TAG, "Initializing QuasarCore renderer (FearCore translator)...");
        DeviceCapabilityProbe probe = new DeviceCapabilityProbe();
        capabilityTable = probe.probe(context);
        Log.i(TAG, "Device capability table: " + capabilityTable.toString());
        QuasarCore.boot(context, capabilityTable);
        ModernGLCompat.activate(capabilityTable);
        Log.i(TAG, QuasarCore.status());
        Log.i(TAG, ModernGLCompat.statusLine());
        Log.i(TAG, "OpenGLFeatureCatalog size=" + OpenGLFeatureCatalog.size());
        shaderCache = new ShaderCache(context);
        activeBackend = BackendSelector.select(capabilityTable);
        Log.i(TAG, "Selected backend: " + activeBackend.getBackendName());
        initialized = true;
        Log.i(TAG, "Quasar initialization complete");
    }

    public RenderBackend getActiveBackend() { return activeBackend; }
    public CapabilityTable getCapabilityTable() { return capabilityTable; }
    public ShaderCache getShaderCache() { return shaderCache; }
    public boolean isInitialized() { return initialized; }

    public void shutdown() {
        Log.i(TAG, "Shutting down Quasar...");
        ModernGLCompat.deactivate();
        if (activeBackend != null) { activeBackend.cleanup(); activeBackend = null; }
        if (shaderCache != null) { shaderCache.flush(); shaderCache = null; }
        capabilityTable = null;
        initialized = false;
    }
}
