package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private final Pattern mUsernameValidationPattern;
    private EditText mUsernameEditText;

    public LocalLoginFragment() {
        super(R.layout.fragment_local_login);
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            if (!checkEditText()) {
                Tools.dialog(requireContext(),
                        getString(R.string.local_login_bad_username_title),
                        getString(R.string.local_login_bad_username_text));
                return;
            }

            String username = mUsernameEditText.getText().toString().trim();

            // ✅ Create and save local account
            Account account = new Account(username, "local");
            Accounts.addAccount(account);
            Accounts.setCurrentAccount(account);

            Toast.makeText(requireContext(), R.string.main_login_done, Toast.LENGTH_SHORT).show();

            // Refresh UI and go back to main menu
            ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
            Tools.backToMainMenu(requireActivity());
        });
    }

    /** @return Whether the username is valid (3-16 chars, only letters, numbers, underscore) */
    private boolean checkEditText() {
        String text = mUsernameEditText.getText().toString();
        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return !(text.isEmpty()
                || text.length() < 3
                || text.length() > 16
                || !matcher.find());
    }
}
