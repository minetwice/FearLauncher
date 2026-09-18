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
            if(name.contains("sodium") || name.contains("embeddium") || name.contains("rubidium")) return true;
        }
        return false;
    }

    private static boolean hasAngelica(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            if(file.getName().contains("angelica")) return true;
        }
        return false;
    }

    private static boolean affectedByRenderDistanceIssue(JMinecraftVersionList.Version version) throws ParseException {
        if(LauncherPreferences.PREF_USE_ANGLE) return false;
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        if(info.glesMajorVersion >= 3) return false;
        Date creationDate = DateUtils.parseReleaseDate(version.releaseTime);
        if(creationDate == null) return false;
        return !DateUtils.dateBefore(creationDate, 2021, 10, 1);
    }

    private static boolean isCompatContext(JMinecraftVersionList.Version version) throws Exception{
        Date creationDate = DateUtils.getOriginalReleaseDate(version);
        if(creationDate == null) return true;
        return DateUtils.dateBefore(creationDate, 2021, 6, 8);
    }

    private static boolean showDialog(AppCompatActivity activity, int message) throws InterruptedException {
        final boolean[] result = {false};
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w)-> result[0] = false)
                        .setNegativeButton(android.R.string.cancel, (d, w)-> result[0] = true);
        LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
        return result[0];
    }

    private static String switchLtw(boolean hasLtw, Instance instance, AppCompatActivity activity, int resId) throws InterruptedException, IOException {
        if(hasLtw) {
            String ltwRenderer = "opengles3_ltw";
            instance.renderer = ltwRenderer;
            instance.write();
            return ltwRenderer;
        }else {
            showDialog(activity, resId);
            System.exit(0);
            return null;
        }
    }

    public static void launchMinecraft(final AppCompatActivity activity, MinecraftAccount minecraftAccount,
                                       Instance instance, String versionId, File[] classpath, String rendererName) throws Throwable {
        int freeDeviceMemory = Tools.getFreeDeviceMemory(activity);
        int localeString;
        int freeAddressSpace = Architecture.is32BitsDevice() ? Tools.getMaxContinuousAddressSpaceSize() : -1;
        Log.i("MemStat", "Free RAM: " + freeDeviceMemory + " Addressable: " + freeAddressSpace);
        if(freeDeviceMemory > freeAddressSpace && freeAddressSpace != -1) {
            freeDeviceMemory = freeAddressSpace;
            localeString = R.string.address_memory_warning_msg;
        } else {
            localeString = R.string.memory_warning_msg;
        }

        if(LauncherPreferences.PREF_RAM_ALLOCATION > freeDeviceMemory) {
            int finalDeviceMemory = freeDeviceMemory;
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(activity.getString(localeString, finalDeviceMemory, LauncherPreferences.PREF_RAM_ALLOCATION))
                        .setPositiveButton(android.R.string.ok, (d, w)->{});
            if(LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) {
                return;
            }
        }
        File gamedir = instance.getGameDirectory();
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);

        if(isCompatContext(versionInfo) && !hasAngelica(gamedir) && rendererName.equals("opengles3_ltw")) {
            instance.renderer = rendererName = "opengles2";
            instance.write();
        }

        boolean isGl4es = rendererName.equals("opengles2");
        boolean ltwSupported = RendererCompatUtil.getCompatibleRenderers(activity).rendererIds.contains("opengles3_ltw");
        if(!isCompatContext(versionInfo) && isGl4es && hasSodium(gamedir)) {
            rendererName = switchLtw(ltwSupported, instance, activity, R.string.compat_sodium_not_supported);
        }

        int requiredJavaVersion = 8;
        if(versionInfo.javaVersion != null) requiredJavaVersion = versionInfo.javaVersion.majorVersion;
        Runtime runtime = MultiRTUtils.forceReread(pickRuntime(instance, requiredJavaVersion));

        List<String> launchArgs = getMinecraftClientArgs(minecraftAccount, versionInfo, gamedir);
        OldVersionsUtils.selectOpenGlVersion(versionInfo);

        ArrayList<String> launchClassPath = new ArrayList<>(classpath.length);
        for(File classpathEntry : classpath) {
            if(classpathEntry.exists()) launchClassPath.add(classpathEntry.getAbsolutePath());
        }

        List<String> javaArgList = new ArrayList<>();

        File configFile = new File(Tools.DIR_DATA + "/log4j-rce-patch.xml");
        if (configFile.exists()) {
            javaArgList.add("-Dlog4j.configurationFile=" + configFile);
        }

        String dirPath = runtime.path + "/lib";
        javaArgList.add("-Djava.library.path="+dirPath+":"+Tools.NATIVE_LIB_DIR);
        javaArgList.add("-Djna.boot.library.path="+dirPath);

        File lwjglExtractDir = new File(activity.getCacheDir(), "lwjgl3");
        FileUtils.ensureDirectorySilently(lwjglExtractDir);
        javaArgList.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="+lwjglExtractDir.getAbsolutePath());

        javaArgList.addAll(getMinecraftJVMArgs(versionId));
        javaArgList.addAll(JREUtils.parseJavaArguments(instance.getLaunchArgs()));

        JREUtils.setEnviroimentForGame(activity, rendererName);

        disableSplash(gamedir);

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if(rendererLibrary == null) {
            Log.i("GameRunner", "Falling back to GL4ES 1.1.4");
            rendererName = "opengles2";
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if(rendererLibrary == null) {
            if(showDialog(activity, R.string.gr_err_renderer_load_Failed)) return;
            System.exit(0);
        }

        String lwjglGlLib;
        if (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink")
                || rendererName.equals("panvk_zink") || rendererName.equals("fear_render")) {
            lwjglGlLib = "libmh_drive_vulkan_mesa.so";
        } else if (rendererName.equals("ng_gl4es") || rendererName.equals("krypton_wrapper")) {
            lwjglGlLib = Tools.NATIVE_LIB_DIR + "/libng_gl4es.so";
        } else if (rendererName.equals("opengles3_ltw")) {
            lwjglGlLib = "libltw.so";
        } else {
            lwjglGlLib = "libgl4es_114.so";
        }
        javaArgList.add("-Dorg.lwjgl.opengl.libname=" + lwjglGlLib);
        if (rendererName.equals("ng_gl4es") || rendererName.equals("opengles2") || rendererName.equals("opengles3_ltw")) {
            javaArgList.add("-Dorg.lwjgl.egl.libname=libEGL.so");
        }
        javaArgList.add("-Dorg.lwjgl.freetype.libname="+ Tools.NATIVE_LIB_DIR+"/libfreetype.so");
        javaArgList.add("-Dorg.lwjgl.util.NoChecks=true");
        javaArgList.add("-Dminecraft.narrator=false");
        javaArgList.add("-Djna.nosys=true");

        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        Log.i("GameRunner", "Running with "+ launchArgs.toString());
        try {
            net.kdt.pojavlaunch.Logger.appendToLog("[GameRunner] Starting JVM " + versionId + " renderer=" + rendererName);
        } catch (Throwable ignored) {}

        try {
            JavaRunner.nativeSetupExit(activity);
            JavaRunner.startJvm(runtime, javaArgList, launchClassPath, versionInfo.mainClass, launchArgs);
        }catch (VMLoadException e) {
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(e.toString(activity)).setPositiveButton(android.R.string.ok, (d, w)->{});
            if(LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) { return; }
        }
        Tools.fullyExit();
    }

    private static void disableSplash(File dir) {
        File configDir = new File(dir, "config");
        if(FileUtils.ensureDirectorySilently(configDir)) {
            File forgeSplashFile = new File(dir, "config/splash.properties");
            String forgeSplashContent = "enabled=true";
            try {
                if (forgeSplashFile.exists()) { forgeSplashContent = Tools.read(forgeSplashFile.getAbsolutePath()); }
                if (forgeSplashContent.contains("enabled=true")) {
                    Tools.write(forgeSplashFile, forgeSplashContent.replace("enabled=true", "enabled=false"));
                }
            } catch (IOException e) { Log.w(Tools.APP_NAME, "Could not disable Forge splash screen!", e); }
        }
    }

    public static List<String> getMinecraftClientArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo, File gameDir) {
        String userType = "mojang";
        try {
            Date creationDate = DateUtils.parseReleaseDate(versionInfo.releaseTime);
            if (creationDate != null && !DateUtils.dateBefore(creationDate, 2022, 9, 26)) userType = "msa";
        } catch (ParseException e) { Log.e("GameRunner", "date", e); }

        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("auth_session", profile.accessToken);
        varArgMap.put("auth_access_token", profile.accessToken);
        varArgMap.put("auth_player_name", profile.username);
        varArgMap.put("auth_uuid", profile.profileId.replace("-", ""));
        varArgMap.put("auth_xuid", profile.xuid);
        varArgMap.put("assets_root", Tools.ASSETS_PATH);
        varArgMap.put("assets_index_name", versionInfo.assets);
        varArgMap.put("game_assets", Tools.ASSETS_PATH);
        varArgMap.put("game_directory", gameDir.getAbsolutePath());
        varArgMap.put("user_properties", "{}");
        varArgMap.put("user_type", userType);
        varArgMap.put("version_name", versionInfo.id);
        varArgMap.put("version_type", versionInfo.type);

        List<String> minecraftArgs = new ArrayList<>();
        if (versionInfo.arguments != null && versionInfo.arguments.game != null) {
            for (Object arg : versionInfo.arguments.game) {
                if (arg instanceof String) minecraftArgs.add((String) arg);
            }
        }
        if(versionInfo.minecraftArguments != null) {
            for (String arg : versionInfo.minecraftArguments.split(" ")) {
                if (!arg.isEmpty()) minecraftArgs.add(arg);
            }
        }
        return JSONUtils.insertJSONValueList(minecraftArgs, varArgMap);
    }

    private static List<String> getMinecraftJVMArgs(String versionName) {
        return new ArrayList<>();
    }

    public static @NonNull String pickRuntime(Instance instance, int targetJavaVersion) {
        String runtime = Tools.getSelectedRuntime(instance);
        String profileRuntime = instance.selectedRuntime;
        Runtime pickedRuntime = MultiRTUtils.read(runtime);
        if(runtime == null || pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion) {
            String preferredRuntime = MultiRTUtils.getNearestJreName(targetJavaVersion);
            if(preferredRuntime == null) throw new RuntimeException("Failed to autopick runtime!");
            if(profileRuntime != null) { instance.selectedRuntime = preferredRuntime; instance.maybeWrite(); }
            runtime = preferredRuntime;
        }
        return runtime;
    }
}
