# 1. GameRunner: remove wrong-package imports (classes are in same package)
p = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'
t = open(p, encoding='utf-8').read()
for line in ['import git.artdeell.mojo.jvm.JavaRunner;\n', 'import git.artdeell.mojo.jvm.VMLoadException;\n']:
    assert t.count(line) == 1, line
    t = t.replace(line, '')
assert 'git.artdeell.mojo.jvm' not in t
open(p, 'w', encoding='utf-8', newline='').write(t)

# 2. add missing strings
sp = 'app_pojavlauncher/src/main/res/values/strings.xml'
s = open(sp, encoding='utf-8').read()
assert 'profiles_latest_release' not in s
assert s.count('</resources>') == 1
s = s.replace('</resources>', '    <string name="profiles_latest_release">Latest release</string>\n    <string name="profiles_latest_snapshot">Latest snapshot</string>\n</resources>')
open(sp, 'w', encoding='utf-8', newline='').write(s)
print('MC3 OK')
