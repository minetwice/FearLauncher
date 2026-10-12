package net.kdt.pojavlaunch.utils;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.fearlauncher.fear.R;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The automatic way off the LTW renderer when its bundled GLSL translator cannot convert a
 * core shader.
 *
 * <p>The failure is a renderer + resource-pack property: a pack that replaces core shaders
 * selects a shader that references the vanilla 1.21.2+ {@code linear_fog}, which LTW's wrapper
 * (LTWShdrWp) cannot translate. After it logs "failed to convert ... skipping" the wrapper
 * leaves that shader with no source, and the NULL reaches {@code strlen} at link time - the
 * SIGSEGV in {@code __strlen_aarch64} that kills the process. Nothing in the launcher can fix
 * the prebuilt {@code libltw.so}, so the way past it is a renderer that does not use the
 * wrapper at all.</p>
 *
 * <p>In practice the user never performs the change by hand (their options keep reverting and
 * they stay on LTW), so this class does it for them: when the last session's log shows the
 * failure and the instance is still on LTW, the renderer preference is moved to a renderer
 * that does not use the wrapper, and a dismissible notice explains what changed and how to
 * undo it. The move is applied at launcher start (never mid-session), so it takes effect on
 * the NEXT game launch.</p>
 *
 * <p>Only one switch happens per "stuck in LTW" episode. A failure stamp - the newest
 * modification time among the log/report files that carry the signature - is remembered; the
 * switch is re-applied only when a newer failure appears, which is exactly "the user hit the
 * crash again". If the user switches back to LTW by hand, that is respected: the same stamp
 * no longer triggers, so the launcher does not fight them.</p>
 */
public final class LtwShaderFallback {

    /** The renderer whose shader wrapper cannot convert the shader. */
    public static final String LTW_RENDERER = "opengles3_ltw";
    /** The default escape hatch: Mesa turnip. Adreno-only. */
    public static final String MESA_TURNIP = "turnip_zink";
    /** The escape hatch for a non-Adreno GPU (Mali and everything else): Quasar. */
    public static final String QUASAR = "holy_zink_kopper";

    /** The renderer the instance was moved away from (non-empty = an auto-switch is live). */
    private static final String KEY_FROM = "fear_ltw_fallback_from";
    /** The renderer the instance was moved to. */
    private static final String KEY_TO = "fear_ltw_fallback_to";
    /** The failure stamp this switch was made for (the one-episode marker). */
    private static final String KEY_STAMP = "fear_ltw_fallback_stamp";
    /** True while the dismissible notice still has to be shown. */
    private static final String KEY_NOTICE = "fear_ltw_fallback_notice";
    /** The resource packs seen at the time of the switch, for the notice. */
    private static final String KEY_PACKS = "fear_ltw_fallback_packs";

    private LtwShaderFallback() {}

    /** What {@link #applyIfNeeded} found, for the caller to act on (on the UI thread). */
    public static final class Result {
        /** True when this call performed the automatic switch. */
        public boolean switched;
        /** True when a notice should be shown to the user. */
        public boolean noticePending;
        /** The renderer the instance was moved away from. */
        public String from;
        /** The renderer the instance was moved to. */
        public String to;
        /** The resource packs seen at the time of the switch. Never null. */
        public List<String> packs = new ArrayList<>();
    }

