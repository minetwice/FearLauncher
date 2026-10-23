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

import com.fearlauncher.fear.R;

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
        // gl4es is the universal fallback. It turns desktop GL into OpenGL ES 2, which
        // every Android GPU can do, so it is what keeps a phone with no Vulkan and no
        // OpenGL ES 3 - the cheap end of the market this launcher is meant to run on -
        // from ending up with no selectable renderer at all.
        boolean appHasGl4es = new File(Tools.NATIVE_LIB_DIR, "libgl4es_114.so").exists();
        List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
        List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
        for(int i = 0; i < defaultRenderers.length; i++) {
            String rendererId = defaultRenderers[i];
            if(rendererId.equals("turnip_zink")) {
                rendererIds.add(rendererId);
                rendererNames.add(defaultRendererNames[i]);
                continue;
            }
            if(rendererId.equals("opengles2") && !appHasGl4es) continue;
            if(rendererId.contains("vulkan") && !deviceHasVulkan) continue;
            if(rendererId.contains("zink") && !deviceCompatibleMesa) continue;
            if(rendererId.contains("ltw") && (!deviceHasOpenGLES3 || !appHasLtw)) continue;
            // Fear Monster is only offered when an engine build exists FOR THE VERSION
            // THAT IS GOING TO BE LAUNCHED.
            //
            // Not "a build exists somewhere": the engine hooks Minecraft's own classes,
            // so it is built per version and a build for one version says nothing about
            // another. Offering the entry on the strength of any build let it be selected
            // for a version with no engine - and this path deliberately has no GL layer,
            // so the session then had no renderer at all and died on launch looking for
            // liblwjgl.so. That is a crash, not a renderer that merely does not work.
            if(rendererId.equals("fear_monster") && !engineCanServeCurrentVersion()) continue;
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

    /**
     * True when the engine has a build for the version that is about to be launched.
     *
     * The engine is built per Minecraft version, so this is the only honest question to
     * ask before offering it. When no instance is selected there is nothing more specific
     * to go on, and any installed build will do.
     */
    private static boolean engineCanServeCurrentVersion() {
        try {
            net.kdt.pojavlaunch.instances.Instance selected =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (selected != null && selected.versionId != null && !selected.versionId.isEmpty()) {
                return net.kdt.pojavlaunch.engine.FearMonster.buildFor(selected.versionId) != null;
            }
        } catch (Throwable ignored) { }
        return appHasAnyFearMonsterBuild();
    }

    /** True when at least one Fear Monster engine build is installed anywhere. */
    private static boolean appHasAnyFearMonsterBuild() {
        try {
            File root = new File(Tools.DIR_DATA, "fear_monster");
            File[] versions = root.listFiles();
            if (versions == null) return false;
            for (File v : versions) {
                if (!v.isDirectory()) continue;
                File[] jars = v.listFiles((d, name) -> name.endsWith(".jar"));
                if (jars != null && jars.length > 0) return true;
            }
        } catch (Throwable ignored) { }
        return false;
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
