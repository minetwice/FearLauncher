package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;
import java.util.Map;

/**
 * ZinkBackend - Primary rendering path converting desktop OpenGL calls into Vulkan.
 */
public class ZinkBackend {
    private static final String TAG = "ZinkBackend";

    public static void configureEnvironment(Map<String, String> env, String cacheDir) {
        Log.i(TAG, "[Quasar] Configuring Zink (OpenGL-over-Vulkan) environment profile.");
        env.put("EGL_PLATFORM", "android");
        env.put("GALLIUM_DRIVER", "zink");
        env.put("MESA_VK_WSI_PRESENT_MODE", "fifo");
        env.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        env.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        env.put("MESA_NO_MINMAX_CACHE", "1");
        env.put("MESA_NO_ERROR", "0");
        env.put("MESA_GLSL_CACHE_DIR", cacheDir + "/mesa_shader_cache");
        env.put("MESA_GLSL_CACHE_DISABLE", "false");
    }
}
