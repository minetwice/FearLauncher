package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import net.kdt.pojavlaunch.PojavLauncherActivity;
import net.kdt.pojavlaunch.value.LWJGLVersion;

public class JREUtils {
    private static final String TAG = "JREUtils";
    
    private static final String[] VULKAN_LIBRARIES = {
        "https://github.com/minetwice/FearLauncher/releases/download/vulkan-libs/libvulkan.so",
        "https://github.com/minetwice/FearLauncher/releases/download/vulkan-libs/libvulkan_mesa.so"
    };
    
    private static final ReentrantLock vulkanInitLock = new ReentrantLock();
    private static volatile boolean vulkanInitialized = false;
    
    static {
        ensureVulkanLibraries();
    }
    
    public static void ensureVulkanLibraries() {
        if (vulkanInitialized) return;
        vulkanInitLock.lock();
        try {
            if (vulkanInitialized) return;
            Log.i(TAG, "Initializing Vulkan libraries for TurboV1...");
            File nativeDir = new File(PojavLauncherActivity.getPojavContext().getApplicationInfo().nativeLibraryDir);
            File vulkanDir = new File(nativeDir.getParentFile(), "vulkan");
            if (!vulkanDir.exists()) vulkanDir.mkdirs();
            for (String libUrl : VULKAN_LIBRARIES) {
                String libName = libUrl.substring(libUrl.lastIndexOf('/') + 1);
                File libFile = new File(vulkanDir, libName);
                if (!libFile.exists()) {
                    Log.i(TAG, "Downloading Vulkan library: " + libName);
                    downloadFile(libUrl, libFile);
                } else {
                    Log.i(TAG, "Vulkan library already exists: " + libName);
                }
            }
            String ldPath = System.getenv("LD_LIBRARY_PATH");
            String newLdPath = (ldPath != null ? ldPath + ":" : "") + vulkanDir.getAbsolutePath();
            System.setProperty("java.library.path", newLdPath);
            preloadVulkanLibraries();
            setVulkanEnvironment();
            vulkanInitialized = true;
            Log.i(TAG, "Vulkan libraries initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Vulkan libraries", e);
        } finally {
            vulkanInitLock.unlock();
        }
    }
    
    private static void setVulkanEnvironment() {
        System.setProperty("GALLIUM_DRIVER", "zink");
        System.setProperty("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        System.setProperty("MESA_GLSL_VERSION_OVERRIDE", "460");
        System.setProperty("MESA_GL_VERSION_OVERRIDE", "4.6");
        System.setProperty("MESA_VK_WSI_PRESENT_MODE", "mailbox");
        System.setProperty("POJAV_VSYNC_IN_ZINK", "1");
    }
    
    private static void downloadFile(String url, File destination) {
        try (InputStream in = new URL(url).openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(destination)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            destination.setExecutable(true);
            destination.setReadable(true);
            Log.i(TAG, "Successfully downloaded: " + destination.getName());
        } catch (IOException e) {
            Log.e(TAG, "Failed to download " + url, e);
        }
    }
    
    private static void preloadVulkanLibraries() {
        try {
            File nativeDir = new File(PojavLauncherActivity.getPojavContext().getApplicationInfo().nativeLibraryDir);
            File vulkanDir = new File(nativeDir.getParentFile(), "vulkan");
            try {
                System.load(new File(vulkanDir, "libvulkan.so").getAbsolutePath());
                Log.i(TAG, "Successfully preloaded libvulkan.so");
            } catch (Exception e) {
                Log.w(TAG, "libvulkan.so not found, trying system library");
                try {
                    System.loadLibrary("vulkan");
                    Log.i(TAG, "Successfully preloaded system libvulkan.so");
                } catch (Exception e2) {
                    Log.w(TAG, "System libvulkan.so also not found");
                }
            }
            try {
                System.load(new File(vulkanDir, "libvulkan_mesa.so").getAbsolutePath());
                Log.i(TAG, "Successfully preloaded libvulkan_mesa.so");
            } catch (Exception e) {
                Log.w(TAG, "libvulkan_mesa.so not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preloading Vulkan libraries", e);
        }
    }

    public static void prepareForGLFWInit() {
        ensureVulkanLibraries();
        File nativeDir = new File(PojavLauncherActivity.getPojavContext().getApplicationInfo().nativeLibraryDir);
        File vulkanDir = new File(nativeDir.getParentFile(), "vulkan");
        String currentLdPath = System.getenv("LD_LIBRARY_PATH");
        if (currentLdPath == null || !currentLdPath.contains(vulkanDir.getAbsolutePath())) {
            String newLdPath = (currentLdPath != null ? currentLdPath + ":" : "") + vulkanDir.getAbsolutePath();
            System.setProperty("java.library.path", newLdPath);
            try {
                java.lang.reflect.Field envField = System.class.getDeclaredField("env");
                envField.setAccessible(true);
                Map<String, String> env = (Map<String, String>) envField.get(null);
                env.put("LD_LIBRARY_PATH", newLdPath);
            } catch (Exception e) {
                Log.w(TAG, "Could not update LD_LIBRARY_PATH environment variable", e);
            }
        }
    }

    public static void redirectAndPrintJRELog() {
        new Thread(() -> {
            try {
                Process process = new ProcessBuilder(
                    PojavLauncherActivity.getPojavContext().getApplicationInfo().nativeLibraryDir + "/java",
                    "-jar",
                    PojavLauncherActivity.getPojavContext().getCacheDir() + "/app_runtime/bin/jre/bin/jrelog.jar"
                ).redirectErrorStream(true).start();
                BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("jrelog") || line.contains("LIBGL") || 
                        line.contains("NativeInput") || line.contains("FEAR") || 
                        line.contains("FearRender") || line.contains("Mesa")) {
                        Log.i("jrelog", line);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static Map<String, String> setEnvironmentForGame(Context context, LWJGLVersion lwjglVersion, String glesVersion) {
        HashMap<String, String> envMap = new HashMap<>();
        prepareForGLFWInit();
        envMap.put("GALLIUM_DRIVER", "zink");
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        envMap.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
        envMap.put("POJAV_VSYNC_IN_ZINK", "1");
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File vulkanDir = new File(nativeDir.getParentFile(), "vulkan");
        String libPath = nativeDir.getAbsolutePath() + ":" + vulkanDir.getAbsolutePath();
        envMap.put("LD_LIBRARY_PATH", libPath);
        envMap.put("LIBGL_NOINTOVLHACK", "1");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("LIBGL_VSYNC", "0");
        envMap.put("LIBGL_NORMALIZE", "1");
        envMap.put("LIBGL_MIPMAP", "3");
        envMap.put("vblank_mode", "0");
        envMap.put("FORCE_VSYNC", "0");
        envMap.put("MESA_PRESENT_MODE", "mailbox");
        envMap.put("LIBGL_ES", glesVersion);
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        File cacheDir = new File(context.getCacheDir(), "mesa_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        envMap.put("MESA_GLSL_CACHE_DIR", cacheDir.getAbsolutePath());
        envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
        envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "4096MB");
        envMap.put("TU_DEBUG", "sysmem");
        envMap.put("POJAV_NATIVEDIR", nativeDir.getAbsolutePath());
        return envMap;
    }

    public static String getJavaHome() {
        return PojavLauncherActivity.getPojavContext().getCacheDir() + "/app_runtime";
    }
}