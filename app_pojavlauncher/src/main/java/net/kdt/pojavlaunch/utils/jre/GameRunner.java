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
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.GameOptionsUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;
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

    /**
     * FEARPATCH render engine: pick a renderer this GPU can actually run.
     * Turnip / Vulkan-Zink are Adreno-only, so on Mali (and any other
     * non-Adreno GPU) requesting them can only ever fail. In that case use the
     * Mali Zink path instead. LTW and the other renderers are left alone.
     */
    private static String pickRendererForDevice(String requested) {
        try {
            GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
            if (!info.isAdreno() && (requested == null
                    || requested.equals("turnip_zink")
                    || requested.equals("vulkan_zink"))) {
                Log.i("GameRunner", "FEARPATCH: non-Adreno GPU -> using holy_zink_kopper instead of " + requested);
                return "holy_zink_kopper";
            }
        } catch (Throwable t) {
            Log.w("GameRunner", "FEARPATCH: GPU detect failed, keeping " + requested, t);
        }
        return requested;
    }

    private static boolean affectedByRenderDistanceIssue(JMinecraftVersionList.Version version) throws ParseException {
        if(LauncherPreferences.PREF_USE_ANGLE) return false;
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        return info.isAdreno() && info.glesMajorVersion >= 3 &&
                DateUtils.dateBefore(DateUtils.getOriginalReleaseDate(version), 2025, 2, 25);
    }

    private static boolean checkRenderDistance(JMinecraftVersionList.Version version, File gamedir) throws ParseException {
        if(!affectedByRenderDistanceIssue(version)) return false;
        if(hasSodium(gamedir)) return false;
        try { MCOptionUtils.load(); }catch (Exception e) { Log.e("Tools", "Failed to load config", e); }
        int renderDistance = GameOptionsUtils.parseIntDefault(MCOptionUtils.get("renderDistance"),12);
        return renderDistance > 7;
    }

    private static boolean isCompatContext(JMinecraftVersionList.Version version) throws Exception{
        return DateUtils.dateBefore(DateUtils.getOriginalReleaseDate(version), 2021, 3, 9);
    }

    private static boolean showDialog(AppCompatActivity activity, int message) throws InterruptedException {
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = ((alertDialog, dialogBuilder) ->
                dialogBuilder.setMessage(activity.getString(message))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, w)->{}));
        return LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
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
            // FEARPATCH: on a non-Adreno GPU this must not become Turnip.
            instance.renderer = rendererName = pickRendererForDevice("turnip_zink");
            instance.write();
        }

        RendererCompatUtil.releaseRenderersCache();

        boolean isLtw = rendererName.equals("opengles3_ltw") || rendererName.equals("turnip_zink") || rendererName.equals("panvk_zink");

        if(isLtw && checkRenderDistance(versionInfo, gamedir)) {
            if(showDialog(activity, R.string.ltw_render_distance_warning_msg)) return;
            try {
                MCOptionUtils.set("renderDistance", "7");
                MCOptionUtils.save();
            }catch (Exception e) {
                Log.e("Tools", "Failed to fix render distance setting", e);
            }
        }

        GameOptionsUtils.fixOptions(isLtw);

        if(isLtw && GLInfoUtils.getGlInfo().forcedMsaa) {
            if(showDialog(activity, R.string.ltw_4x_msaa_warning_msg)) return;
        }

        int requiredJavaVersion = 8;
        if(versionInfo.javaVersion != null) requiredJavaVersion = versionInfo.javaVersion.majorVersion;

        Runtime runtime = MultiRTUtils.forceReread(pickRuntime(instance, requiredJavaVersion));

        disableSplash(gamedir);

        List<String> launchArgs = getMinecraftClientArgs(minecraftAccount, versionInfo, gamedir);
        OldVersionsUtils.selectOpenGlVersion(versionInfo);

        ArrayList<String> launchClassPath = new ArrayList<>(classpath.length);
        for(File classpathEntry : classpath) {
            String entryPath = classpathEntry.getAbsolutePath();
            if(!classpathEntry.exists()) { Log.w("GameRunner", "Skipped classpath entry " + entryPath + " because it is missing"); }
            launchClassPath.add(entryPath);
        }
        launchClassPath.trimToSize();

        List<String> javaArgList = new ArrayList<>();

        if (versionInfo.logging != null && versionInfo.logging.client != null && versionInfo.logging.client.file != null) {
            String configFile = Tools.DIR_DATA + "/security/" + versionInfo.logging.client.file.id.replace("client", "log4j-rce-patch");
            if (!new File(configFile).exists()) { configFile = Tools.DIR_GAME_NEW + "/" + versionInfo.logging.client.file.id; }
            javaArgList.add("-Dlog4j.configurationFile=" + configFile);
        }

        File versionSpecificNativesDir = new File(Tools.DIR_CACHE, "natives/"+versionId);
        if(versionSpecificNativesDir.exists()) {
            String dirPath = versionSpecificNativesDir.getAbsolutePath();
            javaArgList.add("-Djava.library.path="+dirPath+":"+Tools.NATIVE_LIB_DIR);
            javaArgList.add("-Djna.boot.library.path="+dirPath);
        }

        File lwjglExtractDir = new File(Tools.DIR_CACHE, "lwjgl_native/"+versionId);
        FileUtils.ensureDirectory(lwjglExtractDir);
        javaArgList.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="+lwjglExtractDir.getAbsolutePath());

        addAuthlibInjectorArgs(javaArgList, minecraftAccount, activity);
        javaArgList.addAll(getMinecraftJVMArgs(versionId));
        javaArgList.addAll(JREUtils.parseJavaArguments(instance.getLaunchArgs()));

        JREUtils.setEnviroimentForGame(activity, rendererName);
        JREUtils.chdir(instance.getGameDirectory().getAbsolutePath());

        String rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        if(rendererLibrary == null) {
            // FEARPATCH: fall back to a renderer this GPU can actually run,
            // not blindly to Turnip (which is Adreno-only).
            String fallback = pickRendererForDevice("turnip_zink");
            Log.i("GameRunner", "Renderer failed to load, falling back to " + fallback);
            rendererName = fallback;
            rendererLibrary = JREUtils.loadGraphicsLibrary(rendererName);
        }
        if(rendererLibrary == null) {
            if(showDialog(activity, R.string.gr_err_renderer_load_Failed)) return;
            System.exit(0);
        }
        // FEARPATCH: LWJGL must be pointed at the library that actually exists.
        // For LTW that is libltw.so (the LTW wrapper), not a non-existent
        // libGL.so - the old hardcode made the game die in GL.create() with
        // "Failed to locate library: libGL.so".
        boolean isZinkRenderer = rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink")
                || rendererName.equals("holy_zink_kopper") || rendererName.equals("panvk_zink");
        javaArgList.add("-Dorg.lwjgl.opengl.libname=" + (isZinkRenderer ? "libmh_drive_vulkan_mesa.so" : rendererLibrary));
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
            } catch (IOException e) { Log.w(Tools.APP_NAME, "Could not disable Forge 1.12.2 and below splash screen!", e); }
        } else { Log.w(Tools.APP_NAME, "Failed to create the configuration directory"); }
    }

    private static void addAuthlibInjectorArgs(List<String> javaArgList, MinecraftAccount minecraftAccount, android.content.Context context) {
        boolean useLocalServer = (minecraftAccount.authType == net.kdt.pojavlaunch.authenticator.AuthType.LOCAL) ||
                                 (minecraftAccount.authType == net.kdt.pojavlaunch.authenticator.AuthType.CRAFTYN_MC);
        if (useLocalServer) {
            File injectorJar = new File(Tools.DIR_DATA, "authlib-injector/authlib-injector.jar");
            if (!injectorJar.exists()) {
                try {
                    injectorJar.getParentFile().mkdirs();
                    try (java.io.InputStream in = context.getAssets().open("components/authlib-injector/authlib-injector.jar");
                         java.io.OutputStream out = new java.io.FileOutputStream(injectorJar)) {
                        byte[] buffer = new byte[1024]; int read;
                        while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
                    }
                    Log.i("LocalSkinServer", "Successfully extracted authlib-injector.jar on-demand from assets.");
                } catch (Exception e) { Log.e("LocalSkinServer", "Failed to extract authlib-injector.jar on-demand.", e); }
            }
            if (injectorJar.exists()) {
                try {
                    // Resolve the skin and rewrite the pack first, so the server below
                    // is handed a path that is known to exist.
                    net.kdt.pojavlaunch.skins.FearSkinSync.prepareForLaunch(context, minecraftAccount);
                    net.kdt.pojavlaunch.skins.LocalSkinServer.getInstance().start(context, minecraftAccount);
                    javaArgList.add("-javaagent:" + injectorJar.getAbsolutePath() + "=http://127.0.0.1:25599/");
                    // Fetched here rather than left to the injector, so the game start does
                    // not depend on a request succeeding.
                    String prefetched = net.kdt.pojavlaunch.skins.LocalSkinServer
                            .getInstance().getPrefetchedMetadata();
                    if (prefetched != null) {
                        javaArgList.add("-Dauthlibinjector.yggdrasil.prefetched=" + prefetched);
                    }
                    Log.i("LocalSkinServer", "Successfully started and injected local skin server.");

                    // The launcher's own authlib transformer, alongside authlib-injector.
                    // It hands the profile names over to this server, which is what lets an
                    // offline-mode lookup be answered instead of refused.
                    File skinAgent = new File(Tools.DIR_DATA, "fear_skin_agent/fear-skin-agent.jar");
                    // Always re-extract: an earlier install left a copy behind, and
                    // extract-only-if-missing would keep running that stale jar.
                    {
                        try {
                            skinAgent.getParentFile().mkdirs();
                            try (java.io.InputStream in = context.getAssets().open(
                                        "components/fear-skin-agent/fear-skin-agent.jar");
                                 java.io.OutputStream out = new java.io.FileOutputStream(skinAgent)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                            }
                        } catch (Exception e) {
                            Log.e("FearSkinAgent", "Could not extract the skin agent", e);
                        }
                    }
                    if (skinAgent.exists()) {
                        javaArgList.add("-javaagent:" + skinAgent.getAbsolutePath());
                        // FEAR: expose the agent's classes to the game classloader. Fabric's
                        // KnotClassLoader refuses to load classes from a plain -javaagent jar
                        // ("can't load class com.fear.skin.agent.FearSkinBridge ... as it hasn't
                        // been exposed to the game"), so the injected note() call crashed the
                        // game the first time a profile was looked up. Listing the jar as a
                        // system library lets Knot resolve it from the parent class loader.
                        javaArgList.add("-Dfabric.systemLibraries=" + skinAgent.getAbsolutePath());
                        Log.i("FearSkinAgent", "Installed the launcher's authlib transformer.");
                    }
                } catch (Exception e) { Log.e("LocalSkinServer", "Error starting/injecting local skin server.", e); }
            } else { Log.w("LocalSkinServer", "authlib-injector.jar is missing; skipping local skin server injection."); }
            return;
        }
        String injectorUrl = minecraftAccount.authType.injectorUrl;
        if (injectorUrl == null) { return; }
        File injectorJar = new File(Tools.DIR_DATA, "authlib-injector/authlib-injector.jar");
        if (!injectorJar.exists()) {
            try {
                injectorJar.getParentFile().mkdirs();
                try (java.io.InputStream in = context.getAssets().open("components/authlib-injector/authlib-injector.jar");
                     java.io.OutputStream out = new java.io.FileOutputStream(injectorJar)) {
                    byte[] buffer = new byte[1024]; int read;
                    while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
                }
            } catch (Exception e) { Log.e("GameRunner", "Failed to extract authlib-injector", e); }
        }
        if (injectorJar.exists()) {
            javaArgList.add("-javaagent:" + injectorJar.getAbsolutePath() + "=" + injectorUrl);
        }
    }

    private static List<String> getMinecraftJVMArgs(String versionName) {
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionName, true);
        if (versionInfo.inheritsFrom == null || versionInfo.arguments == null || versionInfo.arguments.jvm == null) {
            return Collections.emptyList();
        }
        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("classpath_separator", ":");
        varArgMap.put("library_directory", Tools.DIR_HOME_LIBRARY);
        varArgMap.put("version_name", versionInfo.id);
        varArgMap.put("natives_directory", Tools.NATIVE_LIB_DIR);
        List<String> minecraftArgs = new ArrayList<>();
        if (versionInfo.arguments != null) {
            for (Object arg : versionInfo.arguments.jvm) {
                if (arg instanceof String) { minecraftArgs.add((String) arg); }
            }
        }
        return JSONUtils.insertJSONValueList(minecraftArgs, varArgMap);
    }

    private static List<String> getMinecraftClientArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo, File gameDir) {
        String username = profile.username;
        String versionName = versionInfo.id;
        if (versionInfo.inheritsFrom != null) { versionName = versionInfo.inheritsFrom; }
        String userType = "mojang";
        try {
            Date creationDate = DateUtils.getOriginalReleaseDate(versionInfo);
            if(creationDate != null && !DateUtils.dateBefore(creationDate, 2022, 9, 26)) { userType = "msa"; }
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
