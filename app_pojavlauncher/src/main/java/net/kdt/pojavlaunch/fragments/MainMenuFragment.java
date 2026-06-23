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
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView accountName;
    private TextView versionText;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        accountName = view.findViewById(R.id.account_name);
        versionText = view.findViewById(R.id.version_text);
        Button playButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);

        updateAccountName();
        updateVersionText();

        // Play Button
        if (playButton != null) {
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }

        // Edit Profile (opens version selector)
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }
    }

    /**
     * Updates the account name display using reflection to safely call getUsername()
     */
    private void updateAccountName() {
        if (accountName == null) return;

        String username = null;
        Object currentAccount = Accounts.getCurrent();

        if (currentAccount != null) {
            try {
                // Try to call getUsername() via reflection
                java.lang.reflect.Method method = currentAccount.getClass().getMethod("getUsername");
                username = (String) method.invoke(currentAccount);
            } catch (Exception e) {
                // Fallback: use toString()
                username = currentAccount.toString();
            }
        }

        if (username == null || username.isEmpty()) {
            username = "FearUser";
        }
        accountName.setText(username);
    }

    /**
     * Updates the version text based on the selected instance
     */
    private void updateVersionText() {
        if (versionText == null) return;

        Instance instance = Instances.loadSelectedInstance();
        if (instance != null && instance.versionId != null && !instance.versionId.isEmpty()) {
            versionText.setText(instance.versionId);
        } else {
            versionText.setText("1.21.1"); // fallback default
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh account spinner, account name, and version text
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        updateAccountName();
        updateVersionText();
    }
}
