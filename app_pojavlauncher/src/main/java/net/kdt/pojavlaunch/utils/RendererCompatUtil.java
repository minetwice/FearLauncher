package net.kdt.pojavlaunch.utils;

import android.content.Context;

import net.kdt.pojavlaunch.R;

/**
 * Utility class for renderer compatibility checks and JNI library
 * resolution. Used to determine which renderers should be visible
 * in the launcher UI and how to load their native libraries.
 */
public class RendererCompatUtil {

    /**
     * Check if a renderer ID should be hidden from the default renderer list
     * (only shown in advanced/debug mode).
     */
    public static boolean isAdvancedRenderer(String rendererId) {
        if(rendererId.equals("fear_engine") || rendererId.equals("mh_drive") || rendererId.equals("quasar") || rendererId.equals("quasar_v2")) {
            return false;
        }
        return false;
    }

    /**
     * Check if a renderer requires Vulkan support.
     */
    public static boolean requiresVulkan(String rendererId) {
        return rendererId.equals("vulkan_zink") ||
               rendererId.equals("freedreno_kgsl") ||
               rendererId.equals("fear_engine");
    }

    /**
     * Check if a renderer uses a custom GL translation library
     * (not the system OpenGL ES driver directly).
     */
    public static boolean usesCustomGLLibrary(String rendererId) {
        return rendererId.equals("quasar") ||
               rendererId.equals("quasar_v2") ||
               rendererId.equals("opengles3_ltw") ||
               rendererId.equals("opengles3_mges") ||
               rendererId.equals("opengles3_mggl") ||
               rendererId.equals("opengles3_nggl4es") ||
               rendererId.equals("custom_inject") ||
               rendererId.equals("fear_engine") ||
               rendererId.equals("mh_drive");
    }

    /**
     * Get the display name for a renderer ID.
     */
    public static String getDisplayName(Context context, String rendererId) {
        String[] names = context.getResources().getStringArray(R.array.renderer);
        String[] values = context.getResources().getStringArray(R.array.renderer_values);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(rendererId)) {
                return names[i];
            }
        }
        return rendererId;
    }
}
