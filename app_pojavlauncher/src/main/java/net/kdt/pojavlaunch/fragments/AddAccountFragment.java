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

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.Tools;

public class AddAccountFragment extends BottomSheetDialogFragment {
    public static final String TAG = "AddAccountFragment";

    private OnAccountAddedListener mListener;
    private LinearLayout mPanelMicrosoft;
    private LinearLayout mPanelLocal;
    private LinearLayout mTabMicrosoft;
    private LinearLayout mTabLocal;

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
        mPanelMicrosoft = view.findViewById(R.id.panel_microsoft);
        mPanelLocal     = view.findViewById(R.id.panel_local);
        mTabMicrosoft   = view.findViewById(R.id.tab_microsoft);
        mTabLocal       = view.findViewById(R.id.tab_local);

        MaterialButton btnMs    = view.findViewById(R.id.btn_microsoft_login);
        MaterialButton btnLocal = view.findViewById(R.id.btn_local_login);
        EditText inputUsername  = view.findViewById(R.id.local_username_input);
        TextView errorText      = view.findViewById(R.id.local_error_text);
        View btnClose           = view.findViewById(R.id.btn_close_add_account);

        // Default: Microsoft tab selected
        selectTab(true);

        if (mTabMicrosoft != null) mTabMicrosoft.setOnClickListener(v -> selectTab(true));
        if (mTabLocal != null)     mTabLocal.setOnClickListener(v -> selectTab(false));
        if (btnClose != null)      btnClose.setOnClickListener(v -> dismiss());

        // ── Microsoft Login ───────────────────────────────────────────
        if (btnMs != null) {
            btnMs.setOnClickListener(v -> {
                dismiss();
                // Open Microsoft login fragment (it exists as fragment_microsoft_login.xml)
                // Use Tools.swapFragment to navigate to the Microsoft login fragment
                Tools.swapFragment(requireActivity(),
                        MicrosoftLoginFragment.class,
                        MicrosoftLoginFragment.TAG, null);
            });
        }

        // ── Local Account ─────────────────────────────────────────────
        if (btnLocal != null) {
            btnLocal.setOnClickListener(v -> {
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
    }

    private void selectTab(boolean microsoft) {
        if (mPanelMicrosoft == null || mPanelLocal == null) return;
        mPanelMicrosoft.setVisibility(microsoft ? View.VISIBLE : View.GONE);
        mPanelLocal.setVisibility(microsoft ? View.GONE : View.VISIBLE);
        if (mTabMicrosoft != null)
            mTabMicrosoft.setBackgroundResource(microsoft
                    ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
        if (mTabLocal != null)
            mTabLocal.setBackgroundResource(microsoft
                    ? R.drawable.tab_unselected_bg : R.drawable.tab_selected_bg);
    }

    private void showError(TextView errorView, String message) {
        if (errorView == null) return;
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }
}
