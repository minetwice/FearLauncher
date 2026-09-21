import os

def patch(path, pairs, replace_all=None):
    t = open(path, encoding='utf-8').read()
    for old, new, cnt in pairs:
        c = t.count(old)
        assert c == cnt, '%s: anchor count %d != %d for %r' % (path, c, cnt, old[:60])
        t = t.replace(old, new)
    if replace_all:
        for old, new in replace_all:
            t = t.replace(old, new)
    open(path, 'w', encoding='utf-8', newline='').write(t)

SPLASH_INCLUDE = '    <include layout="@layout/splash_overlay_layout" />'

fl = 'app_pojavlauncher/src/main/res/layout/fragment_launcher.xml'
patch(fl, [
 # 1. progress layout -> full-screen overlay (bottom floating chip bar removed)
 ('        android:id="@+id/progress_layout"\n        android:layout_width="wrap_content"\n        android:layout_height="wrap_content"\n        android:layout_marginTop="@dimen/_6sdp"\n        android:layout_marginEnd="@dimen/_10sdp"\n        android:visibility="gone"\n        app:layout_constraintEnd_toEndOf="parent"\n        app:layout_constraintTop_toBottomOf="@id/header_layout" />',
  '        android:id="@+id/progress_layout"\n        android:layout_width="0dp"\n        android:layout_height="0dp"\n        android:visibility="gone"\n        app:layout_constraintStart_toStartOf="parent"\n        app:layout_constraintEnd_toEndOf="parent"\n        app:layout_constraintTop_toTopOf="parent"\n        app:layout_constraintBottom_toBottomOf="parent" />', 1),
 # 2. instance selector bar: smaller, centered, a bit wider than play button
 ('            android:id="@+id/mc_version_spinner"\n            android:layout_width="match_parent"\n            android:layout_height="@dimen/_28sdp"',
  '            android:id="@+id/mc_version_spinner"\n            android:layout_width="170dp"\n            android:layout_height="@dimen/_26sdp"\n            android:layout_gravity="center_horizontal"', 1),
 # 3. new skull icon
 ('android:src="@drawable/skull_liquid_avd"', 'android:src="@drawable/ic_skull_new"', 1),
 # 4. tray narrower
 ('        android:id="@+id/settings_tray"\n        android:layout_width="274dp"',
  '        android:id="@+id/settings_tray"\n        android:layout_width="246dp"', 1),
 # 5. splash overlay include
 ('    <!-- ============ HAMBURGER SLIDE-OUT PANEL (TRIANGLE) ============ -->', SPLASH_INCLUDE + '\n\n    <!-- ============ HAMBURGER SLIDE-OUT PANEL (TRIANGLE) ============ -->', 1),
])

# 6. view_progress: hide the floating chip
patch('app_pojavlauncher/src/main/res/layout/view_progress.xml', [
 ('        android:id="@+id/progress_bar_status_root"\n        android:layout_width="@dimen/_46sdp"',
  '        android:id="@+id/progress_bar_status_root"\n        android:visibility="gone"\n        android:layout_width="@dimen/_46sdp"', 1),
])

# 7. premium settings dashboard dialog (account/downloads/settings/core menus): crimson pass
patch('app_pojavlauncher/src/main/res/layout/dialog_premium_settings_dashboard.xml', [
 ('@drawable/premium_gradient_bg', '@drawable/fear_bg_dark', 1),
], replace_all=[
 ('#FF003C', '#DC143C'),
 ('@drawable/premium_button_bg', '@drawable/crimson_button_bg'),
])

