package net.kdt.pojavlaunch.authenticator.impl;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Log;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.BackgroundLogin;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.authenticator.listener.LoginListener;
import net.kdt.pojavlaunch.authenticator.model.CraftynAuthResponse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Talks directly to your server's real Yggdrasil-style login endpoints
 * (/yggdrasil/authserver/authenticate and /yggdrasil/authserver/refresh) -
 * the same protocol Ely.by uses. This does NOT touch the website's /login
 * endpoint (that one issues a website session JWT, not a valid Minecraft
 * access token) and does NOT download/save skin files locally (that never
 * actually reaches the game - authlib-injector handles skin display
 * automatically once AuthType.CRAFTYN_MC.injectorUrl is set correctly).
 *
 * The credentials are packed as "username\npassword" into the single String
 * the BackgroundLogin interface carries - see CraftynLoginFragment.
 */
public class CraftynBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = CraftynBackgroundLogin::new;

    // Must match AuthType.CRAFTYN_MC's injectorUrl (with the scheme in front,
    // since this is used for direct HTTP calls) - including the /yggdrasil
    // path, since the server's website homepage and its Yggdrasil API can't
    // both live at the bare "/" root.
    private static final String SERVER_BASE_URL = "https://farmer-my1t.onrender.com/yggdrasil";

    private CraftynBackgroundLogin() {}

    @Override
    public void createAccount(@NonNull LoginListener loginListener, String code) {
        String[] parts = code.split("\n", 2);
        if (parts.length != 2) {
            Tools.runOnUiThread(() -> loginListener.onLoginError(new IllegalArgumentException("Missing username or password")));
            return;
        }
        String username = parts[0];
        String password = parts[1];

        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(1);
            try {
                Tools.runOnUiThread(() -> loginListener.onLoginProgress(1));
                CraftynAuthResponse response = authenticate(username, password);
                MinecraftAccount account = Accounts.create(acc -> fillAccount(acc, response));
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("CraftynAuth", "Exception thrown during authentication", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    @Override
    public void refreshAccount(@NonNull LoginListener loginListener, MinecraftAccount account) {
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(1);
            try {
                Tools.runOnUiThread(() -> loginListener.onLoginProgress(1));
                // account.refreshToken doubles as the Yggdrasil "clientToken" here - see fillAccount().
                CraftynAuthResponse response = refresh(account.accessToken, account.refreshToken);
                fillAccount(account, response);
                account.save();
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("CraftynAuth", "Exception thrown during refresh", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    private void fillAccount(MinecraftAccount acc, CraftynAuthResponse response) {
        acc.authType = AuthType.CRAFTYN_MC;
        acc.accessToken = response.accessToken;
        // MinecraftAccount has no dedicated "clientToken" field, so we store it in
        // refreshToken - the server's /authserver/refresh needs both accessToken
        // and clientToken together, unlike OAuth-style refresh tokens. This is
        // NOT the account's password - never store the raw password here.
        acc.refreshToken = response.clientToken;
        acc.username = response.selectedProfile.name;
        acc.profileId = response.selectedProfile.id;
        acc.xuid = null;
        acc.expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000; // sessions don't hard-expire server-side; this just avoids unnecessary refreshes
        acc.updateSkinFace();
    }

    private CraftynAuthResponse authenticate(String username, String password) throws IOException {
        String json = "{\"username\":" + jsonString(username) + ",\"password\":" + jsonString(password) + "}";
        return postJson(SERVER_BASE_URL + "/authserver/authenticate", json);
    }

    private CraftynAuthResponse refresh(String accessToken, String clientToken) throws IOException {
        String json = "{\"accessToken\":" + jsonString(accessToken) + ",\"clientToken\":" + jsonString(clientToken) + "}";
        return postJson(SERVER_BASE_URL + "/authserver/refresh", json);
    }

    private CraftynAuthResponse postJson(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                return Tools.GLOBAL_GSON.fromJson(reader, CraftynAuthResponse.class);
            } finally {
                conn.disconnect();
            }
        } else {
            String errorBody = Tools.read(conn.getErrorStream());
            Log.i("CraftynAuth", "Login failed (" + code + "): " + errorBody);
            conn.disconnect();
            throw new IOException(parseErrorMessage(errorBody, code));
        }
    }

    private String parseErrorMessage(String errorBody, int code) {
        try {
            com.google.gson.JsonObject obj = Tools.GLOBAL_GSON.fromJson(errorBody, com.google.gson.JsonObject.class);
            if (obj.has("errorMessage")) return obj.get("errorMessage").getAsString();
            if (obj.has("error")) return obj.get("error").getAsString();
        } catch (Exception ignored) { }
        return "Login failed (HTTP " + code + ")";
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
