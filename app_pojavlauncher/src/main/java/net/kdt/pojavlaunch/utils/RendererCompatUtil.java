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

 public static RenderersList getCompatibleRenderers(Context context) {
 if(sCompatibleRenderers != null) return sCompatibleRenderers;
 Resources resources = context.getResources();
 String[] defaultRenderers = resources.getStringArray(R.array.renderer_values);
 String[] defaultRendererNames = resources.getStringArray(R.array.renderer);
 boolean deviceHasVulkan = checkVulkanSupport(context.getPackageManager());
 boolean deviceCompatibleMesa = SDK_INT >= 29;
 boolean deviceHasOpenGLES3 = JREUtils.getDetectedVersion() >= 3;
 boolean appHasLtw = new File(Tools.NATIVE_LIB_DIR, "libltw.so").exists();
 List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
 List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
 for(int i = 0; i < defaultRenderers.length; i++) {
 String rendererId = defaultRenderers[i];
 if(rendererId.equals("fear_engine") || rendererId.equals("mh_drive") ||
    rendererId.equals("opengles3_mges") || rendererId.equals("opengles3_mggl") ||
    rendererId.equals("opengles3_nggl4es") || rendererId.equals("custom_inject")) {
 rendererIds.add(rendererId);
 rendererNames.add(defaultRendererNames[i]);
 continue;
 }
 if(rendererId.contains("vulkan") && !deviceHasVulkan) continue;
 if(rendererId.contains("zink") && !deviceCompatibleMesa) continue;
 if(rendererId.contains("freedreno") && (!(GLInfoUtils.getGlInfo().isAdreno()) || !deviceCompatibleMesa)) continue;
 if(rendererId.contains("ltw") && (!deviceHasOpenGLES3 || !appHasLtw)) continue;
 rendererIds.add(rendererId);
 rendererNames.add(defaultRendererNames[i]);
 }
 sCompatibleRenderers = new RenderersList(rendererIds,
 rendererNames.toArray(new String[0]));

 return sCompatibleRenderers;
 }

 public static boolean checkRendererCompatible(Context context, String rendererName) {
 return getCompatibleRenderers(context).rendererIds.contains(rendererName);
 }

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
