package net.kdt.pojavlaunch.utils.jre;

import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.lifecycle.LifecycleAwareAlertDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.GameOptionsUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.OldVersionsUtils;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import git.artdeell.mojo.R;

public class GameRunner {
    private static boolean hasSodium(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName();
            if(name.contains("sodium") ||
                    name.contains("embeddium") ||
                    name.contains("rubidium")) return true;
        }
        return false;
    }

    private static boolean hasAngelica(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName();
            if(name.contains("angelica")) return true;
        }
        return false;
    }

    private static boolean affectedByRenderDistanceIssue(JMinecraftVersionList.Version version) throws ParseException {
        if(LauncherPreferences.PREF_USE_ANGLE) return false;
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        // Only the original Adreno 3xx are affected by the render distance issue
        if(info.vendor.equals("Qualcomm") && info.renderer.contains("Adreno (TM) 3")) {
            Date versionReleaseDate = DateUtils.getOriginalReleaseDate(version);
            return DateUtils.dateBefore(versionReleaseDate, 2019, 7, 1);
        }
        return false;
    }

    private static boolean checkRenderDistance(JMinecraftVersionList.Version versionInfo, File gameDir) {
        try {
            if(!affectedByRenderDistanceIssue(versionInfo)) return false;
        }catch (ParseException e) {
            Log.e("RenderDistance", "Failed to parse version date", e);
            return false;
        }
        int renderDistance = MCOptionUtils.getMcOptionInt("renderDistance");
        if(renderDistance == -1) return false;
        return renderDistance > 8;
    }

    private static String switchLtw(boolean ltwSupported, Instance instance, AppCompatActivity activity, int message) {
        if(ltwSupported) {
            if(showDialog(activity, message)) return null;
            String ltwRenderer = "opengles3_ltw";
            instance.renderer = ltwRenderer;
            instance.maybeWrite();
            return ltwRenderer;
        }else {
            if(showDialog(activity, R.string.compat_ltw_not_supported)) return null;
            return "opengles2";
        }
    }

    public static void launchGame(final AppCompatActivity activity, MinecraftAccount minecraftAccount,
                                       Instance instance, String versionId, File[] classpath, String rendererName) throws Throwable {
        int freeDeviceMemory = Tools.getFreeDeviceMemory(activity);
        int requiredFreeMemory = Tools.getRequiredFreeMemory(instance);
        if(freeDeviceMemory < requiredFreeMemory) {
            if(showDialog(activity, R.string.memory_warning_msg, freeDeviceMemory, requiredFreeMemory)) return;
        }

        File gamedir = instance.getGameDirectory();
        FileUtils.createDirectory(gamedir);

        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);
        if(versionInfo == null) {
            if(showDialog(activity, R.string.mcn_download_failed)) return;
            return;
        }

        if(isCompatContext(versionInfo) && !hasAngelica(gamedir) && rendererName.equals("opengles3_ltw")) {
            instance.renderer = rendererName = "opengles2";
            instance.maybeWrite();
        }

        boolean isGl4es = rendererName.equals("opengles2");
        boolean ltwSupported = RendererCompatUtil.getCompatibleRenderers(activity).rendererIds.contains("opengles3_ltw");
        if(hasSodium(gamedir) && isGl4es) {
            rendererName = switchLtw(ltwSupported, instance, activity, R.string.compat_sodium_not_supported);
            if(rendererName == null) return;
        }
        if(isGl4es && OldVersionsUtils.isOldVersion(versionInfo)) {
            rendererName = switchLtw(ltwSupported, instance, activity, R.string.compat_version_not_supported);
            if(rendererName == null) return;
        }
        RendererCompatUtil.releaseRenderersCache();

        boolean isLtw = rendererName.equals("opengles3_ltw") || rendererName.equals("turnip_zink") || rendererName.equals("panvk_zink");

        if(isLtw && checkRenderDistance(versionInfo, gamedir)) {
            if(showDialog(activity, R.string.ltw_render_distance_warning_msg)) return;
        }

        // Only generate options.txt if it doesn't exist or if we are using LTW
        GameOptionsUtils.generateOptions(gamedir, instance, versionInfo, isLtw);
        GameOptionsUtils.fixOptions(isLtw);

        if(isLtw && GLInfoUtils.getGlInfo().forcedMsaa) {
            MCOptionUtils.setMcOption("graphicsMode", "fast");
            MCOptionUtils.setMcOption("entityShadows", "false");
            MCOptionUtils.setMcOption("renderClouds", "false");
        }

        String username = minecraftAccount.username;
        String versionName = versionId;
        if(instance.versionNameOverride != null) versionName = instance.versionNameOverride;

        List<String> javaArgList = new ArrayList<>();

        // Java args
        javaArgList.add("-Xms" + instance.ramAllocationMin + "M");
        javaArgList.add("-Xmx" + instance.ramAllocation + "M");

        // Custom JVM args
        if(instance.jvmArgs != null && !instance.jvmArgs.trim().isEmpty()) {
            Collections.addAll(javaArgList, instance.jvmArgs.trim().split("\\s+"));
        }

        // Cacio
        if(Architecture.is32BitsDevice() || LauncherPreferences.PREF_FORCE_ENGLISH) {
            javaArgList.add("-Duser.language=en");
        }

        // Other default args
        javaArgList.add("-Djava.library.path=" + Tools.NATIVE_LIB_DIR);
        javaArgList.add("-Djna.boot.library.path=" + Tools.NATIVE_LIB_DIR);
        javaArgList.add("-Djna.nounpack=true");
        javaArgList.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + Tools.NATIVE_LIB_DIR);
        javaArgList.add("-Dio.netty.native.workdir=" + Tools.NATIVE_LIB_DIR);
        javaArgList.add("-Dminecraft.launcher.brand=" + activity.getString(R.string.app_short_name));
        javaArgList.add("-Dminecraft.launcher.version=" + Tools.getVersionName(activity));
        javaArgList.add("-Dlog4j2.formatMsgNoLookups=true");

        // Renderer-specific
        JREUtils.setEnviroimentForGame(activity, rendererName);

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if(rendererLibrary == null) {
            Log.e("GameRunner", "Failed to load graphics library for " + rendererName);
            rendererName = "opengles2";
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if(rendererLibrary == null) {
            if(showDialog(activity, R.string.gr_err_renderer_load_Failed)) return;
            System.exit(0);
        }
        javaArgList.add("-Dorg.lwjgl.opengl.libname=" + (rendererName.equals("turnip_zink") || rendererName.equals("panvk_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so" : "libGL.so"));
        javaArgList.add("-Dorg.lwjgl.freetype.libname="+ Tools.NATIVE_LIB_DIR+"/libfreetype.so");
        javaArgList.add("-Dorg.lwjgl.util.NoChecks=true");
        javaArgList.add("-Dminecraft.narrator=false");

        // Classpath
        StringBuilder classpathBuilder = new StringBuilder();
        for(File file : classpath) {
            if(classpathBuilder.length() > 0) classpathBuilder.append(":");
            classpathBuilder.append(file.getAbsolutePath());
        }
        javaArgList.add("-cp");
        javaArgList.add(classpathBuilder.toString());

        // Main class + game args
        javaArgList.add(versionInfo.mainClass);

        List<String> gameArgs = getMinecraftArgs(minecraftAccount, versionInfo, versionName, gamedir);
        javaArgList.addAll(gameArgs);

        // Custom game args
        if(instance.gameArgs != null && !instance.gameArgs.trim().isEmpty()) {
            Collections.addAll(javaArgList, instance.gameArgs.trim().split("\\s+"));
        }

        Log.i("GameRunner", "Launching with args: " + javaArgList);

        JREUtils.launchJavaVM(activity, instance, javaArgList, gamedir);
    }

    private static boolean isCompatContext(JMinecraftVersionList.Version versionInfo) {
        // Versions before 1.17 require a compatibility context on some devices
        try {
            Date releaseDate = DateUtils.getOriginalReleaseDate(versionInfo);
            return DateUtils.dateBefore(releaseDate, 2021, 6, 1);
        } catch (ParseException e) {
            return false;
        }
    }

    private static boolean showDialog(AppCompatActivity activity, int message, Object... formatArgs) {
        final boolean[] result = {false};
        activity.runOnUiThread(() -> {
            LifecycleAwareAlertDialog.Builder builder = new LifecycleAwareAlertDialog.Builder(activity);
            builder.setMessage(activity.getString(message, formatArgs));
            builder.setPositiveButton(android.R.string.ok, (d, w) -> result[0] = false);
            builder.setNegativeButton(android.R.string.cancel, (d, w) -> result[0] = true);
            builder.setCancelable(false);
            builder.show();
        });
        // Wait for dialog (simplified – actual implementation uses proper synchronization)
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        return result[0];
    }

    private static List<String> getMinecraftArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo,
                                                 String versionName, File gameDir) {
        String username = profile.username;
        String userType = "mojang";
        try {
            Date profileCreationDate = DateUtils.getOriginalReleaseDate(versionInfo);
            if(DateUtils.dateBefore(profileCreationDate, 2022, 9, 26)) {
                userType = "msa";
            }
        }catch (ParseException e) {
            Log.e("CheckForProfileKey", "Failed to determine profile creation date, using \"mojang\"", e);
        }

        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("auth_session", profile.accessToken);
        varArgMap.put("auth_access_token", profile.accessToken);
        varArgMap.put("auth_player_name", username);
        varArgMap.put("auth_uuid", profile.profileId.replace("-", ""));
        varArgMap.put("auth_xuid", profile.xuid);
        varArgMap.put("assets_root", Tools.ASSETS_PATH);
        varArgMap.put("assets_index_name", versionInfo.assets);
        varArgMap.put("game_assets", Tools.ASSETS_PATH);
        varArgMap.put("game_directory", gameDir.getAbsolutePath());
        varArgMap.put("user_properties", "{}");
        varArgMap.put("user_type", userType);
        varArgMap.put("version_name", versionName);
        varArgMap.put("version_type", versionInfo.type);

        List<String> minecraftArgs = new ArrayList<>();
        if (versionInfo.arguments != null && versionInfo.arguments.game != null) {
            for (Object arg : versionInfo.arguments.game) {
                if (arg instanceof String) {
                    minecraftArgs.add((String) arg);
                }
            }
        }
        if(versionInfo.minecraftArguments != null){
            minecraftArgs.addAll(splitAndFilterEmpty(versionInfo.minecraftArguments));
        }
        return JSONUtils.insertJSONValueList(minecraftArgs, varArgMap);
    }

    private static List<String> splitAndFilterEmpty(String argStr) {
        List<String> strList = new ArrayList<>();
        for (String arg : argStr.split(" ")) {
            if (!arg.isEmpty()) {
                strList.add(arg);
            }
        }
        return strList;
    }

    public static @NonNull String pickRuntime(Instance instance, int targetJavaVersion) {
        String runtime = Tools.getSelectedRuntime(instance);
        String profileRuntime = instance.selectedRuntime;
        Runtime pickedRuntime = MultiRTUtils.read(runtime);
        if(runtime == null || pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion) {
            String preferredRuntime = MultiRTUtils.getNearestJreName(targetJavaVersion);
            if(preferredRuntime == null) throw new RuntimeException("Failed to autopick runtime!");
            if(profileRuntime != null) {
                instance.selectedRuntime = preferredRuntime;
                instance.maybeWrite();
            }
            runtime = preferredRuntime;
        }
        return runtime;
    }
}
