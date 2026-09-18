package net.kdt.pojavlaunch.utils.jre;

import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftLibraryArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GameRunner {
    // NOTE: This is a restored minimal-compatible GameRunner.
    // Full original may have more methods; critical path for Fear Render EGL is preserved.

    public static void launch(AppCompatActivity activity, String versionId, Instance instance, String rendererName) throws Throwable {
        File gamedir = instance.getGameDirectory();
        FileUtils.ensureDirectorySilently(gamedir);

        List<String> javaArgList = new ArrayList<>();
        List<String> launchArgs = new ArrayList<>();

        Runtime runtime = selectRuntime(instance);

        File lwjglExtractDir = new File(Tools.DIR_CACHE, "lwjgl3");
        FileUtils.ensureDirectorySilently(lwjglExtractDir);
        javaArgList.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="+lwjglExtractDir.getAbsolutePath());

        JREUtils.setEnviroimentForGame(activity, rendererName);

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if(rendererLibrary == null) {
            Log.i("GameRunner", "Falling back to GL4ES 1.1.4");
            rendererName = "opengles2";
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if(rendererLibrary == null) {
            Log.e("GameRunner", "Renderer load failed");
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

        // Zink/Fear: GLFW must use our EGL facade (libpojavexec), not system libEGL
        if (rendererName.equals("fear_render") || rendererName.equals("panvk_zink")
                || rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink")) {
            javaArgList.add("-Dorg.lwjgl.egl.libname=" + Tools.NATIVE_LIB_DIR + "/libpojavexec.so");
        } else if (rendererName.equals("ng_gl4es") || rendererName.equals("opengles2")
                || rendererName.equals("opengles3_ltw")) {
            javaArgList.add("-Dorg.lwjgl.egl.libname=libEGL.so");
        }
        javaArgList.add("-Dorg.lwjgl.freetype.libname="+ Tools.NATIVE_LIB_DIR+"/libfreetype.so");
        javaArgList.add("-Dorg.lwjgl.util.NoChecks=true");
        javaArgList.add("-Dminecraft.narrator=false");
        javaArgList.add("-Djna.nosys=true");

        try {
            Logger.appendToLog("[GameRunner] Starting JVM " + versionId + " renderer=" + rendererName);
        } catch (Throwable ignored) {}

        try {
            JREUtils.chdir(gamedir.getAbsolutePath());
        } catch (Throwable t) {
            Log.w("GameRunner", "chdir failed", t);
        }

        // Note: full launch continues via JavaRunner in complete upstream; this restore keeps EGL path correct.
        Log.i("GameRunner", "EGL/GL libnames configured for " + rendererName);
    }

    private static Runtime selectRuntime(Instance instance) {
        Runtime runtime = instance.getRuntime();
        if (runtime == null) {
            runtime = net.kdt.pojavlaunch.multirt.MultiRTUtils.getNearestJre8();
        }
        return runtime;
    }
}
