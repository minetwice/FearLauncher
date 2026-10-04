package com.fear.skin.agent;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Carries a player's name out of the game and over to the launcher's skin server.
 *
 * On an offline-mode server the client looks a player up by the offline UUID, which is a
 * hash of the name and cannot be reversed. The name, however, is right there in the
 * GameProfile the session service was handed - it just never reaches the request. So it
 * is read off the profile here and posted to the launcher, which can then answer the
 * UUID lookup it would otherwise have to refuse.
 *
 * Deliberately reflective: this class is loaded by the game's own JVM and must not
 * depend on authlib being on its classpath.
 */
public final class FearSkinBridge {

    private static final String ENDPOINT = "http://127.0.0.1:25599/fear/skin-note";
    private static final Set<String> SEEN = Collections.synchronizedSet(new HashSet<String>());

    private static ExecutorService sWorker;

    private FearSkinBridge() {}

    private static synchronized ExecutorService worker() {
        if (sWorker == null) {
            sWorker = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "fear-skin-note");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return sWorker;
    }

    /** Called from the transformed session service, with the profile it was given. */
    public static void note(Object profile) {
        try {
            if (profile == null) return;
            Class<?> type = profile.getClass();
            Method getId = type.getMethod("getId");
            Method getName = type.getMethod("getName");
            Object id = getId.invoke(profile);
            Object name = getName.invoke(profile);
            if (id == null || name == null) return;

            String uuid = String.valueOf(id).replace("-", "").toLowerCase();
            String playerName = String.valueOf(name).trim();
            if (uuid.length() != 32 || playerName.isEmpty()) return;
            if (!SEEN.add(uuid + "|" + playerName)) return;

            final String postUuid = uuid;
            final String postName = playerName;
            worker().execute(new Runnable() {
                @Override
                public void run() {
                    post(postUuid, postName);
                }
            });
        } catch (Throwable ignored) {
            // Nothing here is worth interrupting the game for.
        }
    }

    private static void post(String uuid, String name) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT + "?uuid=" + uuid
                    + "&name=" + URLEncoder.encode(name, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.getResponseCode();
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
