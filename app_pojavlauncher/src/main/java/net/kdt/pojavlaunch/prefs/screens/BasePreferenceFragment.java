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

/**
 * Base class for all preference fragments.
 * Applies FearPreferenceTheme and ensures all items have gradient background.
 */
public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 🔥 Apply theme BEFORE inflating
        getActivity().setTheme(R.style.FearPreferenceTheme);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Background
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_app));

        // Silver divider
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            DividerItemDecoration divider = new DividerItemDecoration(requireContext(),
                    DividerItemDecoration.VERTICAL);
            divider.setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider_silver));
            recyclerView.addItemDecoration(divider);
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Apply gradient background to each preference item after list is ready
        getListView().post(this::applyBackgroundToAllPreferences);
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
        if (view instanceof LinearLayout) {
            view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.preference_background));
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(16, 8, 16, 8);
            view.setLayoutParams(params);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyBackgroundToView(group.getChildAt(i));
            }
        }
    }
}
