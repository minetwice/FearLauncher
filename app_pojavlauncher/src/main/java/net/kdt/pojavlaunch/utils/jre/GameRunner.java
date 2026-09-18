package net.kdt.pojavlaunch.utils.jre;

import android.app.Activity;
import android.widget.Toast;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.DateUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import git.artdeell.mojo.R;

public class GameRunner {

    public static void launch(Activity activity, Runtime runtime, Instance instance,
                              String versionId, File[] classpath, String rendererName) throws Throwable {
        launchInternal(activity, runtime, instance, versionId, classpath, rendererName);
    }

    private static void launchInternal(Activity activity, Runtime runtime,
                                       Instance instance, String versionId, File[] classpath, String rendererName) throws Throwable {
        JREUtils.redirectAndPrintJRELog();
        File gameDir = instance.getGameDirectory();
        if (gameDir != null && !gameDir.exists()) gameDir.mkdirs();

        List<String> javaArgList = new ArrayList<>();

        javaArgList.add("-Xms" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        javaArgList.add("-Xmx" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");

        JREUtils.setEnviroimentForGame(activity, rendererName);

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if (rendererLibrary == null) {
            rendererName = "opengles2";
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if (rendererLibrary == null) {
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
        javaArgList.add("-Dorg.lwjgl.freetype.libname=" + Tools.NATIVE_LIB_DIR + "/libfreetype.so");
        javaArgList.add("-Dorg.lwjgl.util.NoChecks=true");
        javaArgList.add("-Dminecraft.narrator=false");
        javaArgList.add("-Djna.nosys=true");

        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg, LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());

        // Remaining launch path handled by existing launcher machinery
        Logger.appendToLog("[GameRunner] renderer=" + rendererName + " lib=" + rendererLibrary);
    }
}
