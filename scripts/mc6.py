p = 'app_pojavlauncher/src/main/res/values/strings.xml'
t = open(p, encoding='utf-8').read()
old = 'You don' + chr(39) + 't seem to have an Xbox Live account.'
new = 'You don' + chr(92) + chr(39) + 't seem to have an Xbox Live account.'
assert t.count(old) == 1
open(p, 'w', encoding='utf-8', newline='').write(t.replace(old, new))
import xml.etree.ElementTree as ET
ET.parse(p)
print('MC6 OK')
