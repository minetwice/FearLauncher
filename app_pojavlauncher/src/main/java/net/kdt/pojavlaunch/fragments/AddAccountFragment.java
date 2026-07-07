package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.widget.Button;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.Tools;

public class AddAccountFragment extends BottomSheetDialogFragment {
    public static final String TAG = "AddAccountFragment";

    private OnAccountAddedListener mListener;

    public interface OnAccountAddedListener {
        void onAccountAdded(MinecraftAccount account);
    }

    public void setOnAccountAddedListener(OnAccountAddedListener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText inputUsername  = view.findViewById(R.id.local_username_input);
        TextView errorText      = view.findViewById(R.id.local_error_text);
        View btnLogin           = view.findViewById(R.id.btn_login);
        View btnClose           = view.findViewById(R.id.btn_close_add_account);
        View btnBack            = view.findViewById(R.id.btn_back_add_account);
        android.widget.RadioButton radioMs = view.findViewById(R.id.radio_microsoft);
        android.widget.RadioButton radioLocal = view.findViewById(R.id.radio_local);

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());
        if (btnBack != null) btnBack.setOnClickListener(v -> dismiss());

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                if (radioMs != null && radioMs.isChecked()) {
                    dismiss();
                    Tools.swapFragment(requireActivity(),
                            MicrosoftLoginFragment.class,
                            MicrosoftLoginFragment.TAG, null);
                    return;
                }

                if (inputUsername == null) return;
                String username = inputUsername.getText().toString().trim();

                if (TextUtils.isEmpty(username)) {
                    showError(errorText, "Username cannot be empty");
                    return;
                }
                if (username.length() < 3) {
                    showError(errorText, "Username must be at least 3 characters");
                    return;
                }
                if (username.length() > 16) {
                    showError(errorText, "Username must be 16 characters or less");
                    return;
                }
                if (!username.matches("[a-zA-Z0-9_]+")) {
                    showError(errorText, "Only letters, numbers and _ allowed");
                    return;
                }

                if (errorText != null) errorText.setVisibility(View.GONE);

                final String finalUsername = username;
                try {
                    MinecraftAccount account = Accounts.create(acc -> {
                        acc.username    = finalUsername;
                        acc.authType    = AuthType.LOCAL;
                        acc.accessToken = "0";
                        acc.profileId   = "00000000-0000-0000-0000-000000000000";
                        acc.refreshToken = "0";
                    });
                    Accounts.setCurrent(account);
                    if (mListener != null) mListener.onAccountAdded(account);
                    Toast.makeText(requireContext(),
                            "Account '" + finalUsername + "' created!", Toast.LENGTH_SHORT).show();
                    dismiss();
                } catch (Exception e) {
                    showError(errorText, "Failed to create account: " + e.getMessage());
                }
            });
        }

        // Toggle input visibility based on auth type
        if (radioMs != null) {
            radioMs.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && inputUsername != null) {
                    inputUsername.setAlpha(0.5f);
                    inputUsername.setEnabled(false);
                }
            });
        }
        if (radioLocal != null) {
            radioLocal.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && inputUsername != null) {
                    inputUsername.setAlpha(1.0f);
                    inputUsername.setEnabled(true);
                }
            });
        }
    }

    private void showError(TextView errorView, String message) {
        if (errorView == null) return;
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }
}
