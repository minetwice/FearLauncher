package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.gson.JsonObject;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CraftynLoginFragment extends Fragment {
    public static final String TAG = "CRAFTYN_LOGIN_FRAGMENT";

    // Form elements
    private EditText mUsernameInput;
    private EditText mPasswordInput;
    private TextView mAvailabilityIndicator;
    private Button mSubmitBtn;
    private Button mTabLogin;
    private Button mTabSignUp;
    private View mFormWrapper;

    // Stage Progress elements
    private View mStageContainer;
    private TextView mStageTitle;
    private TextView mStage1Icon;
    private TextView mStage1Text;
    private View mStageLine1;
    private TextView mStage2Icon;
    private TextView mStage2Text;
    private View mStageLine2;
    private TextView mStage3Icon;
    private TextView mStage3Text;
    private TextView mStatusDetail;

    // Logic variables
    private boolean mIsSignUpMode = false; // default is LOGIN
    private Handler mHandler;
    private Runnable mCheckRunnable;
    private boolean mUsernameChecked = false;
    private boolean mUsernameAvailable = false;

    public CraftynLoginFragment() {
        super(R.layout.fragment_craftyn_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mHandler = new Handler(Looper.getMainLooper());

        // Bind Form Views
        mUsernameInput = view.findViewById(R.id.craftyn_username);
        mPasswordInput = view.findViewById(R.id.craftyn_password);
        mAvailabilityIndicator = view.findViewById(R.id.craftyn_availability_indicator);
        mSubmitBtn = view.findViewById(R.id.craftyn_submit_btn);
        mTabLogin = view.findViewById(R.id.tab_craftyn_login);
        mTabSignUp = view.findViewById(R.id.tab_craftyn_signup);
        mFormWrapper = view.findViewById(R.id.craftyn_form_wrapper);

        // Bind Stage Views
        mStageContainer = view.findViewById(R.id.craftyn_stage_container);
        mStageTitle = view.findViewById(R.id.craftyn_stage_title);
        mStage1Icon = view.findViewById(R.id.stage_1_icon);
        mStage1Text = view.findViewById(R.id.stage_1_text);
        mStageLine1 = view.findViewById(R.id.stage_line_1);
        mStage2Icon = view.findViewById(R.id.stage_2_icon);
        mStage2Text = view.findViewById(R.id.stage_2_text);
        mStageLine2 = view.findViewById(R.id.stage_line_2);
        mStage3Icon = view.findViewById(R.id.stage_3_icon);
        mStage3Text = view.findViewById(R.id.stage_3_text);
        mStatusDetail = view.findViewById(R.id.craftyn_status_detail);

        // Set Tab Listeners
        mTabLogin.setOnClickListener(v -> setSignUpMode(false));
        mTabSignUp.setOnClickListener(v -> setSignUpMode(true));

        // Submit Button Click
        mSubmitBtn.setOnClickListener(v -> executeAuthAction());

        // Username Availability live-typing checker
        mUsernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mCheckRunnable != null) {
                    mHandler.removeCallbacks(mCheckRunnable);
                }
                mCheckRunnable = () -> performLiveUsernameCheck(s.toString().trim());
                mHandler.postDelayed(mCheckRunnable, 350); // debounce input
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initialize view states
        setSignUpMode(false);
    }

    private void setSignUpMode(boolean signUp) {
        mIsSignUpMode = signUp;
        if (signUp) {
            mTabSignUp.setBackgroundResource(R.drawable.premium_play_button_bg);
            mTabLogin.setBackgroundResource(R.drawable.premium_glass_black_bg);
            mSubmitBtn.setText("EXECUTE SIGN UP");
        } else {
            mTabLogin.setBackgroundResource(R.drawable.premium_play_button_bg);
            mTabSignUp.setBackgroundResource(R.drawable.premium_glass_black_bg);
            mSubmitBtn.setText("EXECUTE LOG IN");
        }
        // Retrigger check for current input
        if (mUsernameInput != null) {
            performLiveUsernameCheck(mUsernameInput.getText().toString().trim());
        }
    }

    private void performLiveUsernameCheck(String username) {
        if (username.isEmpty()) {
            mAvailabilityIndicator.setText("Enter username to verify");
            mAvailabilityIndicator.setTextColor(Color.parseColor("#80FFFFFF"));
            mUsernameChecked = false;
            return;
        }

        mAvailabilityIndicator.setText("Checking availability...");
        mAvailabilityIndicator.setTextColor(Color.parseColor("#00F0FF"));

        PojavApplication.sExecutorService.execute(() -> {
            try {
                URL url = new URL("https://craftynmc.onrender.com/api/check-username?username=" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

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
                        boolean available = response.get("available").getAsBoolean();

                        mHandler.post(() -> {
                            mUsernameChecked = true;
                            mUsernameAvailable = available;
                            if (mIsSignUpMode) {
                                if (available) {
                                    mAvailabilityIndicator.setText("✓ Username Available");
                                    mAvailabilityIndicator.setTextColor(Color.parseColor("#00FF66"));
                                } else {
                                    mAvailabilityIndicator.setText("✗ Username Taken");
                                    mAvailabilityIndicator.setTextColor(Color.parseColor("#FF5252"));
                                }
                            } else {
                                if (available) {
                                    mAvailabilityIndicator.setText("✗ Username Not Registered");
                                    mAvailabilityIndicator.setTextColor(Color.parseColor("#FF5252"));
                                } else {
                                    mAvailabilityIndicator.setText("✓ Registered User Found");
                                    mAvailabilityIndicator.setTextColor(Color.parseColor("#00FF66"));
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {
                mHandler.post(() -> {
                    mAvailabilityIndicator.setText("Live connection bypass");
                    mAvailabilityIndicator.setTextColor(Color.parseColor("#40FFFFFF"));
                    mUsernameChecked = false;
                });
            }
        });
    }

    private void executeAuthAction() {
        String username = mUsernameInput.getText().toString().trim();
        String password = mPasswordInput.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter both credentials", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide main form and show beautiful Stage Progress HUD
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(250);
        mFormWrapper.startAnimation(fadeOut);
        mFormWrapper.setVisibility(View.GONE);

        mStageContainer.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(250);
        mStageContainer.startAnimation(fadeIn);

        // Run authenticating background process
        PojavApplication.sExecutorService.execute(() -> {
            try {
                // STAGE 1: Account Creation or Verification
                updateStageUI(1, "Account Creation / Verification...", "#00F0FF", "#40FFFFFF", "#40FFFFFF");
                Thread.sleep(800); // Elegant delay for animation visibility

                String endpoint = mIsSignUpMode ? "register" : "login";
                URL url = new URL("https://craftynmc.onrender.com/" + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                JsonObject json = new JsonObject();
                json.addProperty("username", username);
                json.addProperty("password", password);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int respCode = conn.getResponseCode();
                if (respCode == 200) {
                    try (InputStream is = conn.getInputStream();
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[1024];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            bos.write(buf, 0, read);
                        }
                        String responseStr = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                        JsonObject response = Tools.GLOBAL_GSON.fromJson(responseStr, JsonObject.class);

                        String token = response.get("token").getAsString();
                        JsonObject userJson = response.getAsJsonObject("user");
                        String finalUser = userJson.get("username").getAsString();
                        String uuid = userJson.get("uuid").getAsString();

                        // STAGE 2: Connectivity Pass & Skin Fetch
                        updateStageUI(2, "Establishing connectivity pass & fetching custom skin...", "#00FF66", "#00F0FF", "#40FFFFFF");
                        Thread.sleep(800);

                        boolean skinSuccess = false;
                        try {
                            URL skinUrl = new URL("https://craftynmc.onrender.com/skins/" + uuid + ".png");
                            HttpURLConnection skinConn = (HttpURLConnection) skinUrl.openConnection();
                            skinConn.setRequestMethod("GET");
                            skinConn.setConnectTimeout(4000);
                            if (skinConn.getResponseCode() == 200) {
                                File skinsDir = new File(Tools.DIR_GAME_HOME, "skins");
                                if (!skinsDir.exists()) skinsDir.mkdirs();
                                File skinFile = new File(skinsDir, "craftynmc_" + finalUser + ".png");
                                try (InputStream in = skinConn.getInputStream();
                                     FileOutputStream out = new FileOutputStream(skinFile)) {
                                    byte[] sBuf = new byte[1024];
                                    int sRead;
                                    while ((read = in.read(sBuf)) != -1) {
                                        out.write(sBuf, 0, read);
                                    }
                                }
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication());
                                prefs.edit().putString("active_skin_path", skinFile.getAbsolutePath()).apply();
                                skinSuccess = true;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Skin fetch bypassed or not found", e);
                        }

                        // Save account in launcher
                        MinecraftAccount account = Accounts.create(acc -> {
                            acc.authType = AuthType.CRAFTYN_MC;
                            acc.accessToken = token;
                            acc.refreshToken = password;
                            acc.username = finalUser;
                            acc.profileId = uuid;
                            acc.xuid = null;
                            acc.updateSkinFace();
                        });

                        Accounts.setCurrent(account);
                        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);

                        // STAGE 3: Done
                        updateStageUI(3, "Station synchronization complete! Launching...", "#00FF66", "#00FF66", "#00FF66");
                        Thread.sleep(1000);

                        mHandler.post(() -> {
                            Toast.makeText(requireContext(), "Connected as " + finalUser + "!", Toast.LENGTH_SHORT).show();
                            Tools.backToMainMenu(requireActivity());
                        });
                    }
                } else {
                    throw new Exception("Server response failed: " + respCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Action failed", e);
                mHandler.post(() -> handleAuthFailure(e.getMessage()));
            }
        });
    }

    private void updateStageUI(int activeStage, String detailText, String s1Color, String s2Color, String s3Color) {
        mHandler.post(() -> {
            mStatusDetail.setText(detailText);

            mStage1Icon.setBackgroundResource(R.drawable.premium_button_bg);
            mStage1Icon.setTextColor(Color.parseColor(s1Color));
            mStage1Text.setTextColor(Color.parseColor(s1Color));

            mStageLine1.setBackgroundColor(Color.parseColor(s2Color));

            mStage2Icon.setBackgroundResource(R.drawable.premium_button_bg);
            mStage2Icon.setTextColor(Color.parseColor(s2Color));
            mStage2Text.setTextColor(Color.parseColor(s2Color));

            mStageLine2.setBackgroundColor(Color.parseColor(s3Color));

            mStage3Icon.setBackgroundResource(R.drawable.premium_button_bg);
            mStage3Icon.setTextColor(Color.parseColor(s3Color));
            mStage3Text.setTextColor(Color.parseColor(s3Color));
        });
    }

    private void handleAuthFailure(String errorMsg) {
        mStatusDetail.setText("✗ Failed: Check username or server credentials");
        mStatusDetail.setTextColor(Color.parseColor("#FF5252"));

        mStage1Icon.setTextColor(Color.parseColor("#FF5252"));
        mStage1Text.setTextColor(Color.parseColor("#FF5252"));
        mStageLine1.setBackgroundColor(Color.parseColor("#FF5252"));
        mStage2Icon.setTextColor(Color.parseColor("#FF5252"));
        mStage2Text.setTextColor(Color.parseColor("#FF5252"));
        mStageLine2.setBackgroundColor(Color.parseColor("#FF5252"));
        mStage3Icon.setTextColor(Color.parseColor("#FF5252"));
        mStage3Text.setTextColor(Color.parseColor("#FF5252"));

        mHandler.postDelayed(() -> {
            mStageContainer.setVisibility(View.GONE);
            mFormWrapper.setVisibility(View.VISIBLE);
            mStatusDetail.setTextColor(Color.parseColor("#B3FFFFFF"));
            performLiveUsernameCheck(mUsernameInput.getText().toString().trim());
        }, 3000); // return to form after 3 seconds so they can fix input
    }
}
