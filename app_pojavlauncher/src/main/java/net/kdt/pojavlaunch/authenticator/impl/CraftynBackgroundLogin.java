package net.kdt.pojavlaunch.authenticator.impl;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.BackgroundLogin;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.authenticator.listener.LoginListener;
import net.kdt.pojavlaunch.skins.FearSkinSync;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CraftynBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = CraftynBackgroundLogin::new;

    private static final String loginUrl = "https://craftynmc.onrender.com/login";

    private String mToken;
    private String mUsername;
    private String mUuid;
    private String mPassword;

    private CraftynBackgroundLogin() {}

    public void setCredentials(String username, String password) {
        this.mUsername = username;
        this.mPassword = password;
    }

    private void authenticateUser(@NonNull LoginListener loginListener, Runnable onSuccess) {
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(2);
            try {
                notifyProgress(loginListener, 1);
                // Perform authentication request to CraftynMC website
                URL url = new URL(loginUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JsonObject json = new JsonObject();
                json.addProperty("username", mUsername);
                json.addProperty("password", mPassword);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream();
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[1024];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            bos.write(buf, 0, read);
                        }
                        String responseStr = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                        JsonObject response = Tools.GLOBAL_GSON.fromJson(responseStr, JsonObject.class);

                        mToken = response.get("token").getAsString();
                        JsonObject userJson = response.getAsJsonObject("user");
                        mUsername = userJson.get("username").getAsString();
                        mUuid = userJson.get("uuid").getAsString();
                        // FEAR: the slim (Alex) model is forced for every account, so the
                        // server-reported skinModel no longer decides the model.
                        Context context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
                        if (context != null) {
                            PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit()
                                    .putBoolean("active_skin_is_alex", true)
                                    .apply();
                        }

                        notifyProgress(loginListener, 2);
                        onSuccess.run();
                    }
                } else {
                    throw new IOException("Failed to login to CraftynMC. Response code: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e("CraftynAuth", "Error during login", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    private void fillAccount(MinecraftAccount acc) {
        acc.authType = AuthType.CRAFTYN_MC;
        acc.accessToken = mToken;
        acc.refreshToken = mPassword;
        acc.username = mUsername;
        acc.profileId = mUuid;
        acc.xuid = null;
        acc.updateSkinFace();
    }

    @Override
    public void createAccount(@NonNull LoginListener loginListener, String credentials) {
        String[] parts = credentials.split(":", 2);
        if (parts.length == 2) {
            mUsername = parts[0];
            mPassword = parts[1];
        }
        authenticateUser(loginListener, () -> {
            try {
                MinecraftAccount account = Accounts.create(this::fillAccount);
                Context context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
                downloadAndSetSkin(context, mUsername, mUuid);
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("CraftynAuth", "Error creating account", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            }
        });
    }

    @Override
    public void refreshAccount(@NonNull LoginListener loginListener, MinecraftAccount account) {
        mUsername = account.username;
        mUuid = account.profileId;
        mPassword = account.refreshToken;

        sExecutorService.execute(() -> {
            try {
                Context context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
                downloadAndSetSkin(context, mUsername, mUuid);
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("CraftynAuth", "Error refreshing skin", e);
                Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            }
        });
    }

    private void downloadAndSetSkin(Context context, String username, String uuid) {
        // Kept as a thin wrapper so the callers below read the same as before; the retrying,
        // non-destructive version lives in FearSkinSync.
        FearSkinSync.downloadCraftynSkin(context, username, uuid);
    }

    private void notifyProgress(LoginListener listener, int step) {
        Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, step * 50);
    }
}
