package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Build;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.ArrayList;
import java.util.List;

public class RendererCompatUtil {
    public static class CompatibleRenderers {
        public final List<String> rendererIds;
        public final List<String> rendererNames;
        public CompatibleRenderers(List<String> rendererIds, List<String> rendererNames) {
            this.rendererIds = rendererIds;
            this.rendererNames = rendererNames;
        }
    }

    public static CompatibleRenderers getCompatibleRenderers(Context context) {
        List<String> rendererIds = new ArrayList<>();
        List<String> rendererNames = new ArrayList<>();

        rendererIds.add("opengles3_ltw");
        rendererNames.add("OpenGL ES 3.2 (LTW)");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rendererIds.add("vulkan");
            rendererNames.add("Vulkan (Turnip/Zink)");
        }

        rendererIds.add("opengles2");
        rendererNames.add("OpenGL ES 2.0 (gl4es 1.1.4)");

        rendererIds.add("fear_turbo");
        rendererNames.add("Fear Turbo - Custom GL Translation Engine");

        return new CompatibleRenderers(rendererIds, rendererNames);
    }

    public static boolean isRendererCompatible(String rendererId, JMinecraftVersionList.Version version) {
        if (rendererId.equals("opengles3_ltw")) return true;
        if (rendererId.equals("vulkan")) return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        if (rendererId.equals("opengles2")) return true;
        if (rendererId.equals("fear_turbo")) return true;
        return false;
    }

    public static void releaseRenderersCache() {
        // No-op for now
    }
}
