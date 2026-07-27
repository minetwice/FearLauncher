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
import net.kdt.pojavlaunch.authenticator.model.FearNetAuthResponse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Talks directly to the FearNet server's Yggdrasil-style login endpoints
 * (/authserver/authenticate and /authserver/refresh). Unlike Microsoft/Ely.by,
 * this is a plain username+password login - no OAuth redirect needed.
 *
 * The generic BackgroundLogin.createAccount(listener, code) signature only
 * carries a single String, so the login fragment packs "username\npassword"
 * into that one field before calling us - see FearNetLoginFragment.
 */
public class FearNetBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = FearNetBackgroundLogin::new;

    // Must match the domain your FearNet server is deployed at (same value as
    // AuthType.FEAR_NET's injectorUrl, just with the scheme in front for direct HTTP calls).
    private static final String SERVER_BASE_URL = "https://farmer-my1t.onrender.com";

    private FearNetBackgroundLogin() {}

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
                FearNetAuthResponse response = authenticate(username, password);
                MinecraftAccount account = Accounts.create(acc -> fillAccount(acc, response));
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("FearNetLogin", "Exception thrown during authentication", e);
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
                FearNetAuthResponse response = refresh(account.accessToken, account.refreshToken);
                fillAccount(account, response);
                account.save();
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("FearNetLogin", "Exception thrown during refresh", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    private void fillAccount(MinecraftAccount acc, FearNetAuthResponse response) {
        acc.authType = AuthType.FEAR_NET;
        acc.accessToken = response.accessToken;
        // MinecraftAccount has no dedicated "clientToken" field, so we store it in
        // refreshToken - FearNet's /authserver/refresh needs both accessToken and
        // clientToken together, unlike OAuth-style refresh tokens.
        acc.refreshToken = response.clientToken;
        acc.username = response.selectedProfile.name;
        acc.profileId = response.selectedProfile.id;
        acc.xuid = null;
        acc.expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000; // FearNet sessions don't hard-expire; this just avoids unnecessary refreshes
        acc.updateSkinFace();
    }

    private FearNetAuthResponse authenticate(String username, String password) throws IOException {
        String json = "{\"username\":" + jsonString(username) + ",\"password\":" + jsonString(password) + "}";
        return postJson(SERVER_BASE_URL + "/authserver/authenticate", json);
    }

    private FearNetAuthResponse refresh(String accessToken, String clientToken) throws IOException {
        String json = "{\"accessToken\":" + jsonString(accessToken) + ",\"clientToken\":" + jsonString(clientToken) + "}";
        return postJson(SERVER_BASE_URL + "/authserver/refresh", json);
    }

    private FearNetAuthResponse postJson(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                return Tools.GLOBAL_GSON.fromJson(reader, FearNetAuthResponse.class);
            } finally {
                conn.disconnect();
            }
        } else {
            String errorBody = Tools.read(conn.getErrorStream());
            Log.i("FearNetLogin", "Login failed (" + code + "): " + errorBody);
            conn.disconnect();
            throw new IOException(parseErrorMessage(errorBody, code));
        }
    }

    private String parseErrorMessage(String errorBody, int code) {
        try {
            // Our server returns {"error": "..."} on the website API and
            // {"error": "...", "errorMessage": "..."} on the Yggdrasil endpoints.
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


