import re, glob

# 1. JAVA: fix the master theme color default (PorterDuff paints every MineButton)
for jf in ['app_pojavlauncher/src/main/java/com/kdt/mcgui/MineButton.java',
           'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java']:
    t = open(jf, encoding='utf-8').read()
    n = t.count('0xFFFF003C')
    assert n >= 1, jf
    t = t.replace('0xFFFF003C', '0xFF4D9BFF')
    t = t.replace('launcher_theme_color_argb', 'launcher_theme_color_v2')
    open(jf, 'w', encoding='utf-8', newline='').write(t)
    print(jf.split('/')[-1], 'fixed', n, 'refs')

# 2. RES sweep: remap every red-family hex to the navy/blue/purple theme
MAP8 = {
 '#4DFF003C': '#664D9BFF', '#33FF003C': '#404D9BFF', '#40FF003C': '#404D9BFF',
 '#80FF003C': '#804D9BFF', '#20FF003C': '#294D9BFF', '#1AFF003C': '#264D9BFF',
 '#30FF003C': '#3D4D9BFF', '#B3FF003C': '#B34D9BFF', '#12FF003C': '#194D9BFF',
 '#4CFF003C': '#5C4D9BFF', '#FFFF003C': '#FF4D9BFF', '#40FF4D4D': '#407CB9FF',
 '#40FF0000': '#404D9BFF', '#26FF0000': '#264D9BFF',
 '#E60F0204': '#A60D1220', '#EB0F0204': '#B80D1220', '#CC1E1E1E': '#CC141830',
}
CORE = {
 'FF003C': '4D9BFF', 'FF4D4D': '7CB9FF', 'FF5252': '7CB9FF', 'FF3333': '7CB9FF',
 'FF3B30': '7CB9FF', '8E0812': '1E3A8A', '8E1116': '1E3A8A', 'D31A21': '3B82F6',
 'E31D24': '7C3AED', 'FF9500': 'A855F7', '0F0204': '0D1220',
}
def fix(t):
    for k, v in MAP8.items():
        t = t.replace(k, v).replace(k.lower(), v)
    def core_repl(m):
        h = m.group(0).upper()
        if len(h) == 9:
            a, c = h[:3], h[3:]
        else:
            a, c = '', h[1:]
        return (a + '#' if a else '#') + CORE.get(c, c)
    return re.sub(r'#[0-9A-Fa-f]{6}\b|#[0-9A-Fa-f]{8}\b', core_repl, t)

changed = 0
for f in glob.glob('app_pojavlauncher/src/main/res/layout*/**/*.xml', recursive=True) + \
         glob.glob('app_pojavlauncher/src/main/res/values*/**/*.xml', recursive=True) + \
         glob.glob('app_pojavlauncher/src/main/res/drawable*/**/*.xml', recursive=True):
    t = open(f, encoding='utf-8').read()
    t2 = fix(t)
    if t2 != t:
        open(f, 'w', encoding='utf-8', newline='').write(t2)
        changed += 1
print('res files recolored:', changed)

# 3. semantic colors back to green (post-sweep they would turn blue)
cf = 'app_pojavlauncher/src/main/res/values/colors.xml'
t = open(cf, encoding='utf-8').read()
t = re.sub(r'(<color name="mc_official_green">)[^<]+(</color>)', r'\g<1>#22C55E\g<2>', t)
t = re.sub(r'(<color name="status_success">)[^<]+(</color>)', r'\g<1>#34D399\g<2>', t)
open(cf, 'w', encoding='utf-8', newline='').write(t)

# 4. fix clipped COLLAPSE button: add end margin + constraint
pf = 'app_pojavlauncher/src/main/res/layout/dialog_premium_settings_dashboard.xml'
t = open(pf, encoding='utf-8').read()
old = 'android:text="COLLAPSE"'
assert t.count(old) == 1
i = t.index('android:id="@+id/dash_done_btn"')
e = t.index('/>', i)
block = t[i:e]
assert 'marginEnd' not in block
t = t.replace('            android:text="COLLAPSE"', '            android:layout_marginEnd="12dp"\n            android:text="COLLAPSE"')
if 'layout_constraintEnd_toEndOf' not in block:
    t = t.replace('app:layout_constraintTop_toTopOf="parent"\n            app:layout_constraintBottom_toBottomOf="parent"\n            app:layout_constraintStart_toEndOf="@id/dash_back_btn" />',
                  'app:layout_constraintTop_toTopOf="parent"\n            app:layout_constraintBottom_toBottomOf="parent"\n            app:layout_constraintStart_toEndOf="@id/dash_back_btn"\n            app:layout_constraintEnd_toEndOf="parent" />', 1)
open(pf, 'w', encoding='utf-8', newline='').write(t)
print('MC8 OK')
