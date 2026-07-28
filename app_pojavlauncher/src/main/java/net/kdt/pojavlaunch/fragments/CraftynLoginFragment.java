package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.gson.JsonObject;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CraftynLoginFragment extends Fragment {
    public static final String TAG = "CRAFTYN_LOGIN_FRAGMENT";

    private WebView mWebView;
    private Handler mHandler;
    private Runnable mPollingRunnable;
    private boolean mIsCompleted = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mWebView = new WebView(requireContext());
        mWebView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return mWebView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mHandler = new Handler(Looper.getMainLooper());

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "Loaded web page: " + url + ", starting local storage polling.");
                startLocalStoragePolling();
            }
        });

        Toast.makeText(requireContext(), "Opening CraftynMC Station...", Toast.LENGTH_SHORT).show();
        mWebView.loadUrl("https://farmer-my1t.onrender.com/");
    }

    private void startLocalStoragePolling() {
        if (mPollingRunnable != null) {
            mHandler.removeCallbacks(mPollingRunnable);
        }

        mPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWebView == null || mIsCompleted) return;

                mWebView.evaluateJavascript("localStorage.getItem('userInfo');", userInfoRaw -> {
                    mWebView.evaluateJavascript("localStorage.getItem('token');", tokenRaw -> {
                        if (userInfoRaw != null && !userInfoRaw.equals("null") && !userInfoRaw.equals("\"\"") &&
                            tokenRaw != null && !tokenRaw.equals("null") && !tokenRaw.equals("\"\"")) {

                            mIsCompleted = true;
                            handleCapturedCredentials(userInfoRaw, tokenRaw);
                        } else {
                            mHandler.postDelayed(mPollingRunnable, 1000);
                        }
                    });
                });
            }
        };
        mHandler.postDelayed(mPollingRunnable, 1000);
    }

    private void handleCapturedCredentials(String userInfoRaw, String tokenRaw) {
        try {
            String userInfoJson = unescapeJsString(userInfoRaw);
            String token = unescapeJsString(tokenRaw);

            Log.i(TAG, "Captured token and user credentials successfully! userInfo: " + userInfoJson);

            JsonObject response = Tools.GLOBAL_GSON.fromJson(userInfoJson, JsonObject.class);
            String username = response.get("username").getAsString();
            String uuid = response.get("uuid").getAsString();

            MinecraftAccount account = Accounts.create(acc -> {
                acc.authType = AuthType.CRAFTYN_MC;
                acc.accessToken = token;
                acc.refreshToken = "";
                acc.username = username;
                acc.profileId = uuid;
                acc.xuid = null;
                acc.updateSkinFace();
            });

            // Set current and refresh spinner
            Accounts.setCurrent(account);
            ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);

            // Fetch and download skin in the background
            downloadAndSetSkin(net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication(), username, uuid);

            Toast.makeText(requireContext(), "Welcome " + username + "! Connected successfully.", Toast.LENGTH_LONG).show();

            Tools.backToMainMenu(requireActivity());
        } catch (Exception e) {
            Log.e(TAG, "Error handling captured credentials", e);
            mIsCompleted = false;
            mHandler.postDelayed(mPollingRunnable, 1000);
        }
    }

    private void downloadAndSetSkin(Context context, String username, String uuid) {
        net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
            try {
                URL url = new URL("https://farmer-my1t.onrender.com/skins/" + uuid + ".png");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    File skinsDir = new File(Tools.DIR_GAME_HOME, "skins");
                    if (!skinsDir.exists()) skinsDir.mkdirs();
                    File skinFile = new File(skinsDir, "craftynmc_" + username + ".png");
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(skinFile)) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    if (context != null) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        prefs.edit().putString("active_skin_path", skinFile.getAbsolutePath()).apply();
                        Log.i("CraftynSkin", "Downloaded and activated CraftynMC skin for " + username);
                    }
                }
            } catch (Exception e) {
                Log.w("CraftynSkin", "Could not download CraftynMC skin", e);
            }
        });
    }

    private String unescapeJsString(String s) {
        if (s == null) return "";
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Override
    public void onDestroyView() {
        if (mPollingRunnable != null) {
            mHandler.removeCallbacks(mPollingRunnable);
        }
        super.onDestroyView();
    }
}
