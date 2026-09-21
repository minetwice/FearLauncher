B = 'app_pojavlauncher/src/main/res/'
L = B + 'layout/'
J = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'

def swap(t):
    t = t.replace('fear_skeleton_bg', 'theme_bg').replace('fear_bg_dark', 'theme_bg').replace('premium_gradient_bg', 'theme_bg')
    t = t.replace('crimson_button_bg', 'theme_button_bg').replace('premium_button_bg', 'theme_button_bg')
    t = t.replace('premium_play_button_bg', 'theme_button_bg').replace('premium_edit_bg', 'theme_button_bg')
    t = t.replace('#DC143C', '#4D9BFF').replace('#FF4D6D', '#A855F7').replace('#FF003C', '#4D9BFF').replace('#7A0A18', '#1E3A8A')
    return t

layouts = ['fragment_launcher.xml','fragment_local_login.xml','fragment_craftyn_login.xml',
 'fragment_select_auth_method.xml','splash_overlay_layout.xml','fragment_mod_search.xml',
 'view_mod.xml','view_mod_extended.xml','dialog_mod_filters.xml','fragment_mod_version_list.xml',
 'dialog_mod_detail_fullscreen.xml','fragment_instance_editor.xml','fragment_custom_settings.xml',
 'dialog_premium_settings_dashboard.xml','view_progress.xml']
for f in layouts:
    src = open(L + f, encoding='utf-8').read()
    open(L + f, 'w', encoding='utf-8', newline='').write(swap(src))

def cut_element(path, marker, tag):
    t = open(path, encoding='utf-8').read()
    i = t.index(marker)
    s = t.rindex(tag, 0, i)
    e = t.index('/>', i) + 2
    t = t[:s] + t[e:].lstrip('\n')
    open(path, 'w', encoding='utf-8', newline='').write(t)
    return t

def cut_java_block(t, start):
    i = t.index(start)
    j = t.index('{', i)
    depth = 0
    k = j
    while True:
        if t[k] == '{': depth += 1
        elif t[k] == '}':
            depth -= 1
            if depth == 0: break
        k += 1
    e = t.index('\n', k) + 1
    if t[e:e+1] == '\n': e += 1
    return t[:i] + t[e:]

