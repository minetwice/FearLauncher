# 1. Instance editor: glass cards, glass inputs, gradient save button
p = 'app_pojavlauncher/src/main/res/layout/fragment_instance_editor.xml'
t = open(p, encoding='utf-8').read()
n = t.count('@drawable/theme_bg')
assert n == 20, n
t = t.replace('@drawable/theme_bg', '@drawable/premium_glass_black_bg')
i = t.index('android:background="@drawable/premium_glass_black_bg"')
t = t[:i] + 'android:background="@drawable/theme_bg"' + t[i+len('android:background="@drawable/premium_glass_black_bg"'):]
assert t.count('@drawable/theme_bg') == 1
i = t.index('android:id="@+id/vprof_editor_save_button"')
e = t.index('/>', i)
block = t[i:e]
assert block.count('android:background="@drawable/premium_glass_black_bg"') == 1
t = t[:i] + block.replace('android:background="@drawable/premium_glass_black_bg"', 'android:background="@drawable/theme_button_bg"') + t[e:]
open(p, 'w', encoding='utf-8', newline='').write(t)
print('editor OK')

# 2. Mod search: search input -> glass (not a solid button)
p = 'app_pojavlauncher/src/main/res/layout/fragment_mod_search.xml'
t = open(p, encoding='utf-8').read()
i = t.index('android:id="@+id/search_mod_edittext"')
e = t.index('/>', i)
block = t[i:e]
if 'theme_button_bg' in block:
    t = t[:i] + block.replace('theme_button_bg', 'premium_glass_black_bg') + t[e:]
    print('search input -> glass')
open(p, 'w', encoding='utf-8', newline='').write(t)
print('MC11 OK')
