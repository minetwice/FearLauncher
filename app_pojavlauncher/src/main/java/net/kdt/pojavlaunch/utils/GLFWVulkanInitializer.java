package net.kdt.pojavlaunch.utils;

import android.util.Log;

public class GLFWVulkanInitializer {
    private static final String TAG = "GLFWVulkanInit";
    private static volatile boolean glfwInitialized = false;
    private static final Object initLock = new Object();
    
    public static void initGLFWForVulkan() {
        if (glfwInitialized) return;
        synchronized (initLock) {
            if (glfwInitialized) return;
            Log.i(TAG, "Initializing GLFW for Vulkan backend...");
            JREUtils.prepareForGLFWInit();
            try {
                Class.forName("org.lwjgl.vulkan.VK10");
                Log.i(TAG, "Vulkan bindings available");
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Vulkan bindings not available", e);
            }
            try {
                org.lwjgl.glfw.GLFW.glfwInit();
                Log.i(TAG, "GLFW initialized successfully for Vulkan");
                glfwInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize GLFW", e);
                throw new RuntimeException("GLFW initialization failed for Vulkan backend", e);
            }
        }
    }
    
    public static boolean isGLFWInitialized() {
        return glfwInitialized;
    }
}