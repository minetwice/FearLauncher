package net.kdt.pojavlaunch.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

/**
 * Fear Launcher keeps its instances, accounts and Minecraft files in a directory of its
 * own. On Android 11 and newer that directory is only writable once the launcher has
 * been granted file access - "All files access" in system settings - and when it has not
 * been, the launcher used to fail in ways that looked like the app being broken: the
 * version list never loaded, and account creation sat behind it.
 *
 * <p>This is the prompt that replaces that. Instead of a failure, the user gets a short
 * explanation and one button that opens the exact settings screen where the switch
 * lives, so granting it is a tap rather than a hunt through settings.
 */
public final class FearStoragePermission {

    private static final String TAG = "FearStoragePermission";

    private FearStoragePermission() {}

    /** True when the launcher can write its own directory. */
    public static boolean hasAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
            return true;
        } catch (Throwable t) {
            // If the check itself fails, do not block the launcher over it.
            return true;
        }
    }

    /**
     * Shows the prompt when file access is missing, and does nothing when it is present.
     * Never throws: a launcher that cannot show a dialog still has to start.
     */
    public static void ensure(final Context context) {
        if (hasAccess()) return;
        try {
            new AlertDialog.Builder(context)
                    .setTitle("File access needed")
                    .setMessage("Fear Launcher stores your instances, accounts and Minecraft "
                            + "files in a folder of its own, and Android is currently blocking "
                            + "that folder.\n\nTap Allow, then switch on file access for Fear "
                            + "Launcher on the screen that opens. Without it the launcher cannot "
                            + "create an instance or an account.")
                    .setPositiveButton("Allow", (dialog, which) -> openSettings(context))
                    .setNegativeButton("Later", null)
                    .show();
        } catch (Throwable t) {
            Log.w(TAG, "could not show the file access prompt", t);
        }
    }

    /** Opens the one screen where the switch actually is. */
    private static void openSettings(Context context) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 30) {
                intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            // Some OEM builds refuse the all-files screen; fall back to the app's own page,
            // which always exists and has the same switch on it.
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable t2) {
                Log.w(TAG, "could not open settings", t2);
            }
        }
    }
}
