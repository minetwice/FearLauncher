package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.FearToggle;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.FearTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The instance manager: every mod, resource pack and shaderpack in the selected
 * instance, each with an on/off switch.
 *
 * Switching a file off renames it with a .disabled suffix, which is how the game
 * is told to ignore it - loaders only pick up .jar, and the pack loaders only
 * pick up .zip. Switching it back on strips the suffix. Nothing is ever deleted.
 */
public class FearManagerFragment extends Fragment {

    public static final String TAG = "FearManagerFragment";

    private static final String DISABLED = ".disabled";

    private final List<FearToggle> mToggles = new ArrayList<>();
    private FearTheme.Listener mAccentListener;

    public FearManagerFragment() {
        super(R.layout.fragment_fear_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Instance instance = Instances.loadSelectedInstance();
        File gameDir = instance != null ? instance.getGameDirectory() : null;

        TextView instanceLabel = view.findViewById(R.id.manager_instance);
        if (instanceLabel != null) {
            String name = instance != null && instance.name != null ? instance.name : "no instance selected";
            instanceLabel.setText("INSTANCE: " + name.toUpperCase(Locale.US));
        }

        LinearLayout sections = view.findViewById(R.id.manager_sections);
        if (sections == null) return;

        if (gameDir == null) {
            sections.addView(note("NO INSTANCE SELECTED - PICK ONE ON THE HOME SCREEN FIRST."));
        } else {
            addSection(sections, gameDir, "mods", "MODS");
            addSection(sections, gameDir, "resourcepacks", "RESOURCE PACKS");
            addSection(sections, gameDir, "shaderpacks", "SHADERS");
        }

        // keep the pills in step with the launcher-wide colour cycle
        final View root = view;
        mAccentListener = colour -> {
            View stripe = root.findViewById(R.id.manager_stripe);
            if (stripe != null) {
                stripe.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colour));
            }
            for (FearToggle t : mToggles) t.invalidate();
        };
        FearTheme.register(mAccentListener);
    }

    @Override
    public void onDestroyView() {
        if (mAccentListener != null) {
            FearTheme.unregister(mAccentListener);
            mAccentListener = null;
        }
        mToggles.clear();
        super.onDestroyView();
    }

    private void addSection(LinearLayout parent, File gameDir, String folder, String label) {
        File dir = new File(gameDir, folder);
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        List<File> entries = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith(".")) entries.add(f);
            }
        }
        entries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        parent.addView(sectionHeader(label + "  (" + entries.size() + ")"));
        if (entries.isEmpty()) {
            parent.addView(note("nothing here yet"));
            return;
        }
        for (File f : entries) parent.addView(row(parent, f));
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(0, dp(10), 0, dp(6));
        return tv;
    }

    private TextView note(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(0x80FFFFFF);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
        tv.setPadding(dp(4), dp(2), dp(4), dp(6));
        return tv;
    }

    private View row(LinearLayout parent, File file) {
        final boolean enabled = !file.getName().endsWith(DISABLED);
        final String display = enabled
                ? file.getName()
                : file.getName().substring(0, file.getName().length() - DISABLED.length());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(6);
        row.setLayoutParams(rp);
        row.setBackgroundResource(R.drawable.fear_tray_item_bg);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setElevation(dp(3));

        TextView name = new TextView(requireContext());
        name.setText(display);
        name.setTextColor(enabled ? Color.WHITE : 0x80FFFFFF);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(np);

        FearToggle toggle = new FearToggle(requireContext());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(46), dp(26));
        tp.setMarginStart(dp(10));
        toggle.setLayoutParams(tp);
        toggle.setChecked(enabled, false);
        toggle.setOnCheckedChangeListener((t, checked) -> applyState(file, display, checked, name));
        mToggles.add(toggle);

        row.addView(name);
        row.addView(toggle);
        return row;
    }

    /** Renames the file to (or from) a .disabled suffix. Never deletes anything. */
    private void applyState(File file, String display, boolean enabled, TextView name) {
        File target = enabled
                ? new File(file.getParentFile(), display)
                : new File(file.getParentFile(), display + DISABLED);
        boolean ok = file.renameTo(target);
        if (!ok) {
            android.widget.Toast.makeText(requireContext(),
                    "Could not rename " + display, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (name != null) name.setTextColor(enabled ? Color.WHITE : 0x80FFFFFF);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
