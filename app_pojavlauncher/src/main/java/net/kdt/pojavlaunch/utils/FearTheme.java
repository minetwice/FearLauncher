package net.kdt.pojavlaunch.utils;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The single source of the red <-> blue accent cycle.
 *
 * Every screen reads its colour from here instead of running its own animation,
 * so the whole launcher shifts together and no screen is left out of step with
 * the others. The cycle is a 6 second triangle wave between the two accents.
 */
public final class FearTheme {
    public static final int RED  = 0xFFFF2B3A;
    public static final int BLUE = 0xFF2B7BE0;

    public interface Listener { void onFearAccent(int colour); }

    private static final ArgbEvaluator sEvaluator = new ArgbEvaluator();
    private static final List<Listener> sListeners = new ArrayList<>();
    private static ValueAnimator sAnimator;
    private static int sAccent = RED;

    private FearTheme() {}

    public static int accent() { return sAccent; }

    public static void register(Listener listener) {
        if (!sListeners.contains(listener)) sListeners.add(listener);
        listener.onFearAccent(sAccent);
        ensureRunning();
    }

    public static void unregister(Listener listener) {
        sListeners.remove(listener);
        if (sListeners.isEmpty() && sAnimator != null) {
            sAnimator.cancel();
            sAnimator = null;
        }
    }

    private static void ensureRunning() {
        if (sAnimator != null) return;
        sAnimator = ValueAnimator.ofFloat(0f, 1f);
        sAnimator.setDuration(6000);
        sAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sAnimator.setInterpolator(new LinearInterpolator());
        sAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            float k = t < 0.5f ? t * 2f : (1f - t) * 2f;   // triangle wave
            sAccent = (int) sEvaluator.evaluate(k, RED, BLUE);
            for (int i = sListeners.size() - 1; i >= 0; i--) {
                sListeners.get(i).onFearAccent(sAccent);
            }
        });
        sAnimator.start();
    }
}
