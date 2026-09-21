p = 'app_pojavlauncher/src/main/res/layout/fragment_local_login.xml'
t = open(p, encoding='utf-8').read()
i = t.index('android:id="@+id/login_edit_email"')
e = t.index('/>', i)
block = t[i:e]
assert 'theme_button_bg' in block, 'email input not button-styled'
t = t[:i] + block.replace('theme_button_bg', 'premium_glass_black_bg') + t[e:]
i = t.index('FEAR ULTRA-CORE v4.0 [PRO]')
s = t.rindex('<TextView', 0, i)
e2 = t.index('/>', i)
block2 = t[s:e2]
if 'theme_button_bg' in block2:
    t = t[:s] + block2.replace('theme_button_bg', 'premium_glass_black_bg') + t[e2:]
    print('badge -> glass')
print('email input -> glass')
open(p, 'w', encoding='utf-8', newline='').write(t)
import xml.etree.ElementTree as ET
ET.parse(p)
print('MC12 OK')
