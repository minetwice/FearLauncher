package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.MineEditText;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class FearNetLoginFragment extends Fragment {
    public static final String TAG = "FEARNET_LOGIN_FRAGMENT";

    private MineEditText mUsernameEditText;
    private MineEditText mPasswordEditText;

    public FearNetLoginFragment() {
        super(R.layout.fragment_fearnet_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.fearnet_username);
        mPasswordEditText = view.findViewById(R.id.fearnet_password);

        view.findViewById(R.id.fearnet_login_button).setOnClickListener(v -> {
            String username = mUsernameEditText.getText().toString().trim();
            String password = mPasswordEditText.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Enter your FearNet username and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Packed as a single string: AccountSpinner's LoginExtraListener expects
            // one String value and passes it straight to FearNetBackgroundLogin,
            // which splits it back into username/password.
            String code = username + "\n" + password;
            ExtraCore.setValue(ExtraConstants.FEARNET_LOGIN_TODO, code);
            Tools.backToMainMenu(requireActivity());
        });
    }
}


