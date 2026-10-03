package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

/**
 * One screen for every way in.
 *
 * The method rail sits on the left - CraftynMC, Mojang, Local - and the matching
 * sign-in form loads into the pane beside it, so nothing is a separate page.
 * CraftynMC also carries an arrow that opens the account site in the browser.
 */
public class FearAuthFragment extends Fragment {

    public static final String TAG = "FearAuthFragment";

    private static final String CRAFTYN_SITE = "https://craftynmc.onrender.com/";

    private View mOpenSite;
    private String mMethod = "craftyn";

    public FearAuthFragment() {
        super(R.layout.fragment_fear_auth);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        showMethod("craftyn");
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
}
