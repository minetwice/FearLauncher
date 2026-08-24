package net.kdt.pojavlaunch.quasar;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.backend.BackendSelector;
import net.kdt.pojavlaunch.quasar.backend.RenderBackend;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.capability.DeviceCapabilityProbe;
import net.kdt.pojavlaunch.quasar.stage.QuasarPipeline;
import net.kdt.pojavlaunch.quasar.transpile.ShaderCache;

/**
 * QuasarRenderer is the entry point for the Quasar rendering subsystem.
 *
 * Quasar is a translation + compatibility layer that allows PC Java Edition
 * shaderpacks (Iris-compatible: Complementary, BSL, etc., and legacy OptiFine
 * shaders) to run on Android across both Mali (ARM) and Adreno (Qualcomm) GPUs.
 *
 * Lifecycle:
 * 1. probe() - detect device capabilities at launcher startup
 * 2. selectBackend() - choose Zink (primary) or GL4ES (fallback)
 * 3. loadShaderpack() - transpile and cache shaders
 * 4. renderFrame() - per-frame rendering through the selected backend
 */
public class QuasarRenderer {
    private static final String TAG = "QuasarRenderer";
    private static QuasarRenderer instance;

    private CapabilityTable capabilityTable;
    private RenderBackend activeBackend;
    private ShaderCache shaderCache;
    private QuasarPipeline pipeline;
    private boolean initialized = false;

    private QuasarRenderer() {}

    public static synchronized QuasarRenderer getInstance() {
        if (instance == null) {
            instance = new QuasarRenderer();
        }
        return instance;
    }

    /**
     * Initialize Quasar: probe device capabilities and select backend.
     * Call this at launcher startup, before any shaderpack is loaded.
     */
    public void initialize(Context context) {
        if (initialized) {
            Log.w(TAG, "Quasar already initialized, skipping");
            return;
        }

        Log.i(TAG, "Initializing Quasar renderer subsystem...");

        DeviceCapabilityProbe probe = new DeviceCapabilityProbe();
        capabilityTable = probe.probe(context);
        Log.i(TAG, "Device capability table: " + capabilityTable.toString());

        shaderCache = new ShaderCache(context);
        Log.i(TAG, "Shader cache initialized at: " + shaderCache.getCachePath());

        pipeline = new QuasarPipeline(capabilityTable, shaderCache);
        Log.i(TAG, "5-Stage Quasar Shader Translator Net pipeline initialized!");

        Log.i(TAG, "Selecting render backend...");
        activeBackend = BackendSelector.select(capabilityTable);
        Log.i(TAG, "Selected backend: " + activeBackend.getBackendName());

        initialized = true;
        Log.i(TAG, "Quasar initialization complete");
    }

    public RenderBackend getActiveBackend() {
        return activeBackend;
    }

    public CapabilityTable getCapabilityTable() {
        return capabilityTable;
    }

    public ShaderCache getShaderCache() {
        return shaderCache;
    }

    public QuasarPipeline getPipeline() {
        return pipeline;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void shutdown() {
        Log.i(TAG, "Shutting down Quasar renderer subsystem...");
        if (activeBackend != null) {
            activeBackend.cleanup();
            activeBackend = null;
        }
        if (shaderCache != null) {
            shaderCache.flush();
            shaderCache = null;
        }
        pipeline = null;
        capabilityTable = null;
        initialized = false;
        Log.i(TAG, "Quasar shutdown complete");
    }
}
