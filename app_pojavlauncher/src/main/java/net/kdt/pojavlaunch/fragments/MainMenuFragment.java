package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView accountName;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        accountName = view.findViewById(R.id.account_name);
        TextView versionText = view.findViewById(R.id.version_text);
        Button playButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);

        updateAccountName();

        if (playButton != null) {
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }

        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }

        if (versionText != null) {
            // Set dynamic version later
        }
    }

    private void updateAccountName() {
        if (accountName != null) {
            Object currentAccount = Accounts.getCurrent();
            if (currentAccount != null) {
                // Try to get username via reflection or using known methods
                // For PojavLauncher, Accounts.getCurrent() returns an Account object with getUsername()
                try {
                    // Use reflection to call getUsername() if available
                    java.lang.reflect.Method method = currentAccount.getClass().getMethod("getUsername");
                    String username = (String) method.invoke(currentAccount);
                    if (username != null && !username.isEmpty()) {
                        accountName.setText(username);
                        return;
                    }
                } catch (Exception ignored) {
                    // Fallback: use toString() or default
                }
                // Fallback: try to get display name or something else
                try {
                    java.lang.reflect.Method method = currentAccount.getClass().getMethod("getDisplayName");
                    String displayName = (String) method.invoke(currentAccount);
                    if (displayName != null && !displayName.isEmpty()) {
                        accountName.setText(displayName);
                        return;
                    }
                } catch (Exception ignored) {
                    // Ignore
                }
                // Last resort: use toString()
                accountName.setText(currentAccount.toString());
            } else {
                accountName.setText("FearUser");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        updateAccountName();
    }
}
