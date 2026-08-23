package net.kdt.pojavlaunch.plugins;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import android.content.pm.ApplicationInfo;
import java.util.ArrayList;
import java.util.List;

public class LibraryPlugin {
    private static final String TAG = "LibraryPlugin";

    // Known plugins constants
    public static final String ID_ANGLE_PLUGIN = "git.fear.angle";
    public static final String ID_FFMPEG_PLUGIN = "git.fear.ffmpeg";

    // Mobile Glue & Common Custom Renderer Package Prefixes / Identifiers
    public static final String[] MOBILE_GLUE_PACKAGES = new String[]{
            "org.mobileglues",
            "org.mobileglues.plugin",
            "org.mobilegl.mobileglues",
            "com.mobileglues",
            "net.mobileglue",
            "com.fcl.renderer",
            "git.fear.renderer"
    };

    private final String appId;
    private final String libraryPath;
    private final String displayName;

    public LibraryPlugin(String app, String libraryPath, String displayName){
        this.appId = app;
        this.libraryPath = libraryPath;
        this.displayName = displayName;
    }

    public LibraryPlugin(String app, String libraryPath){
        this(app, libraryPath, app);
    }

    public static LibraryPlugin discoverPlugin(Context ctx, String appId){
        String libraryPath;
        try {
            PackageInfo pluginPackage = ctx.getPackageManager().getPackageInfo(appId, PackageManager.GET_SHARED_LIBRARY_FILES);
            if (pluginPackage != null && pluginPackage.applicationInfo != null) {
                libraryPath = pluginPackage.applicationInfo.nativeLibraryDir;
                CharSequence label = pluginPackage.applicationInfo.loadLabel(ctx.getPackageManager());
                String name = (label != null) ? label.toString() : appId;
                return new LibraryPlugin(appId, libraryPath, name);
            }
        } catch (Exception e){
            // Ignore
        }

        // Secondary attempt using getApplicationInfo
        try {
            ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(appId, PackageManager.GET_META_DATA);
            if (appInfo != null && appInfo.nativeLibraryDir != null) {
                libraryPath = appInfo.nativeLibraryDir;
                CharSequence label = appInfo.loadLabel(ctx.getPackageManager());
                String name = (label != null) ? label.toString() : appId;
                return new LibraryPlugin(appId, libraryPath, name);
            }
        } catch (Exception e) {
            Log.d(TAG, "Plugin discover failed for " + appId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Discover all installed custom renderer plugin packages (Mobile Glue, Zalith Plugins, FCL Plugins).
     */
    public static List<LibraryPlugin> discoverRendererPlugins(Context ctx) {
        List<LibraryPlugin> rendererPlugins = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();

        // 1. Direct discovery for Mobile Glue and known custom renderer packages
        for (String pkg : MOBILE_GLUE_PACKAGES) {
            LibraryPlugin plugin = discoverPlugin(ctx, pkg);
            if (plugin != null) {
                rendererPlugins.add(plugin);
            }
        }

        // 2. Dynamic package scan for any app declaring "mobileglue", "renderer", or "pojav.plugin"
        try {
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo appInfo : installedApps) {
                if (appInfo.packageName == null) continue;
                String pkgName = appInfo.packageName.toLowerCase();

                // Skip already added packages
                boolean alreadyAdded = false;
                for (LibraryPlugin existing : rendererPlugins) {
                    if (existing.getId().equalsIgnoreCase(appInfo.packageName)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (alreadyAdded) continue;

                if (pkgName.contains("mobileglue") || pkgName.contains("fclrenderer") || pkgName.contains("customrenderer")) {
                    String libDir = appInfo.nativeLibraryDir;
                    if (libDir != null && new File(libDir).exists()) {
                        CharSequence label = appInfo.loadLabel(pm);
                        String name = (label != null) ? label.toString() : appInfo.packageName;
                        rendererPlugins.add(new LibraryPlugin(appInfo.packageName, libDir, name));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning installed applications for custom renderers", e);
        }

        return rendererPlugins;
    }

    public String getId(){
        return appId;
    }

    public String getLibraryPath(){
        return libraryPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String resolveAbsolutePath(String library) {
        return new File(libraryPath, library).getAbsolutePath();
    }

    public boolean checkLibraries(String... libs){
        for(String lib : libs){
            if(!(new File(libraryPath, lib).exists())) return false;
        }
        return true;
    }
}
