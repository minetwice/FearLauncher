package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import git.artdeell.mojo.R;

public class AdvancementsUtil {

    public static void showAdvancement(Activity activity, String title, String message) {
        if (activity == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) return;

            View advView = LayoutInflater.from(activity).inflate(R.layout.view_advancement_notification, rootView, false);
            TextView titleView = advView.findViewById(R.id.adv_title);
            TextView messageView = advView.findViewById(R.id.adv_message);

            titleView.setText(title);
            messageView.setText(message);

            rootView.addView(advView);

            // Initial state: hidden above the screen
            advView.setTranslationY(-500f);

            // Slide Down
            advView.animate()
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Stay for a while then Slide Up
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (advView.getParent() != null) {
                            advView.animate()
                                .translationY(-500f)
                                .setDuration(800)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .withEndAction(() -> rootView.removeView(advView))
                                .start();
                        }
                    }, 4000);
                })
                .start();
        });
    }
}