mm = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'
# 8. tray open: smooth hardware-accelerated property animation (no lag)
patch(mm, [
 ('                if (settingsTray.getVisibility() != View.VISIBLE) {\n                    settingsTray.setVisibility(View.VISIBLE);\n                    Animation slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_in);\n                    settingsTray.startAnimation(slideIn);\n                    bindPerformanceStats(view);',
  '                if (settingsTray.getVisibility() != View.VISIBLE) {\n                    settingsTray.setVisibility(View.VISIBLE);\n                    settingsTray.setLayerType(View.LAYER_TYPE_HARDWARE, null);\n                    settingsTray.animate()\n                            .translationX(0f).setDuration(240)\n                            .setInterpolator(new android.view.animation.DecelerateInterpolator())\n                            .withEndAction(() -> settingsTray.setLayerType(View.LAYER_TYPE_NONE, null))\n                            .start();\n                    bindPerformanceStats(view);', 1),
 # 9. splash trigger
 ('        // Sliding Drawer (settings_tray) bindings and trigger logic\n        View settingsTray = view.findViewById(R.id.settings_tray);',
  '        view.post(() -> playSplashIntro(view));\n\n        // Sliding Drawer (settings_tray) bindings and trigger logic\n        View settingsTray = view.findViewById(R.id.settings_tray);', 1),
 # 10. splash method + collapseTray smooth
 ('    private void collapseTray(View settingsTray) {\n        if (settingsTray != null && settingsTray.getVisibility() == View.VISIBLE) {\n            Animation slideOut = AnimationUtils.loadAnimation(requireContext(), R.anim.tray_slide_out);\n            slideOut.setAnimationListener(new Animation.AnimationListener() {\n                @Override\n                public void onAnimationStart(Animation animation) {}\n                @Override\n                public void onAnimationEnd(Animation animation) {\n                    settingsTray.setVisibility(View.GONE);\n                }\n                @Override\n                public void onAnimationRepeat(Animation animation) {}\n            });\n            settingsTray.startAnimation(slideOut);\n        }\n    }',
  '    private void collapseTray(View settingsTray) {\n        if (settingsTray != null && settingsTray.getVisibility() == View.VISIBLE) {\n            settingsTray.setLayerType(View.LAYER_TYPE_HARDWARE, null);\n            settingsTray.animate()\n                    .translationX(-settingsTray.getWidth() - 24)\n                    .setDuration(200)\n                    .setInterpolator(new android.view.animation.AccelerateInterpolator())\n                    .withEndAction(() -> {\n                        settingsTray.setVisibility(View.GONE);\n                        settingsTray.setLayerType(View.LAYER_TYPE_NONE, null);\n                    }).start();\n        }\n    }\n\n    private static boolean sSplashPlayed = false;\n\n    /** v9 splash intro: logo shards fly in and join, red ray flashes at the join, then fades out. */\n    private void playSplashIntro(View view) {\n        if (sSplashPlayed) return;\n        sSplashPlayed = true;\n        android.widget.FrameLayout overlay = view.findViewById(R.id.splash_overlay);\n        if (overlay == null) return;\n        overlay.setVisibility(View.VISIBLE);\n        overlay.setAlpha(1f);\n        overlay.bringToFront();\n        android.view.View logo = overlay.findViewById(R.id.splash_logo);\n        android.view.View beam = overlay.findViewById(R.id.splash_beam);\n        android.view.View title = overlay.findViewById(R.id.splash_title);\n        android.view.View stage = overlay.findViewById(R.id.splash_stage);\n        if (logo == null || beam == null || stage == null) return;\n        float cx = overlay.getWidth() / 2f;\n        float cy = overlay.getHeight() / 2f;\n        int[] ids = {R.id.splash_shard_1, R.id.splash_shard_2, R.id.splash_shard_3, R.id.splash_shard_4};\n        float[][] offs = {{-cx, -cy}, {cx, -cy}, {-cx, cy}, {cx, cy}};\n        logo.setAlpha(0f);\n        logo.setScaleX(0.6f);\n        logo.setScaleY(0.6f);\n        beam.setAlpha(0f);\n        beam.setScaleX(0.1f);\n        if (title != null) title.setAlpha(0f);\n        android.view.animation.DecelerateInterpolator dec = new android.view.animation.DecelerateInterpolator();\n        for (int i = 0; i < ids.length; i++) {\n            android.view.View shard = overlay.findViewById(ids[i]);\n            if (shard == null) continue;\n            shard.setTranslationX(offs[i][0]);\n            shard.setTranslationY(offs[i][1]);\n            shard.setAlpha(1f);\n            shard.animate()\n                    .translationX(0f).translationY(0f)\n                    .setDuration(1100)\n                    .setStartDelay(i * 90L)\n                    .setInterpolator(dec)\n                    .withEndAction(() -> shard.animate().alpha(0f).setDuration(200).start())\n                    .start();\n        }\n        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)\n                .setDuration(900).setStartDelay(850)\n                .setInterpolator(dec).start();\n        beam.animate().alpha(1f).scaleX(1f)\n                .setDuration(260).setStartDelay(1650)\n                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))\n                .withEndAction(() -> beam.animate().alpha(0f).setDuration(700).setStartDelay(750).start())\n                .start();\n        if (title != null) {\n            title.animate().alpha(1f).setDuration(500).setStartDelay(2050).start();\n        }\n        overlay.animate().alpha(0f).setDuration(800).setStartDelay(3850)\n                .withEndAction(() -> overlay.setVisibility(View.GONE)).start();\n    }', 1),
])

# 11. landscape = exact copy of portrait
src = open(fl, encoding='utf-8').read()
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(src)

print('v9 patch OK')