    /**
     * Runs the whole check and, when warranted, performs the automatic switch. Blocking file
     * I/O and a SharedPreferences commit, so call it off the main thread (see
     * {@link #applyInBackground}). Never throws.
     */
    public static Result applyIfNeeded(Context context) {
        Result result = new Result();
        if (context == null) return result;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Instance instance = Instances.loadSelectedInstance();
            File gameDir = instance != null ? instance.getGameDirectory() : null;

            String current = instance != null && instance.renderer != null && !instance.renderer.isEmpty()
                    ? instance.renderer
                    : LauncherPreferences.PREF_RENDERER;

            // Only LTW uses the failing shader wrapper; anything else is already safe.
            if (!LTW_RENDERER.equals(current)) return result;

            // "Recorded for a session" = the last session's log or lag report carries the
            // signature. The in-memory flag is gone once the process died, so read the file.
            if (!LagWatch.logMentionsLtwShaderFailure(gameDir)) return result;

            long stamp = LagWatch.ltwShaderFailureStamp(gameDir);
            long handled = prefs.getLong(KEY_STAMP, 0L);
            if (stamp <= handled) {
                // The exact failure already handled once - respect a manual switch back to LTW.
                return result;
            }

            String target = safeTarget();
            List<String> packs = LagWatch.enabledResourcePacks(gameDir);

            // Persist the record first, so it survives even if the renderer write below fails.
            prefs.edit()
                    .putString(KEY_FROM, LTW_RENDERER)
                    .putString(KEY_TO, target)
                    .putLong(KEY_STAMP, stamp)
                    .putBoolean(KEY_NOTICE, true)
                    .putString(KEY_PACKS, TextUtils.join(", ", packs))
                    .commit();

            writeRenderer(instance, target);

            result.switched = true;
            result.noticePending = true;
            result.from = LTW_RENDERER;
            result.to = target;
            result.packs = packs;
            return result;
        } catch (Throwable t) {
            return result;
        }
    }

    /**
     * Runs {@link #applyIfNeeded} on a background thread and, when a switch happened or a
     * notice is still owed, shows the dismissible dialog on the UI thread. Safe to call from
     * an Activity's onCreate; never throws.
     */
    public static void applyInBackground(final Activity activity) {
        if (activity == null) return;
        final Context app = activity.getApplicationContext();
        Thread thread = new Thread(() -> {
            final Result result = applyIfNeeded(app);
            final boolean show = result.switched || noticePending(app);
            if (!show) return;
            activity.runOnUiThread(() -> showNoticeDialog(activity));
        }, "fear-ltw-fallback");
        thread.setDaemon(true);
        thread.start();
    }

    /** Shows the one-time, dismissible notice about the switch. */
    private static void showNoticeDialog(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            String from = prefs.getString(KEY_FROM, LTW_RENDERER);
            String to = prefs.getString(KEY_TO, MESA_TURNIP);
            String packs = prefs.getString(KEY_PACKS, "");
            String fromLabel = displayName(activity, from);
            String toLabel = displayName(activity, to);
            if (packs == null || packs.isEmpty()) packs = activity.getString(R.string.ltw_fallback_packs_none);

            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.ltw_fallback_dialog_title_fmt, toLabel))
                    .setMessage(activity.getString(R.string.ltw_fallback_dialog_msg,
                            fromLabel, toLabel, packs))
                    .setPositiveButton(activity.getString(R.string.ltw_fallback_keep_fmt, toLabel),
                            (d, w) -> dismissNotice(activity))
                    .setNegativeButton(activity.getString(R.string.ltw_fallback_undo_fmt, fromLabel),
                            (d, w) -> undo(activity))
                    .setCancelable(true)
                    .show();
        } catch (Throwable ignored) { }
    }

    /** True while a notice still has to be shown. */
    public static boolean noticePending(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(KEY_NOTICE, false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Forgets the pending notice without changing the renderer (the "keep" choice). */
    public static void dismissNotice(Context context) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(KEY_NOTICE, false).apply();
        } catch (Throwable ignored) { }
    }

    /** The renderer an auto-switch moved the instance away from, or "" when none is live. */
    public static String activeFrom(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_FROM, "");
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    /** The renderer an auto-switch moved the instance to, or "" when none is live. */
    public static String activeTo(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_TO, "");
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * True when an auto-switch is live: it moved the instance away from a renderer and the
     * instance is still on the renderer it was moved to. If the user has since picked another
     * renderer by hand, this is false, so the dashboard row hides itself.
     */
    public static boolean isActive(Context context) {
        try {
            String from = activeFrom(context);
            String to = activeTo(context);
            if (from.isEmpty() || to.isEmpty()) return false;
            Instance instance = Instances.loadSelectedInstance();
            String current = instance != null && instance.renderer != null && !instance.renderer.isEmpty()
                    ? instance.renderer
                    : LauncherPreferences.PREF_RENDERER;
            return to.equals(current);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Puts the instance back on the renderer it was moved away from and clears the auto-switch
     * state, so the dashboard row disappears. Returns the renderer it went back to ("" when
     * there was nothing to undo). Applies on the next launch.
     */
    public static String undo(Context context) {
        String from = "";
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            from = prefs.getString(KEY_FROM, "");
            if (from == null || from.isEmpty()) return "";
            Instance instance = Instances.loadSelectedInstance();
            writeRenderer(instance, from);
            prefs.edit()
                    .remove(KEY_FROM)
                    .remove(KEY_TO)
                    .remove(KEY_PACKS)
                    .putBoolean(KEY_NOTICE, false)
                    .apply();
            return from;
        } catch (Throwable t) {
            return from == null ? "" : from;
        }
    }

    /** The renderer to move to: Mesa turnip, or Quasar when the GPU cannot run turnip. */
    public static String safeTarget() {
        try {
            if (!GLInfoUtils.getGlInfo().isAdreno()) return QUASAR;
        } catch (Throwable ignored) { }
        return MESA_TURNIP;
    }

    /**
     * The renderer's label exactly as the renderer picker shows it, resolved from the same
     * {@code renderer} / {@code renderer_values} arrays the picker uses. Falls back to the raw
     * id when the arrays cannot be read. Never throws.
     */
    public static String displayName(Context context, String rendererValue) {
        if (rendererValue == null) return "";
        try {
            String[] values = context.getResources().getStringArray(R.array.renderer_values);
            String[] names = context.getResources().getStringArray(R.array.renderer);
            for (int i = 0; i < values.length && i < names.length; i++) {
                if (rendererValue.equals(values[i])) return names[i];
            }
        } catch (Throwable ignored) { }
        return rendererValue;
    }

    /** Writes the chosen renderer to both the instance and the launcher preference. */
    private static void writeRenderer(Instance instance, String target) {
        try {
            if (instance != null) {
                instance.renderer = target;
                instance.write();
            }
        } catch (Throwable ignored) { }
        try {
            LauncherPreferences.PREF_RENDERER = target;
            if (LauncherPreferences.DEFAULT_PREF != null) {
                LauncherPreferences.DEFAULT_PREF.edit().putString("renderer", target).commit();
            }
        } catch (Throwable ignored) { }
    }
}
