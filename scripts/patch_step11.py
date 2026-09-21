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
# recorder dashboard + player: black bg, crimson accents/buttons, white text
patch(B+'dialog_recorder_dashboard.xml', [('@drawable/premium_gradient_bg','@drawable/fear_bg_dark',1)], replace_all=[('#FF003C','#DC143C'),('@drawable/premium_button_bg','@drawable/crimson_button_bg')])
patch(B+'dialog_recorder_player.xml', [('@drawable/premium_button_bg','@drawable/crimson_button_bg',2)], replace_all=[('#FF003C','#DC143C'),('android:textColor="#000000"','android:textColor="#FFFFFF"')])
# login forms: black bg + crimson accents
patch(B+'fragment_craftyn_login.xml', [('@drawable/premium_gradient_bg','@drawable/fear_bg_dark',1)], replace_all=[('#FF003C','#DC143C')])
patch(B+'fragment_local_login.xml', [('@drawable/premium_gradient_bg','@drawable/fear_bg_dark',1)])
print('v11 login + recorder crimson pass OK')
