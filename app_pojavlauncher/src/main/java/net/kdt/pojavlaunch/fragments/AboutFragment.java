package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fearlauncher.fear.BuildConfig;
import com.fearlauncher.fear.R;

import net.kdt.pojavlaunch.Tools;

/**
 * The "About Us" dashboard.
 *
 * A short description of what the launcher is, a feature rundown drawn from what the launcher
 * actually ships today, and a credits list for the three people who build it. Every credit
 * link opens in the system browser through an ACTION_VIEW intent, the same way the account
 * screens open external pages.
 */
public class AboutFragment extends Fragment {

    public static final String TAG = "ABOUT_FRAGMENT";

    /** The team's social links. Kept here as constants, like FearAuthFragment does for its site. */
    private static final String URL_TWICEFEAR_YOUTUBE = "https://youtube.com/@twicefear3?si=yYK-ygcuwfRQqMUf";
    private static final String URL_TWICEFEAR_DISCORD = "https://discord.gg/b3uj4YPYAu";
    private static final String URL_HELLZIOR_YOUTUBE = "https://youtube.com/@hellzior01?si=dLi618PGNxfh2Fdg";
    private static final String URL_HELLZIOR_DISCORD = "https://discord.gg/yjuax9fh5w";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // BACK simply returns to whatever opened the About screen (the home tray).
        view.findViewById(R.id.about_back).setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            Tools.removeCurrentFragment(requireActivity());
        });

        // Version line under the title.
        ((TextView) view.findViewById(R.id.about_version))
                .setText(getString(R.string.about_version_format, BuildConfig.VERSION_NAME));

        // ---- feature rundown ------------------------------------------------------
        // Every row describes something the launcher really does today.
        LinearLayout features = view.findViewById(R.id.about_features);
        addFeature(features, R.string.about_feature_renderer_title, R.string.about_feature_renderer_desc);
        addFeature(features, R.string.about_feature_performance_title, R.string.about_feature_performance_desc);
        addFeature(features, R.string.about_feature_pvp_title, R.string.about_feature_pvp_desc);
        addFeature(features, R.string.about_feature_quality_title, R.string.about_feature_quality_desc);
        addFeature(features, R.string.about_feature_skin_title, R.string.about_feature_skin_desc);
        addFeature(features, R.string.about_feature_accounts_title, R.string.about_feature_accounts_desc);
        addFeature(features, R.string.about_feature_instances_title, R.string.about_feature_instances_desc);

        // ---- credits --------------------------------------------------------------
        // Its_crazy_plays has no public links, so that card carries no buttons.
        bindLink(view, R.id.credit_twicefear_youtube, URL_TWICEFEAR_YOUTUBE);
        bindLink(view, R.id.credit_twicefear_discord, URL_TWICEFEAR_DISCORD);
        bindLink(view, R.id.credit_hellzior_youtube, URL_HELLZIOR_YOUTUBE);
        bindLink(view, R.id.credit_hellzior_discord, URL_HELLZIOR_DISCORD);

        // ---- footer ---------------------------------------------------------------
        ((TextView) view.findViewById(R.id.about_footer))
                .setText(getString(R.string.about_footer, BuildConfig.VERSION_NAME));
    }

    /** Inflates one feature row (title + description) and appends it to the container. */
    private void addFeature(LinearLayout parent, int titleRes, int descRes) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_about_feature, parent, false);
        ((TextView) row.findViewById(R.id.about_feature_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.about_feature_desc)).setText(descRes);
        parent.addView(row);
    }

    /** Wires a button to open its URL in the system browser. */
    private void bindLink(View root, int id, String url) {
        View button = root.findViewById(id);
        if (button == null) return;
        button.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
