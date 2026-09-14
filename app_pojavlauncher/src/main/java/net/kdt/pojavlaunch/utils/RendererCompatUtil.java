package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RendererCompatUtil {
    private static RenderersList sCompatibleRenderers;

    /**
     * Checks if the device has Vulkan support (required by Zink-based renderers).
     */
    public static boolean checkVulkanSupport(PackageManager packageManager) {
        try {
            if (packageManager != null
                    && packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            System.loadLibrary("vulkan");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Checks if the device can run the bundled Mesa-based drivers.
     */
    public static boolean checkDeviceCompatibleMesa() {
        return Architecture.is64BitsDevice();
    }

    /**
     * Checks if the device supports OpenGL ES 3.
     */
    public static boolean checkOpenGLES3Support() {
        return GLInfoUtils.getGlInfo().glesMajorVersion >= 3;
    }

    /**
     * Checks if a native library is present in the launcher's native library directory.
     */
    public static boolean checkLocalLibraryPresent(String libName) {
        return new File(Tools.NATIVE_LIB_DIR, libName).exists();
    }

    public static RenderersList getCompatibleRenderers(Context context) {
        if(sCompatibleRenderers != null) return sCompatibleRenderers;

        Resources resources = context.getResources();
        String[] defaultRenderers = resources.getStringArray(R.array.renderer_values);
        String[] defaultRendererNames = resources.getStringArray(R.array.renderer);

        boolean deviceHasVulkan = checkVulkanSupport(context.getPackageManager());
        boolean deviceCompatibleMesa = checkDeviceCompatibleMesa();
        boolean deviceHasOpenGLES3 = checkOpenGLES3Support();
        boolean appHasLtw = checkLocalLibraryPresent("libltw.so");

        List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
        List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
        for(int i = 0; i < defaultRenderers.length; i++) {
            String rendererId = defaultRenderers[i];
            if(rendererId.equals("turnip_zink") || rendererId.equals("panvk_zink")) {
                rendererIds.add(rendererId);
                rendererNames.add(defaultRendererNames[i]);
                continue;
            }
            if(rendererId.contains("vulkan") && !deviceHasVulkan) continue;
            if(rendererId.contains("zink") && !deviceCompatibleMesa) continue;
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
