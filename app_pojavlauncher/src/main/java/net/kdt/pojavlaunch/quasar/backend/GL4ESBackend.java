package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;
import java.util.Map;

/**
 * GL4ESBackend - Fallback rendering path translating GL calls directly to GLES.
 */
public class GL4ESBackend {
    private static final String TAG = "GL4ESBackend";

    public static void configureEnvironment(Map<String, String> env) {
        Log.i(TAG, "[Quasar] Configuring GL4ES Fallback (Direct GLES) environment profile.");
        env.put("EGL_PLATFORM", "android");
        env.put("LIBGL_NOERROR", "1");
        env.put("LIBGL_FBOTEXTURE2D", "1");
        env.put("LIBGL_MIPMAP", "3");
        env.put("LIBGL_COLOR_RESCALE", "1");
        env.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
        env.put("gl_draw_buffers_override", "true");
        env.put("glsl_force_highp", "true");
        env.put("glsl_ignore_noperspective", "true");
    }
}
