sp = 'app_pojavlauncher/src/main/res/values/strings.xml'
s = open(sp, encoding='utf-8').read()
for k in ['notif_channel_id','tasks_ongoing','mcn_abort_title','mcn_exit_title','select_bta_version']:
    assert 'name="%s"' % k not in s, k
assert s.count('</resources>') == 1
add = ('    <string name="notif_channel_id" translatable="false">channel_id</string>\n'
 '    <string name="tasks_ongoing">Tasks are in progress, please wait</string>\n'
 '    <string name="mcn_abort_title">Application/Game aborted, check latestlog.txt for more information</string>\n'
 '    <string name="mcn_exit_title">Application/Game exited with code %d, check latestlog.txt for more details.</string>\n'
 '    <string name="select_bta_version">Select \\"Better than Adventure!\\" version</string>\n')
s = s.replace('</resources>', add + '</resources>')
open(sp, 'w', encoding='utf-8', newline='').write(s)
import xml.etree.ElementTree as ET
ET.parse(sp)
print('MC4 OK')
