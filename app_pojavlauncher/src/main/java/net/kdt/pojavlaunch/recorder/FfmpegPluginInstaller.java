package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Gets FFmpeg onto the device when it is missing.
 *
 * FFmpeg cannot simply be dropped into the launcher's own storage and executed - since
 * Android 10 the platform refuses to run a binary from an app's writable data directory.
 * The one place a binary may be executed from is a native library directory, and that is
 * populated only from an installed APK. So "download FFmpeg" means "download the FFmpeg
 * plugin APK and let the user install it", which is also how the launcher already expects
 * to find it (LibraryPlugin.ID_FFMPEG_PLUGIN).
 *
 * Both the launcher's own plugin id and the upstream PojavLauncher plugin are accepted, so
 * whichever build the user has works.
 */
public final class FfmpegPluginInstaller {

    private static final String TAG = "FfmpegPluginInstaller";

    /** Upstream PojavLauncher FFmpeg plugin; ships libffmpeg.so in its native library dir. */
    public static final String PLUGIN_APK_URL =
            "https://github.com/PojavLauncherTeam/FFmpegPlugin/releases/download/v1.1/FFmpeg_plugin.apk";

    public interface Listener {
        void onProgress(int percent);
        void onReady(File apk);
        void onFailed(String message);
    }

    private FfmpegPluginInstaller() {}

    /** True when either the launcher's plugin or the upstream one is installed. */
    public static boolean isInstalled(Context context) {
        return FfmpegExporter.locate(context) != null;
    }

    /** Downloads the plugin APK into the cache, then reports where it landed. */
    public static void download(Context context, Listener listener) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                File target = new File(context.getExternalCacheDir(), "FFmpeg_plugin.apk");
                conn = (HttpURLConnection) new URL(PLUGIN_APK_URL).openConnection();
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(120_000);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() != 200) {
                    listener.onFailed("Download failed (HTTP " + conn.getResponseCode() + ")");
                    return;
                }
                int total = conn.getContentLength();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) listener.onProgress((int) (done * 100 / total));
                    }
                }
                listener.onReady(target);
            } catch (Exception e) {
                Log.e(TAG, "Plugin download failed", e);
                listener.onFailed(e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "fear-ffmpeg-dl").start();
    }

    /**
     * Fires the system package installer at a downloaded APK. On Android 8+ the user must
     * first allow this app to install unknown apps, so that settings screen is opened when
     * the permission has not been granted yet.
     */
    public static void install(Context context, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not launch the installer", e);
        }
    }
}
