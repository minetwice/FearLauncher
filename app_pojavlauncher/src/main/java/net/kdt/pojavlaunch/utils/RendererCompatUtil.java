package net.kdt.pojavlaunch.utils;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;

public class RendererCompatUtil {
    private static RenderersList sCompatibleRenderers;

    public static boolean checkVulkanSupport(PackageManager packageManager) {
        if(SDK_INT >= Build.VERSION_CODES.N) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        }
        return false;
    }

    /** Return the renderers that are compatible with this device */
    public static RenderersList getCompatibleRenderers(Context context) {
        if(sCompatibleRenderers != null) return sCompatibleRenderers;
        Resources resources = context.getResources();
        String[] defaultRenderers = resources.getStringArray(R.array.renderer_values);
        String[] defaultRendererNames = resources.getStringArray(R.array.renderer);
        boolean deviceHasVulkan = checkVulkanSupport(context.getPackageManager());
        // Current Mesa requires API29+
        boolean deviceCompatibleMesa = SDK_INT >= 29;
        boolean deviceHasOpenGLES3 = JREUtils.getDetectedVersion() >= 3;
        // LTW is an optional dependency
        boolean appHasLtw = new File(Tools.NATIVE_LIB_DIR, "libltw.so").exists();
        List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
        List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
        for(int i = 0; i < defaultRenderers.length; i++) {
            String rendererId = defaultRenderers[i];
            if(rendererId.equals("fear_engine") || rendererId.equals("mh_drive")) {
                rendererIds.add(rendererId);
                rendererNames.add(defaultRendererNames[i]);
                continue;
            }
            if(rendererId.contains("vulkan") && !deviceHasVulkan) continue;
            if(rendererId.contains("zink") && !deviceCompatibleMesa) continue;
            // freedreno and turnip are available primarily on Adreno GPUs
            if((rendererId.contains("freedreno") || rendererId.contains("turnip")) && (!(GLInfoUtils.getGlInfo().isAdreno()) || !deviceCompatibleMesa)) continue;
            if(rendererId.contains("ltw") && (!deviceHasOpenGLES3 || !appHasLtw)) continue;
            rendererIds.add(rendererId);
            rendererNames.add(defaultRendererNames[i]);
        }
        // Check for installed plugin renderers (e.g. Mobile Glue, Zalith Launcher custom renderer plugins)
        List<net.kdt.pojavlaunch.plugins.LibraryPlugin> rendererPlugins = net.kdt.pojavlaunch.plugins.LibraryPlugin.discoverRendererPlugins(context);
        for (net.kdt.pojavlaunch.plugins.LibraryPlugin plugin : rendererPlugins) {
            String pluginId = "plugin:" + plugin.getId();
            String displayName = plugin.getDisplayName();
            if (displayName == null || displayName.isEmpty() || displayName.equalsIgnoreCase(plugin.getId())) {
                displayName = "Mobile Glue Plugin (" + plugin.getId() + ")";
            }
            if (!rendererIds.contains(pluginId)) {
                rendererIds.add(pluginId);
                rendererNames.add(displayName);
            }
        }

        // Check for local custom .so renderers in PojavLauncher/renderers directory
        File customRenderersDir = new File(Tools.DIR_GAME_HOME, "renderers");
        if (customRenderersDir.exists() && customRenderersDir.isDirectory()) {
            File[] files = customRenderersDir.listFiles((dir, name) -> name.endsWith(".so"));
            if (files != null) {
                for (File file : files) {
                    String rendererId = "custom_so:" + file.getAbsolutePath();
                    String displayName = "Custom Renderer (" + file.getName() + ")";
                    if (!rendererIds.contains(rendererId)) {
                        rendererIds.add(rendererId);
                        rendererNames.add(displayName);
                    }
                }
            }
        }

        sCompatibleRenderers = new RenderersList(rendererIds,
                rendererNames.toArray(new String[0]));

        return sCompatibleRenderers;
    }

    /** Checks if the renderer Id is compatible with the current device */
    public static boolean checkRendererCompatible(Context context, String rendererName) {
         return getCompatibleRenderers(context).rendererIds.contains(rendererName);
    }

    /** Releases the cache of compatible renderers. */
    public static void releaseRenderersCache() {
        sCompatibleRenderers = null;
        System.gc();
    }

    public static class RenderersList {
        public final List<String> rendererIds;
        public final String[] rendererDisplayNames;

        public RenderersList(List<String> rendererIds, String[] rendererDisplayNames) {
            this.rendererIds = rendererIds;
            this.rendererDisplayNames = rendererDisplayNames;
        }
    }
}
