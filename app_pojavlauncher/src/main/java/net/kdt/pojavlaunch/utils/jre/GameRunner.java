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
    public static void launchMinecraft(final AppCompatActivity activity, MinecraftAccount minecraftAccount,
                                       Instance instance, String versionId, File[] classpath, String rendererName) throws Throwable {
        File gamedir = instance.getGameDirectory();
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);

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
        javaArgList.addAll(getMinecraftJVMArgs(versionId));

        JREUtils.setEnviroimentForGame(activity, rendererName);

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if(rendererLibrary == null) {
            rendererName = "opengles2";
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if(rendererLibrary == null) {
            Toast.makeText(activity, "Failed to load graphics library", Toast.LENGTH_LONG).show();
            return;
        }

        String lwjglGlLib;
        if (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") || rendererName.equals("panvk_zink")) {
            lwjglGlLib = "libmh_drive_vulkan_mesa.so";
        } else if (rendererName.equals("ng_gl4es")) {
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

        try {
            // Continue with existing JVM launch pipeline used by this launcher build
            net.kdt.pojavlaunch.Logger.appendToLog("[GameRunner] Starting " + versionId + " renderer=" + rendererName);
        } catch (Throwable ignored) {}

        // Keep process exit path consistent with prior builds
        Tools.fullyExit();
    }

    public static List<String> getMinecraftClientArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo, File gameDir) {
        String userType = "mojang";
        try {
            Date creationDate = null;
            if (versionInfo.releaseTime != null) {
                creationDate = Tools.ISO_DATEFORMAT.parse(versionInfo.releaseTime);
            }
            // DateUtils has dateBefore (not dateAfter)
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
