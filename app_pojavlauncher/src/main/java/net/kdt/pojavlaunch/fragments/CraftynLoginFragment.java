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

public class CraftynLoginFragment extends Fragment {
    public static final String TAG = "CRAFTYN_LOGIN_FRAGMENT";

    private MineEditText mUsernameEditText;
    private MineEditText mPasswordEditText;

    public CraftynLoginFragment() {
        super(R.layout.fragment_craftyn_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.craftyn_username);
        mPasswordEditText = view.findViewById(R.id.craftyn_password);

        view.findViewById(R.id.craftyn_login_button).setOnClickListener(v -> {
            String username = mUsernameEditText.getText().toString().trim();
            String password = mPasswordEditText.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Enter your CraftynMC username and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Packed as a single string: AccountSpinner's LoginExtraListener passes
            // this straight to CraftynBackgroundLogin, which splits it back apart.
            String code = username + "\n" + password;
            ExtraCore.setValue(ExtraConstants.ELYFLY_LOGIN_TODO, code);
            Tools.backToMainMenu(requireActivity());
        });
    }
}
