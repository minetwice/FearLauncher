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

            // ✅ Create and save account using reflection (safe and compatible)
            try {
                // 1. Create MinecraftAccount instance
                Class<?> accountClass = Class.forName("net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount");
                Object account = accountClass.newInstance();

                // 2. Set username via reflection
                try {
                    java.lang.reflect.Method setUsername = accountClass.getMethod("setUsername", String.class);
                    setUsername.invoke(account, username);
                } catch (NoSuchMethodException e) {
                    // Fallback: set field directly
                    java.lang.reflect.Field field = accountClass.getField("username");
                    field.set(account, username);
                }

                // 3. Set type to "local"
                try {
                    java.lang.reflect.Method setType = accountClass.getMethod("setType", String.class);
                    setType.invoke(account, "local");
                } catch (NoSuchMethodException e) {
                    java.lang.reflect.Field field = accountClass.getField("type");
                    field.set(account, "local");
                }

                // 4. Add to Accounts using reflection
                try {
                    java.lang.reflect.Method addAccount = Accounts.class.getMethod("addAccount", Account.class);
                    addAccount.invoke(null, account);
                } catch (NoSuchMethodException e) {
                    // Try alternative method name
                    try {
                        java.lang.reflect.Method add = Accounts.class.getMethod("add", Account.class);
                        add.invoke(null, account);
                    } catch (NoSuchMethodException ex) {
                        // Try field access
                        java.lang.reflect.Field accountsField = Accounts.class.getDeclaredField("sAccounts");
                        accountsField.setAccessible(true);
                        java.util.List<Object> list = (java.util.List<Object>) accountsField.get(null);
                        list.add(account);
                    }
                }

                // 5. Set as current account
                try {
                    java.lang.reflect.Method setCurrent = Accounts.class.getMethod("setCurrentAccount", Account.class);
                    setCurrent.invoke(null, account);
                } catch (NoSuchMethodException e) {
                    try {
                        java.lang.reflect.Method setCurrent = Accounts.class.getMethod("setCurrent", Account.class);
                        setCurrent.invoke(null, account);
                    } catch (NoSuchMethodException ex) {
                        java.lang.reflect.Field currentField = Accounts.class.getDeclaredField("sCurrentAccount");
                        currentField.setAccessible(true);
                        currentField.set(null, account);
                    }
                }

                Toast.makeText(requireContext(), R.string.main_login_done, Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
                // Fallback: use original ExtraCore method
                net.kdt.pojavlaunch.extra.ExtraCore.setValue(
                        net.kdt.pojavlaunch.extra.ExtraConstants.MOJANG_LOGIN_TODO,
                        new String[]{username, ""}
                );
                Toast.makeText(requireContext(), "Login attempted via fallback", Toast.LENGTH_SHORT).show();
            }

            // Go back to dashboard
            Tools.backToMainMenu(requireActivity());
        });
    }

    private boolean checkEditText() {
        String text = mUsernameEditText.getText().toString().trim();
        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return matcher.find();
    }
}
