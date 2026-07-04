package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Transparent background so the activity background shows through
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setPadding(32, 32, 32, 32);
            recyclerView.setClipToPadding(false);
            // No divider needed for the card style
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getListView() != null) {
            getListView().post(this::applyBackgroundToAllPreferences);
        }
    }

    private void applyBackgroundToAllPreferences() {
        RecyclerView recyclerView = getListView();
        if (recyclerView == null) return;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            applyBackgroundToView(child);
        }
    }

    private void applyBackgroundToView(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // Check if it's a preference item (usually has a title)
            if (group.findViewById(android.R.id.title) != null) {
                view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.preference_background_selector));
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                if (params != null) {
                    params.setMargins(0, 12, 0, 12);
                    view.setLayoutParams(params);
                }
            } else {
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyBackgroundToView(group.getChildAt(i));
                }
            }
        }
    }
}
