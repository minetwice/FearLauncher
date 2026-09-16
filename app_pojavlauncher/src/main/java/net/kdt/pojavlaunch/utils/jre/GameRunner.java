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
import git.artdeell.mojo.jvm.JavaRunner;
import git.artdeell.mojo.jvm.VMLoadException;

public final class GameRunner {
    private GameRunner() {}

    public static void launch(@NonNull AppCompatActivity activity, @NonNull MinecraftAccount minecraftAccount, @NonNull Instance instance) throws IOException {
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(instance);
        File gameDir = instance.getGameFolder();

        Runtime runtime = pickRuntime(activity, instance, versionInfo);
        if (runtime == null) return;

        List<String> javaArgList = new ArrayList<>();
        List<String> launchArgs = new ArrayList<>();

        String launchClassPath = Tools.generateLaunchClassPath(versionInfo, instance);

        File configFile = new File(Tools.DIR_DATA, "config.txt");
        if(configFile.exists()) {
            String[] customArgs = Tools.read(configFile.getAbsolutePath()).trim().split(" ");
            Collections.addAll(javaArgList, customArgs);
        }

        if(instance.selectedRuntime != null && !instance.selectedRuntime.isEmpty()) {
            Runtime selectedRuntime = MultiRTUtils.forceReread(instance.selectedRuntime);
            if(selectedRuntime != null) {
                runtime = selectedRuntime;
            }
        }

        int javaVersion = runtime.javaVersion;
        String rendererName = LauncherPreferences.PREF_RENDERER;

        if (rendererName == null || rendererName.isEmpty()) {
            rendererName = "turnip_zink";
        }

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
        javaArgList.add("-Dorg.lwjgl.opengl.libname=" + (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so" : rendererName.equals("krypton_wrapper") ? "libNG-GL4ES.so" : "libGL.so"));
        javaArgList.add("-Dorg.lwjgl.freetype.libname="+ Tools.NATIVE_LIB_DIR+"/libfreetype.so");
        javaArgList.add("-Dorg.lwjgl.util.NoChecks=true");
        javaArgList.add("-Dminecraft.narrator=false");

        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        Log.i("GameRunner", "Running with "+ launchArgs.toString());

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

    private static boolean showDialog(AppCompatActivity activity, int message) {
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(message).setPositiveButton(android.R.string.ok, (d, w)->{});
        return LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
    }

    private static Runtime pickRuntime(AppCompatActivity activity, Instance instance, JMinecraftVersionList.Version versionInfo) {
        return MultiRTUtils.getNearestJre8();
    }
}
