package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

/**
 * RenderPluginManager - Java-side manager for custom renderer plugin injection.
 *
 * This class provides a high-level API for loading, unloading, and querying
 * custom renderer plugins (.so files) that inject into the GL/EGL dispatch chain.
 *
 * It is the Java counterpart to the native fear_plugin_loader.cpp, communicating
 * via JNI methods declared in JREUtils.
 *
 * Usage flow:
 *   1. User selects "Custom Render Injection" in renderer settings
 *   2. User picks a .so file via file picker (path saved in PREF_CUSTOM_RENDERER_PATH)
 *   3. Before game launch, RenderPluginManager.loadPlugin() is called
 *   4. The native plugin's functions override the built-in FearRender hooks
 *   5. On game exit, RenderPluginManager.unloadPlugin() is called
 *
 * Plugin .so contract (must export these C symbols):
 *   int     fear_plugin_init(void* userdata);
 *   void    fear_plugin_shutdown(void);
 *   void*   fear_plugin_get_proc(const char* name);
 *   const char* fear_plugin_get_name(void);
 *   const char* fear_plugin_get_version(void);
 */
public class RenderPluginManager {
    private static final String TAG = "RenderPluginManager";

    private static boolean sInitialized = false;
    private static String sLoadedPluginPath = null;

    /**
     * Load a custom renderer plugin from the given path.
     * The plugin must be a .so file implementing the FearRender Plugin API.
     *
     * @param context  Android context
     * @param pluginPath  Absolute path to the .so plugin file
     * @return true if the plugin was loaded successfully
     */
    public static boolean loadPlugin(Context context, String pluginPath) {
        if (pluginPath == null || pluginPath.isEmpty()) {
            Log.e(TAG, "Cannot load plugin: path is null or empty");
            return false;
        }

        File pluginFile = new File(pluginPath);
        if (!pluginFile.exists()) {
            Log.e(TAG, "Plugin file does not exist: " + pluginPath);
            return false;
        }

        if (!pluginFile.canRead()) {
            Log.e(TAG, "Cannot read plugin file: " + pluginPath);
            return false;
        }

        // Check if a plugin is already loaded
        if (JREUtils.isRenderPluginLoaded()) {
            Log.w(TAG, "A plugin is already loaded, unloading first: " + sLoadedPluginPath);
            unloadPlugin();
        }

        Logger.appendToLog("[RenderPlugin] Loading custom renderer plugin: " + pluginPath);
        Log.i(TAG, "Loading custom renderer plugin: " + pluginPath);

        boolean success = JREUtils.loadRenderPlugin(pluginPath);
        if (success) {
            sLoadedPluginPath = pluginPath;
            sInitialized = true;

            String name = JREUtils.getRenderPluginName();
            String version = JREUtils.getRenderPluginVersion();
            int overrides = JREUtils.getRenderPluginOverrideCount();

            Logger.appendToLog("[RenderPlugin] Plugin loaded: " + name + " v" + version
                    + " (overrides: " + overrides + " functions)");
            Log.i(TAG, "Plugin loaded: " + name + " v" + version
                    + " (overrides: " + overrides + " functions)");
        } else {
            Logger.appendToLog("[RenderPlugin] FAILED to load plugin: " + pluginPath);
            Log.e(TAG, "Failed to load plugin: " + pluginPath);
        }

        return success;
    }

    /**
     * Load the plugin from the saved preference path.
     * Call this before game launch if the renderer is set to "custom_inject".
     *
     * @param context  Android context
     * @return true if a plugin was loaded (or no plugin was needed)
     */
    public static boolean loadPluginFromPrefs(Context context) {
        String path = LauncherPreferences.PREF_CUSTOM_RENDERER_PATH;
        if (path == null || path.isEmpty()) {
            Log.w(TAG, "No custom renderer plugin path in preferences");
            return false;
        }
        return loadPlugin(context, path);
    }

    /**
     * Unload the currently loaded renderer plugin.
     */
    public static void unloadPlugin() {
        if (!sInitialized) return;

        Logger.appendToLog("[RenderPlugin] Unloading renderer plugin: " + sLoadedPluginPath);
        Log.i(TAG, "Unloading renderer plugin: " + sLoadedPluginPath);

        JREUtils.unloadRenderPlugin();
        sLoadedPluginPath = null;
        sInitialized = false;
    }

    /**
     * Check if a renderer plugin is currently loaded.
     */
    public static boolean isPluginLoaded() {
        return JREUtils.isRenderPluginLoaded();
    }

    /**
     * Get the name of the currently loaded plugin.
     */
    public static String getPluginName() {
        return JREUtils.getRenderPluginName();
    }

    /**
     * Get the version of the currently loaded plugin.
     */
    public static String getPluginVersion() {
        return JREUtils.getRenderPluginVersion();
    }

    /**
     * Get the number of GL/EGL functions overridden by the plugin.
     */
    public static int getOverrideCount() {
        return JREUtils.getRenderPluginOverrideCount();
    }

    /**
     * Get the path of the currently loaded plugin.
     */
    public static String getLoadedPluginPath() {
        return sLoadedPluginPath;
    }

    /**
     * Validate that a .so file looks like a valid FearRender plugin.
     * This does a basic check that the file exists and has a .so extension.
     * A more thorough check would require dlopen, which is done in loadPlugin().
     *
     * @param pluginPath  Path to the .so file
     * @return true if the file appears to be a valid plugin
     */
    public static boolean validatePluginFile(String pluginPath) {
        if (pluginPath == null || pluginPath.isEmpty()) return false;

        File f = new File(pluginPath);
        if (!f.exists() || !f.canRead()) return false;
        if (!f.getName().endsWith(".so")) return false;

        // Minimum reasonable size for a plugin (arbitrary: 10KB)
        if (f.length() < 10240) {
            Log.w(TAG, "Plugin file seems too small: " + f.length() + " bytes");
            return false;
        }

        return true;
    }
}
