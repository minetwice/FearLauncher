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

B = 'app_pojavlauncher/src/main/res/layout/'
# 1. home head: full visible - bigger, no scale-down, no clipping, elevated above overlays
patch(B+'fragment_launcher.xml', [
 ('        android:id="@+id/home_content_pane"',
  '        android:clipChildren="false"\n        android:clipToPadding="false"\n        android:id="@+id/home_content_pane"', 1),
 ('            android:layout_width="@dimen/_56sdp"\n            android:layout_height="@dimen/_56sdp"\n            android:layout_marginTop="@dimen/_8sdp"\n            android:scaleX="0.82"\n            android:scaleY="0.82"',
  '            android:layout_width="64dp"\n            android:layout_height="64dp"\n            android:layout_marginTop="@dimen/_10sdp"\n            android:scaleX="1.0"\n            android:scaleY="1.0"\n            android:elevation="12dp"', 1),
])
src = open(B+'fragment_launcher.xml', encoding='utf-8').read()
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(src)

# 2. splash SFX: tick at start + shine at beam flash
mm = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'
patch(mm, [
 ('        overlay.bringToFront();\n        android.view.View logo = overlay.findViewById(R.id.splash_logo);',
  '        overlay.bringToFront();\n        net.kdt.pojavlaunch.SoundManager.playClick();\n        android.view.View logo = overlay.findViewById(R.id.splash_logo);', 1),
 ('        beam.animate().alpha(1f).scaleX(1f)\n                .setDuration(260).setStartDelay(1650)',
  '        overlay.postDelayed(() -> net.kdt.pojavlaunch.SoundManager.playClick(), 1650);\n        beam.animate().alpha(1f).scaleX(1f)\n                .setDuration(260).setStartDelay(1650)', 1),
])

# 3. instance editor renewal: black glass cards, crimson buttons
patch(B+'fragment_instance_editor.xml', [], replace_all=[
 ('@drawable/premium_gradient_bg', '@drawable/premium_glass_black_bg'),
 ('@drawable/premium_button_bg', '@drawable/crimson_button_bg'),
])

# 4. core settings (renderer/controller/JVM): black bg + crimson accents
patch(B+'fragment_custom_settings.xml', [('@drawable/premium_gradient_bg','@drawable/fear_bg_dark',1)], replace_all=[('#FF003C','#DC143C')])
print('v12 OK')
