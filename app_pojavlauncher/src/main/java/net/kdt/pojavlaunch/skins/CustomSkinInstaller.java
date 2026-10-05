package net.kdt.pojavlaunch.skins;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Undoes the CustomSkinLoader install.
 *
 * CustomSkinLoader was added to force the account's skin on any server, but on this mod
 * pack it conflicts with Entity Model Features: the game hung on the loading screen while
 * EMF threw a stream of "ArrayIndexOutOfBoundsException: Index -1 out of bounds for length
 * 513". So the launcher now removes it again - the mod jar and its config are deleted from
 * the instance, and the resource-pack based skin (which works in singleplayer) is left
 * untouched.
 */
public final class CustomSkinInstaller {

    private static final String TAG = "CustomSkinInstaller";

    private static final String MOD_FILE_NAME = "CustomSkinLoader_Universal-15.1.jar";

    private CustomSkinInstaller() {}

    /** Idempotent and safe to call on every launch. */
    public static void removeInstalled(Context context) {
        if (context == null) return;

        List<File> bases = new ArrayList<>();
        bases.add(new File(Tools.DIR_GAME_HOME));
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                File dir = instance.getGameDirectory();
                if (dir != null && !dir.equals(new File(Tools.DIR_GAME_HOME))) bases.add(dir);
            }
        } catch (Throwable ignored) { }

        for (File base : bases) {
            delete(new File(base, "mods/" + MOD_FILE_NAME));
            delete(new File(base, "CustomSkinLoader/CustomSkinLoader.json"));
            delete(new File(base, "CustomSkinLoader/ExtraList/craftynmc.json"));
            delete(new File(base, "CustomSkinLoader/Core/CustomSkinLoader-Common.jar"));
        }
    }

    private static void delete(File file) {
        try {
            if (file.isFile() && !file.delete()) {
                Log.w(TAG, "Could not delete " + file);
            }
        } catch (Throwable ignored) { }
    }
}
