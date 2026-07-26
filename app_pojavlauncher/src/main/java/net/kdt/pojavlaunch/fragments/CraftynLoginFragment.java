package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class CraftynLoginFragment extends Fragment {
    public static final String TAG = "CRAFTYN_LOGIN_FRAGMENT";

    private EditText mUsernameField;
    private EditText mPasswordField;

    public CraftynLoginFragment() {
        super(R.layout.fragment_craftyn_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameField = view.findViewById(R.id.craftyn_username);
        mPasswordField = view.findViewById(R.id.craftyn_password);

        view.findViewById(R.id.craftyn_login_btn).setOnClickListener(v -> {
            String username = mUsernameField.getText().toString().trim();
            String password = mPasswordField.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both username and password!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Trigger the craftynmc/elyfly login callback with the entered credentials
            ExtraCore.setValue(ExtraConstants.ELYFLY_LOGIN_TODO, username + ":" + password);

            // Go back to the dashboard, account spinner handles login completion in background
            Tools.backToMainMenu(requireActivity());
        });

        view.findViewById(R.id.craftyn_register_web_btn).setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://farmer-my1t.onrender.com/"));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
