package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import com.fearlauncher.fear.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.util.ArrayList;
import java.util.List;

/**
 * Accounts in one place.
 *
 * Your existing accounts are listed on the left, each with its head and name, and
 * the one in use is marked. Tapping one switches to it. The last entry adds a new
 * account, which is what reveals the method rail and the sign-in pane on the right.
 */
public class FearAuthFragment extends Fragment {

    public static final String TAG = "FearAuthFragment";

    private static final String CRAFTYN_SITE = "https://craftynmc.onrender.com/";

    private View mOpenSite;
    private LinearLayout mAccountsList;
    private View mNewContainer;
    private ImageView mCurrentHead;
    private TextView mCurrentName;
    private String mMethod = "craftyn";

    public FearAuthFragment() {
        super(R.layout.fragment_fear_auth);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAccountsList = view.findViewById(R.id.auth_accounts_list);
        mNewContainer = view.findViewById(R.id.auth_new_container);
        mCurrentHead = view.findViewById(R.id.auth_current_head);
        mCurrentName = view.findViewById(R.id.auth_current_name);

        mOpenSite = view.findViewById(R.id.auth_open_site);
        if (mOpenSite != null) {
            mOpenSite.setOnClickListener(v -> {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                net.kdt.pojavlaunch.SoundManager.playClick();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CRAFTYN_SITE)));
                } catch (Exception e) {
                    Toast.makeText(requireContext(), CRAFTYN_SITE, Toast.LENGTH_LONG).show();
                }
            });
        }

        bindMethod(view, R.id.auth_method_craftyn, "craftyn");
        bindMethod(view, R.id.auth_method_mojang, "mojang");
        bindMethod(view, R.id.auth_method_local, "local");

        rebuildAccounts();
        showMethod("craftyn");
    }

    /** Rebuilds the left column: one row per account, then the add-new row. */
    private void rebuildAccounts() {
        if (mAccountsList == null) return;
        mAccountsList.removeAllViews();

        List<MinecraftAccount> accounts = new ArrayList<>();
        MinecraftAccount current = null;
        try {
            Accounts loaded = Accounts.load();
            accounts.addAll(loaded.accounts);
            current = Accounts.getCurrent();
        } catch (Exception ignored) { }

        refreshHeader(current);

        for (MinecraftAccount account : accounts) {
            boolean selected = current != null
                    && current.mSaveLocation != null
                    && current.mSaveLocation.equals(account.mSaveLocation);
            mAccountsList.addView(accountRow(account, selected));
        }
        if (accounts.isEmpty()) {
            mAccountsList.addView(hint("No accounts yet."));
        }
        mAccountsList.addView(addNewRow());
    }

    private void refreshHeader(MinecraftAccount current) {
        if (mCurrentName != null) {
            mCurrentName.setText(current != null ? current.username : "No account selected");
        }
        if (mCurrentHead != null) {
            Bitmap face = null;
            try {
                if (current != null) face = current.getSkinFace();
            } catch (Exception ignored) { }
            if (face != null) mCurrentHead.setImageBitmap(face);
            else mCurrentHead.setImageResource(R.drawable.ic_app_logo);
        }
    }

    private View accountRow(final MinecraftAccount account, boolean selected) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(6);
        row.setLayoutParams(rp);
        row.setBackgroundResource(selected ? R.drawable.fear_tray_row_active
                : R.drawable.fear_tray_row_bg);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setElevation(dp(3));

        ImageView head = new ImageView(requireContext());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(26), dp(26));
        hp.setMarginEnd(dp(10));
        head.setLayoutParams(hp);
        head.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap face = null;
        try {
            face = account.getSkinFace();
        } catch (Exception ignored) { }
        if (face != null) head.setImageBitmap(face);
        else head.setImageResource(R.drawable.ic_app_logo);

        TextView name = new TextView(requireContext());
        name.setText(account.username != null ? account.username : "Account");
        name.setTextColor(selected ? Color.BLACK : Color.WHITE);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(np);

        row.addView(head);
        row.addView(name);
        row.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            Accounts.setCurrent(account);
            ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
            rebuildAccounts();
            if (mNewContainer != null) mNewContainer.setVisibility(View.GONE);
        });
        return row;
    }

    private View addNewRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(4);
        row.setLayoutParams(rp);
        row.setBackgroundResource(R.drawable.fear_tray_row_bg);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setElevation(dp(3));

        TextView label = new TextView(requireContext());
        label.setText("+  NEW ACCOUNT");
        label.setTextColor(Color.WHITE);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label);

        row.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            if (mNewContainer != null) mNewContainer.setVisibility(View.VISIBLE);
            showMethod(mMethod);
        });
        return row;
    }

    private TextView hint(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(0x80FFFFFF);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f);
        tv.setPadding(dp(4), dp(2), dp(4), dp(6));
        return tv;
    }

    private void bindMethod(View root, int buttonId, String method) {
        View b = root.findViewById(buttonId);
        if (b == null) return;
        b.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            showMethod(method);
        });
    }

    private void showMethod(String method) {
        mMethod = method;
        styleRail();

        if (mOpenSite != null) {
            mOpenSite.setVisibility("craftyn".equals(method) ? View.VISIBLE : View.GONE);
        }

        if (ProgressKeeper.hasProgressKey(ProgressLayout.AUTHENTICATE)) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }

        Fragment pane;
        String tag;
        if ("mojang".equals(method)) {
            pane = new MicrosoftLoginFragment();
            tag = MicrosoftLoginFragment.TAG;
        } else if ("local".equals(method)) {
            pane = new LocalLoginFragment();
            tag = LocalLoginFragment.TAG;
        } else {
            pane = new CraftynLoginFragment();
            tag = CraftynLoginFragment.TAG;
        }
        getChildFragmentManager().beginTransaction()
                .replace(R.id.auth_pane, pane, tag)
                .commit();
    }

    private void styleRail() {
        View root = getView();
        if (root == null) return;
        int[] ids = {R.id.auth_method_craftyn, R.id.auth_method_mojang, R.id.auth_method_local};
        String[] keys = {"craftyn", "mojang", "local"};
        for (int i = 0; i < ids.length; i++) {
            View b = root.findViewById(ids[i]);
            if (!(b instanceof TextView)) continue;
            boolean on = mMethod.equals(keys[i]);
            ((TextView) b).setTextColor(on ? Color.BLACK : Color.WHITE);
            b.setBackgroundResource(on ? R.drawable.premium_button_bg
                    : R.drawable.premium_glass_black_bg);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
