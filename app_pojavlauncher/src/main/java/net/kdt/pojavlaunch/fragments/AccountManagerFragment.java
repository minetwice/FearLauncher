package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;

import java.util.ArrayList;
import java.util.List;

public class AccountManagerFragment extends BottomSheetDialogFragment {
    public static final String TAG = "AccountManagerFragment";

    private OnAccountSelectedListener mListener;
    private AccountAdapter mAdapter;
    private View mEmptyState;

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
        View btnAddAccount = view.findViewById(R.id.btn_add_account);

        // Load accounts
        List<MinecraftAccount> accountList = loadAccounts();
        final String currentAccountName;
        MinecraftAccount current = Accounts.getCurrent();
        currentAccountName = (current != null) ? current.mSaveLocation.getName() : "";

        mAdapter = new AccountAdapter(accountList, currentAccountName,
                // On select
                account -> {
                    Accounts.setCurrent(account);
                    if (mListener != null) mListener.onAccountSelected(account);
                    dismiss();
                },
                // On delete
                account -> {
                    Accounts.delete(account);
                    accountList.remove(account);
                    mAdapter.notifyDataSetChanged();
                    updateEmptyState(accountList);
                    // If deleted account was current, refresh parent
                    if (mListener != null && current != null
                            && account.mSaveLocation.getName().equals(currentAccountName)) {
                        mListener.onAccountSelected(null);
                    }
                    Toast.makeText(requireContext(), "Account removed", Toast.LENGTH_SHORT).show();
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(mAdapter);
        updateEmptyState(accountList);

        // Add account button → opens AddAccountFragment
        if (btnAddAccount != null) {
            btnAddAccount.setOnClickListener(v -> {
                dismiss();
                AddAccountFragment addSheet = new AddAccountFragment();
                addSheet.setOnAccountAddedListener(newAccount -> {
                    // After adding, set as current and notify parent
                    Accounts.setCurrent(newAccount);
                    if (mListener != null) mListener.onAccountSelected(newAccount);
                });
                addSheet.show(getParentFragmentManager(), AddAccountFragment.TAG);
            });
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

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────────
    static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        interface OnClick { void onClick(MinecraftAccount account); }

        private final List<MinecraftAccount> mList;
        private final String mCurrentName;
        private final OnClick mOnSelect;
        private final OnClick mOnDelete;

        AccountAdapter(List<MinecraftAccount> list, String currentName,
                       OnClick onSelect, OnClick onDelete) {
            mList = list;
            mCurrentName = currentName;
            mOnSelect = onSelect;
            mOnDelete = onDelete;
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
                    case ELY_BY:    typeLabel = "Ely.by";    break;
                    default:        typeLabel = "Local";     break;
                }
            }
            h.type.setText(typeLabel);

            // Show checkmark if this is current account
            boolean isSelected = acc.mSaveLocation != null
                    && acc.mSaveLocation.getName().equals(mCurrentName);
            h.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            h.activeDot.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> mOnSelect.onClick(acc));
            h.deleteBtn.setOnClickListener(v -> mOnDelete.onClick(acc));
        }

        @Override public int getItemCount() { return mList.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView username, type;
            View check, activeDot, deleteBtn;
            VH(@NonNull View v) {
                super(v);
                username  = v.findViewById(R.id.account_username);
                type      = v.findViewById(R.id.account_type);
                check     = v.findViewById(R.id.account_selected_check);
                activeDot = v.findViewById(R.id.account_active_dot);
                deleteBtn = v.findViewById(R.id.account_delete_btn);
            }
        }
    }
}
