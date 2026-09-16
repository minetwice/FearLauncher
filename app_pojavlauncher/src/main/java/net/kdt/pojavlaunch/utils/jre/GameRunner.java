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
        if(!info.isAdreno()) return false;
        if(info.glesMajorVersion != 3) return false;
        if(info.glesMinorVersion > 1) return false;
        Date versionTime = DateUtils.getOriginalReleaseDate(version);
        return DateUtils.dateBefore(versionTime, 2024, 3, 1);
    }

    private static boolean showDialog(AppCompatActivity activity, int message) throws InterruptedException {
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder)->
                builder.setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d,s)->{});
        return !LifecycleAwareAlertDialog.halt_onDialog(activity.getLifecycle(), activity, dialogCreator);
    }

    public static void launch(AppCompatActivity activity, MinecraftAccount account, Instance instance, JMinecraftVersionList.Version versionInfo, String versionName) throws Exception {
        File gameDir = instance.getGameDirectory();
        FileUtils.ensureDirectory(gameDir);

        String runtimeName = pickRuntime(instance, Tools.getGameEngineVersion(versionInfo));
        Runtime runtime = MultiRTUtils.read(runtimeName);

        String rendererName = RendererCompatUtil.getCompatibleRenderer(instance, versionInfo);

        List<String> launchArgs = getMinecraftArgs(account, versionInfo, gameDir, versionName);
        List<String> javaArgList = new ArrayList<>();

        File customArgsFile = new File(Tools.DIR_GAME_HOME, "config_vertype/jvm_args.txt");
        if (customArgsFile.exists()) {
            try {
                String content = Tools.read(customArgsFile.getAbsolutePath());
                if (content != null && !content.trim().isEmpty()) {
                    for (String line : content.split("\\n")) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            javaArgList.add(line);
                        }
                    }
                }
            } catch (IOException e) {
                Log.w("GameRunner", "Failed to read jvm_args.txt", e);
            }
        }

        javaArgList.addAll(JREUtils.parseJavaArguments(instance.getLaunchArgs()));

        JREUtils.setEnviroimentForGame(activity, rendererName);
        JREUtils.chdir(instance.getGameDirectory().getAbsolutePath());

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
        if (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink")) {
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

        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        Log.i("GameRunner", "Running with "+ launchArgs.toString());

        try {
            JavaRunner.nativeSetupExit(activity);
            String launchClassPath = Tools.generateLaunchClassPath(versionInfo, versionName);
            JavaRunner.startJvm(runtime, javaArgList, launchClassPath, versionInfo.mainClass, launchArgs);
        }catch (VMLoadException e) {
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(e.toString(activity)).setPositiveButton(android.R.string.ok, (d, w)->{});
            LifecycleAwareAlertDialog.halt_onDialog(activity.getLifecycle(), activity, dialogCreator);
        }
    }

    private static List<String> getMinecraftArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo, File gameDir, String versionName) {
        String username = profile.username;
        String userType = "mojang";
        try {
            Date creationDate = DateUtils.getOriginalReleaseDate(versionInfo);
            if (DateUtils.dateAfter(creationDate, 2022, 9, 26)) { userType = "msa"; }
        }catch (ParseException e) { Log.e("CheckForProfileKey", "Failed to determine profile creation date, using \"mojang\"", e); }

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
                if (arg instanceof String) { minecraftArgs.add((String) arg); }
            }
        }
        if(versionInfo.minecraftArguments != null){ minecraftArgs.addAll(splitAndFilterEmpty(versionInfo.minecraftArguments)); }
        return JSONUtils.insertJSONValueList(minecraftArgs, varArgMap);
    }

    private static List<String> splitAndFilterEmpty(String argStr) {
        List<String> strList = new ArrayList<>();
        for (String arg : argStr.split(" ")) { if (!arg.isEmpty()) { strList.add(arg); } }
        return strList;
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
