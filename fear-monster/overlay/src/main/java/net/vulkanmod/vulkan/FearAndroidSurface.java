package net.vulkanmod.vulkan;

import java.nio.LongBuffer;

/**
 * The Android surface for the Fear Monster engine.
 *
 * <p>VulkanMod gets its surface from GLFW:
 *
 * <pre>
 *   glfwCreateWindowSurface(Instance.instance, window, null, pSurface)
 * </pre>
 *
 * That works on a desktop, where GLFW has a Win32 window or an X11 display to build a
 * surface from. On Android there is neither - there is an ANativeWindow - so the only way
 * to a VkSurfaceKHR is vkCreateAndroidSurfaceKHR, and GLFW's call cannot succeed here.
 * That is why the engine never had a surface to render into.
 *
 * <p>This class is the engine's half of the fix. The call itself is native, because
 * building the surface needs the ANativeWindow and the loader, and those live in
 * fearmonster_vk.c, which the launcher builds and this class loads.
 *
 * <p>It lives in the overlay rather than in the launcher because the engine runs as a mod
 * inside the game: none of the launcher's classes are on that classpath, so the engine has
 * to reach the native side itself.
 *
 * <p>The window comes from -Dfear.monster.window, which the launcher sets when it hands
 * the window over on this path.
 */
public final class FearAndroidSurface {

    private FearAndroidSurface() {}

    /**
     * vkCreateAndroidSurfaceKHR, in fearmonster_vk.c. Returns a VkResult, so the caller can
     * keep using its own checkResult, and writes the surface into pSurface.
     */
    private static native int nativeCreateSurface(long instance, long window, LongBuffer pSurface);

    static {
        try {
            System.loadLibrary("fearmonster_vk");
        } catch (Throwable t) {
            // Not fatal here: the call below fails with a clear message, and the engine
            // reports that far better than an exception from a class initialiser.
            System.err.println("[FearMonster] could not load fearmonster_vk: " + t);
        }
    }

    /**
     * Builds the surface from the window the launcher handed over. Returns a VkResult; on
     * anything but success the surface buffer is left untouched.
     */
    public static int createSurface(long instance, LongBuffer pSurface) {
        long window = 0L;
        try {
            window = Long.getLong("fear.monster.window", 0L);
        } catch (Throwable ignored) { }
        if (window == 0L) {
            System.err.println("[FearMonster] no window: -Dfear.monster.window was not set");
            return -1;
        }
        return nativeCreateSurface(instance, window, pSurface);
    }
}
