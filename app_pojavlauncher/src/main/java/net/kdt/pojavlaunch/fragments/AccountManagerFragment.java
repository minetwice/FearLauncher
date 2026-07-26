package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.Tools;

import java.util.ArrayList;
import java.util.List;

public class AccountManagerFragment extends BottomSheetDialogFragment {
    public static final String TAG = "AccountManagerFragment";

    private OnAccountSelectedListener mListener;
    private AccountAdapter mAdapter;
    private View mEmptyState;

    // Controllers
    private EditText mInputUsername;
    private TextView mErrorText;
    private View mCardMs;
    private View mCardMojang;
    private View mCardLocal;
    private View mBtnAddAccount;
    private View mBtnSwitchAccount;

    private AuthType mSelectedAuthType = AuthType.MICROSOFT;
    private MinecraftAccount mSelectedAccountInList = null;

    public interface OnAccountSelectedListener {
        void onAccountSelected(MinecraftAccount account);
    }

    public void setOnAccountSelectedListener(OnAccountSelectedListener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recyclerView = view.findViewById(R.id.account_list);
        mEmptyState = view.findViewById(R.id.empty_account_state);
        View btnClose = view.findViewById(R.id.btn_close_account_manager);

        // Right panel inputs
        mInputUsername = view.findViewById(R.id.local_username_input);
        mErrorText     = view.findViewById(R.id.local_error_text);
        mCardMs        = view.findViewById(R.id.card_type_ms);
        mCardMojang    = view.findViewById(R.id.card_type_mojang);
        mCardLocal     = view.findViewById(R.id.card_type_local);
        mBtnAddAccount = view.findViewById(R.id.btn_add_account);
        mBtnSwitchAccount = view.findViewById(R.id.btn_switch_account);
        View troubleLink = view.findViewById(R.id.trouble_logging_in);

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        // Setup Trouble Logging In
        if (troubleLink != null) {
            troubleLink.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://farmer-my1t.onrender.com/"));
                requireContext().startActivity(intent);
            });
        }

        // Load accounts
        List<MinecraftAccount> accountList = loadAccounts();
        MinecraftAccount current = Accounts.getCurrent();

        // Default list selection to current active account
        mSelectedAccountInList = current;

        mAdapter = new AccountAdapter(accountList, current, mSelectedAccountInList,
                // On list click
                account -> {
                    mSelectedAccountInList = account;
                    mAdapter.setSelectedAccount(account);
                },
                // On delete click (three dots menu)
                account -> {
                    Accounts.delete(account);
                    accountList.remove(account);
                    mAdapter.notifyDataSetChanged();
                    updateEmptyState(accountList);
                    if (mSelectedAccountInList != null && mSelectedAccountInList.mSaveLocation != null
                            && account.mSaveLocation != null
                            && mSelectedAccountInList.mSaveLocation.getName().equals(account.mSaveLocation.getName())) {
                        mSelectedAccountInList = null;
                        mAdapter.setSelectedAccount(null);
                    }
                    // If deleted account was the active one, notify parent
                    if (current != null && account.mSaveLocation != null
                            && account.mSaveLocation.getName().equals(current.mSaveLocation.getName())) {
                        if (mListener != null) mListener.onAccountSelected(null);
                    }
                    Toast.makeText(requireContext(), "Account removed", Toast.LENGTH_SHORT).show();
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(mAdapter);
        updateEmptyState(accountList);

        // Bind Switch Account Button
        if (mBtnSwitchAccount != null) {
            mBtnSwitchAccount.setOnClickListener(v -> {
                if (mSelectedAccountInList != null) {
                    Accounts.setCurrent(mSelectedAccountInList);
                    if (mListener != null) mListener.onAccountSelected(mSelectedAccountInList);
                    Toast.makeText(requireContext(), "Switched to " + mSelectedAccountInList.username, Toast.LENGTH_SHORT).show();
                    dismiss();
                } else {
                    Toast.makeText(requireContext(), "Please select an account first", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Setup Account Type Selector Cards
        setupAuthTypeCards();

        // Bind Add Account Button
        if (mBtnAddAccount != null) {
            mBtnAddAccount.setOnClickListener(v -> handleAddAccount());
        }
    }

    private List<MinecraftAccount> loadAccounts() {
        List<MinecraftAccount> list = new ArrayList<>();
        try {
            Accounts loaded = Accounts.load();
            list.addAll(loaded.accounts);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void updateEmptyState(List<MinecraftAccount> list) {
        if (mEmptyState == null) return;
        mEmptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupAuthTypeCards() {
        // Default Selection
        updateAuthSelection(AuthType.MICROSOFT);

        if (mCardMs != null) {
            mCardMs.setOnClickListener(v -> updateAuthSelection(AuthType.MICROSOFT));
        }
        if (mCardMojang != null) {
            mCardMojang.setOnClickListener(v -> updateAuthSelection(AuthType.ELY_BY)); // map Mojang to alternate/OAuth or warn
        }
        if (mCardLocal != null) {
            mCardLocal.setOnClickListener(v -> updateAuthSelection(AuthType.LOCAL));
        }
    }

    private void updateAuthSelection(AuthType type) {
        mSelectedAuthType = type;

        if (mCardMs != null) mCardMs.setSelected(type == AuthType.MICROSOFT);
        if (mCardMojang != null) mCardMojang.setSelected(type == AuthType.ELY_BY);
        if (mCardLocal != null) mCardLocal.setSelected(type == AuthType.LOCAL);

        // Control username field visibility/access
        if (mInputUsername != null) {
            if (type == AuthType.LOCAL) {
                mInputUsername.setEnabled(true);
                mInputUsername.setAlpha(1.0f);
            } else {
                mInputUsername.setEnabled(false);
                mInputUsername.setAlpha(0.4f);
                mInputUsername.setText("");
            }
        }
        if (mErrorText != null) mErrorText.setVisibility(View.GONE);
    }

    private void handleAddAccount() {
        if (mSelectedAuthType == AuthType.MICROSOFT) {
            dismiss();
            Tools.swapFragment(requireActivity(),
                    MicrosoftLoginFragment.class,
                    MicrosoftLoginFragment.TAG, null);
            return;
        }

        if (mSelectedAuthType == AuthType.ELY_BY) {
            dismiss();
            Tools.swapFragment(requireActivity(),
                    ElyByLoginFragment.class,
                    ElyByLoginFragment.TAG, null);
            return;
        }

        // Local Profile creation
        if (mInputUsername == null) return;
        String username = mInputUsername.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            showError("Username cannot be empty");
            return;
        }
        if (username.length() < 3) {
            showError("Username must be at least 3 characters");
            return;
        }
        if (username.length() > 16) {
            showError("Username must be 16 characters or less");
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            showError("Only letters, numbers and _ allowed");
            return;
        }

        if (mErrorText != null) mErrorText.setVisibility(View.GONE);

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
            if (mListener != null) mListener.onAccountSelected(account);
            Toast.makeText(requireContext(), "Account '" + finalUsername + "' created!", Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (Exception e) {
            showError("Failed to create account: " + e.getMessage());
        }
    }

    private void showError(String message) {
        if (mErrorText == null) return;
        mErrorText.setText(message);
        mErrorText.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────────
    static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        interface OnClick { void onClick(MinecraftAccount account); }

        private final List<MinecraftAccount> mList;
        private final MinecraftAccount mCurrentActive;
        private MinecraftAccount mSelectedAccount;
        private final OnClick mOnSelect;
        private final OnClick mOnDelete;

        AccountAdapter(List<MinecraftAccount> list, MinecraftAccount currentActive,
                       MinecraftAccount selectedAccount, OnClick onSelect, OnClick onDelete) {
            mList = list;
            mCurrentActive = currentActive;
            mSelectedAccount = selectedAccount;
            mOnSelect = onSelect;
            mOnDelete = onDelete;
        }

        public void setSelectedAccount(MinecraftAccount account) {
            mSelectedAccount = account;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MinecraftAccount acc = mList.get(position);
            h.username.setText(acc.username);

            // Account type label
            String typeLabel = "Local";
            if (acc.authType != null) {
                switch (acc.authType) {
                    case MICROSOFT: typeLabel = "Microsoft"; break;
                    case ELY_BY:    typeLabel = "Craftyn";   break;
                    default:        typeLabel = "Local";     break;
                }
            }
            h.type.setText(typeLabel);

            // Highlight if selected in the list
            boolean isListSelected = mSelectedAccount != null && mSelectedAccount.mSaveLocation != null
                    && acc.mSaveLocation != null
                    && mSelectedAccount.mSaveLocation.getName().equals(acc.mSaveLocation.getName());

            if (isListSelected) {
                h.itemView.setSelected(true);
                h.itemView.setBackgroundResource(R.drawable.premium_auth_type_card_bg);
                h.itemView.setSelected(true);
            } else {
                h.itemView.setSelected(false);
                h.itemView.setBackgroundResource(R.drawable.premium_glass_black_bg);
            }

            // Check status dot and label (Red for active or Local)
            boolean isCurrentActive = mCurrentActive != null && mCurrentActive.mSaveLocation != null
                    && acc.mSaveLocation != null
                    && mCurrentActive.mSaveLocation.getName().equals(acc.mSaveLocation.getName());

            if (isCurrentActive) {
                h.statusText.setText("Active");
                h.statusText.setTextColor(Color.parseColor("#FF4D4D"));
                h.statusDot.setBackgroundColor(Color.parseColor("#FF4D4D"));
            } else {
                h.statusText.setText(typeLabel);
                h.statusText.setTextColor(Color.parseColor("#80FFFFFF"));
                h.statusDot.setBackgroundColor(Color.parseColor("#80FFFFFF"));
            }

            h.itemView.setOnClickListener(v -> mOnSelect.onClick(acc));

            // Delete action (Popup Menu)
            h.deleteBtn.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), h.deleteBtn);
                popup.getMenu().add("Delete");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Delete".equals(item.getTitle())) {
                        mOnDelete.onClick(acc);
                    }
                    return true;
                });
                popup.show();
            });
        }

        @Override public int getItemCount() { return mList.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView username, type, statusText;
            View statusDot, deleteBtn;
            VH(@NonNull View v) {
                super(v);
                username   = v.findViewById(R.id.account_username);
                type       = v.findViewById(R.id.account_type);
                statusText = v.findViewById(R.id.account_status_text);
                statusDot  = v.findViewById(R.id.account_status_dot);
                deleteBtn  = v.findViewById(R.id.account_delete_btn);
            }
        }
    }
}
