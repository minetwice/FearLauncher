package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private final Pattern mUsernameValidationPattern;
    private EditText mUsernameEditText;

    public LocalLoginFragment() {
        super(R.layout.fragment_local_login);
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            String username = mUsernameEditText.getText().toString().trim();
            if (!checkEditText()) {
                Tools.dialog(requireContext(),
                        getString(R.string.local_login_bad_username_title),
                        getString(R.string.local_login_bad_username_text));
                return;
            }

            // ✅ PojavLauncher's built-in local login mechanism
            ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{username, ""});

            // Go back to dashboard – account will be automatically saved by the app
            Tools.backToMainMenu(requireActivity());
        });
    }

    private boolean checkEditText() {
        String text = mUsernameEditText.getText().toString().trim();
        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return matcher.find();
    }
}
