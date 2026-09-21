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

# mods search page: black bg, crimson glass buttons, white text on crimson
patch('app_pojavlauncher/src/main/res/layout/fragment_mod_search.xml', [
 ('@drawable/premium_gradient_bg', '@drawable/fear_bg_dark', 1),
], replace_all=[
 ('android:textColor="#000000"', 'android:textColor="#FFFFFF"'),
 ('@drawable/premium_button_bg', '@drawable/crimson_button_bg'),
])
# mod card: crimson glass keep, ensure description text readable
patch('app_pojavlauncher/src/main/res/layout/view_mod.xml', [])
patch('app_pojavlauncher/src/main/res/layout/view_mod_extended.xml', [])
print('v10 mods page crimson pass OK')
