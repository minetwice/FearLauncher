package net.kdt.pojavlaunch.gpu;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;

/**
 * Manager for Krypton Wrapper (NG-GL4ES/NGG-FCLRendererPlugin) renderer configuration.
 * This class provides GPU-specific initialization and environment setup for
 * Krypton Wrapper, a Mesa-based GL4ES fork that supports shaders without MobileGlues.
 */
public class KryptonWrapperManager {
    
    private static final String TAG = "KryptonWrapperManager";
    private static boolean initialized = false;
    
    // GPU detection
    private static String cachedGpuModel = null;
    
    /**
     * Get the current GPU model
     */
    public static String getGpuModel() {
        if (cachedGpuModel != null) {
            return cachedGpuModel;
        }
        
        // Try different methods to get GPU model
        String gpu = System.getProperty("ro.product.gpu", "");
        if (!gpu.isEmpty()) {
            cachedGpuModel = gpu;
            return gpu;
        }
        
        gpu = System.getProperty("ro.board.platform", "");
        if (!gpu.isEmpty()) {
            cachedGpuModel = gpu;
            return gpu;
        }
        
        // Try Build class
        try {
            Field field = Build.class.getDeclaredField("GPU");
            if (field != null) {
                field.setAccessible(true);
                cachedGpuModel = (String) field.get(null);
                return cachedGpuModel;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get GPU from Build.GPU", e);
        }
        
        // Fallback: check for Mali-G615 in device model
        String deviceModel = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        if (deviceModel.contains("motorola") && deviceModel.contains("edge 60 fusion")) {
            cachedGpuModel = "Mali-G615";
            return cachedGpuModel;
        }
        
        cachedGpuModel = "";
        return cachedGpuModel;
    }
    
    /**
     * Apply Krypton Wrapper specific environment variables.
     * 
     * This MUST be called early in the application lifecycle, before
     * any native libraries are loaded.
     */
    public static void applyKryptonWrapperEnv() {
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU for Krypton Wrapper: " + gpuModel);
        
        // Krypton Wrapper (NG-GL4ES) environment variables
        // These enable the NG-GL4ES library to work properly
        
        // Force using NG-GL4ES as the GL library
        System.setProperty("LIBGL_LIBNAME", "libNG-GL4ES.so");
        System.setProperty("LIB_GL_LIBNAME", "libNG-GL4ES.so");
        
        // Enable shader support
        System.setProperty("NGGL_ENABLE_SHADERS", "1");
        System.setProperty("NGGL_SHADER_PATH", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache/shaders");
        
        // Performance and compatibility settings
        System.setProperty("NGGL_DEBUG", "0"); // 0 = no debug, 1 = info, 2 = warn, 3 = error
        System.setProperty("NGGL_OPTIMIZE", "1"); // Enable optimizations
        System.setProperty("NGGL_MULTITHREAD", "1"); // Enable multithreading if supported
        
        // OpenGL version overrides for compatibility
        System.setProperty("MESA_GL_VERSION_OVERRIDE", "4.6");
        System.setProperty("MESA_GLSL_VERSION_OVERRIDE", "460");
        
        // Vulkan driver override (Krypton Wrapper uses Vulkan backend)
        System.setProperty("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        System.setProperty("GALLIUM_DRIVER", "zink");
        
        // Shader cache settings
        System.setProperty("MESA_SHADER_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DISABLE", "false");
        
        // Performance settings
        System.setProperty("LIBGL_NOINTOVLHACK", "1");
        System.setProperty("LIBGL_NOERROR", "1");
        System.setProperty("LIBGL_NORMALIZE", "1");
        System.setProperty("LIBGL_MIPMAP", "3");
        
        // Krypton Wrapper specific
        System.setProperty("POJAV_RENDERER", "krypton_wrapper");
        System.setProperty("FEAR_RENDERER", "krypton_wrapper");
        
        // GPU-specific workarounds for Mali GPUs
        if (gpuModel.toLowerCase().contains("mali")) {
            applyMaliWorkarounds();
        }
        
        Log.i(TAG, "Applied Krypton Wrapper environment variables");
    }
    
    /**
     * Apply Mali-specific workarounds for Krypton Wrapper
     */
    private static void applyMaliWorkarounds() {
        Log.i(TAG, "Applying Mali GPU workarounds for Krypton Wrapper");
        
        // Mali GPUs may need these specific settings
        System.setProperty("NGGL_FORCE_GLES3", "1");
        System.setProperty("NGGL_EMULATE_UNSIGNED_INT", "1");
        
        // For Mali-G615 specifically
        String gpuModel = getGpuModel().toLowerCase();
        if (gpuModel.contains("mali-g615") || gpuModel.contains("mali g615")) {
            System.setProperty("NGGL_SOFTWARE_CLIP", "1");
            System.setProperty("NGGL_SOFTWARE_LOGIC_OP", "1");
            Log.w(TAG, "Mali-G615 detected - applying software fallbacks for missing Vulkan features");
        }
    }
    
    /**
     * Initialize Krypton Wrapper manager. This should be called early in
     * Application.onCreate() before any other initialization.
     */
    public static void initialize() {
        if (initialized) {
            Log.w(TAG, "KryptonWrapperManager already initialized");
            return;
        }
        
        Log.i(TAG, "Initializing Krypton Wrapper Manager");
        applyKryptonWrapperEnv();
        initialized = true;
        
        // Log GPU info
        String gpuModel = getGpuModel();
        if (!gpuModel.isEmpty()) {
            Log.i(TAG, "GPU: " + gpuModel);
        }
    }
    
    /**
     * Check if Krypton Wrapper has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if Krypton Wrapper libraries are available.
     */
    public static boolean hasKryptonWrapperLibraries() {
        File libDir = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/lib/arm64");
        File libNGGL4ES = new File(libDir, "libNG-GL4ES.so");
        File libNGGFCL = new File(libDir, "libngg_fcl.so");
        
        return libNGGL4ES.exists() || libNGGFCL.exists();
    }
}