# 2. remove tray_skin_btn from home layout
p = L + 'fragment_launcher.xml'
t = cut_element(p, 'tray_skin_btn', '<com.kdt.mcgui.LauncherMenuButton')
assert 'tray_skin_btn' not in t
open(B + 'layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(t)

# 3. remove dash_nav_skin from settings dashboard
t = cut_element(L + 'dialog_premium_settings_dashboard.xml', 'dash_nav_skin', '<com.kdt.mcgui.MineButton')
assert 'dash_nav_skin' not in t

# 4. MainMenuFragment surgery
t = open(J, encoding='utf-8').read()
t = t.replace('R.drawable.premium_button_bg', 'R.drawable.theme_button_bg')

# 4a. remove navSkin listener block + its comment
pos = t.index('navSkin.setOnClickListener')
cs = t.rindex('\n', 0, t.rindex('// SPLIT-PANE 3', 0, pos)) + 1
j = t.index('{', pos)
depth = 0
k = j
while True:
    if t[k] == '{': depth += 1
    elif t[k] == '}':
        depth -= 1
        if depth == 0: break
    k += 1
e = t.index('\n', k) + 1
if t[e:e+1] == '\n': e += 1
t = t[:cs] + t[e:]
assert 'navSkin.setOnClickListener' not in t

# 4b. remove navSkin declaration + reset refs + skin defaultTab branch
assert t.count('        Button navSkin = dialog.findViewById(R.id.dash_nav_skin);\n') == 1
t = t.replace('        Button navSkin = dialog.findViewById(R.id.dash_nav_skin);\n', '')
assert t.count('            navSkin.setBackgroundResource(R.drawable.premium_glass_black_bg);\n            navSkin.setTextColor(0xFFFFFFFF);\n') == 1
t = t.replace('            navSkin.setBackgroundResource(R.drawable.premium_glass_black_bg);\n            navSkin.setTextColor(0xFFFFFFFF);\n', '')
assert t.count('        if ("skin".equals(defaultTab)) {\n            navSkin.performClick();\n        } else if') == 1
t = t.replace('        if ("skin".equals(defaultTab)) {\n            navSkin.performClick();\n        } else if', '        if')

# 4c. remove tray_skin_btn handler block
t = cut_java_block(t, '        View traySkin = view.findViewById(R.id.tray_skin_btn);')
assert 'traySkin' not in t and 'navSkin' not in t

# 4d. bounce press animation for tray buttons + play button
anchor = '        // Sliding Drawer (settings_tray) bindings and trigger logic'
assert t.count(anchor) == 1
bounce = '        // Universal press bounce animation (FEAR UI)\n        android.view.View.OnTouchListener bounceFx = (v2, ev) -> {\n            if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {\n                v2.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();\n            } else if (ev.getAction() == android.view.MotionEvent.ACTION_UP || ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {\n                v2.animate().scaleX(1f).scaleY(1f).setDuration(170)\n                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();\n            }\n            return false;\n        };\n        View trayScroller = view.findViewById(R.id.tray_container);\n        if (trayScroller instanceof android.view.ViewGroup) {\n            android.view.ViewGroup trayVg = (android.view.ViewGroup) trayScroller;\n            for (int bi = 0; bi < trayVg.getChildCount(); bi++) {\n                trayVg.getChildAt(bi).setOnTouchListener(bounceFx);\n            }\n        }\n        android.view.View playFx = view.findViewById(R.id.play_button);\n        if (playFx != null) playFx.setOnTouchListener(bounceFx);\n\n'
t = t.replace(anchor, bounce + anchor)
open(J, 'w', encoding='utf-8', newline='').write(t)

# 5. smoother fragment transitions
fx = '<?xml version="1.0" encoding="utf-8"?>\n<set xmlns:android="http://schemas.android.com/apk/res/android"\n    android:ordering="together">\n    <objectAnimator\n        android:propertyName="alpha"\n        android:duration="300"\n        android:valueFrom="0.0"\n        android:valueTo="1.0"\n        android:interpolator="@android:interpolator/decelerate_quint" />\n    <objectAnimator\n        android:propertyName="translationY"\n        android:duration="300"\n        android:valueFrom="80"\n        android:valueTo="0"\n        android:valueType="floatType"\n        android:interpolator="@android:interpolator/decelerate_quint" />\n    <objectAnimator\n        android:propertyName="scaleX"\n        android:duration="300"\n        android:valueFrom="0.96"\n        android:valueTo="1.0"\n        android:interpolator="@android:interpolator/decelerate_quint" />\n    <objectAnimator\n        android:propertyName="scaleY"\n        android:duration="300"\n        android:valueFrom="0.96"\n        android:valueTo="1.0"\n        android:interpolator="@android:interpolator/decelerate_quint" />\n</set>\n'
fxout = '<?xml version="1.0" encoding="utf-8"?>\n<set xmlns:android="http://schemas.android.com/apk/res/android"\n    android:ordering="together">\n    <objectAnimator\n        android:propertyName="alpha"\n        android:duration="240"\n        android:valueFrom="1.0"\n        android:valueTo="0.0"\n        android:interpolator="@android:interpolator/accelerate_quint" />\n    <objectAnimator\n        android:propertyName="translationY"\n        android:duration="240"\n        android:valueFrom="0"\n        android:valueTo="40"\n        android:valueType="floatType"\n        android:interpolator="@android:interpolator/accelerate_quint" />\n</set>\n'
open(B + 'animator/fragment_enter.xml', 'w', encoding='utf-8', newline='').write(fx)
open(B + 'animator/fragment_pop_enter.xml', 'w', encoding='utf-8', newline='').write(fx)
open(B + 'animator/fragment_exit.xml', 'w', encoding='utf-8', newline='').write(fxout)
open(B + 'animator/fragment_pop_exit.xml', 'w', encoding='utf-8', newline='').write(fxout)

# 6. delete obsolete drawables
import os
for d in ['fear_bg_dark.xml', 'fear_skeleton_bg.xml', 'crimson_button_bg.xml']:
    fp = B + 'drawable/' + d
    if os.path.exists(fp):
        os.remove(fp)
print('MC7 OK')
