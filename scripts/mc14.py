p = 'app_pojavlauncher/src/main/res/layout/fragment_launcher.xml'
t = open(p, encoding='utf-8').read()
assert 'UI Logic Dummies' not in t
assert t.count('splash_overlay_layout') == 0
anchor = '    <!-- ULTRA-PROFESSIONAL GLASS SIDEBAR TRAY'
assert t.count(anchor) == 1
add = '''    <!-- UI Logic Dummies -->
    <TextView android:id="@+id/account_name" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <TextView android:id="@+id/account_type_label" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <TextView android:id="@+id/version_text" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/account_section" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/edit_profile_button" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/tab_home" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/tab_installations" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/tab_skin" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />
    <View android:id="@+id/tab_settings_icon" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" />

    <include layout="@layout/splash_overlay_layout" />

'''
t = t.replace(anchor, add + anchor)
open(p, 'w', encoding='utf-8', newline='').write(t)
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(t)
import xml.etree.ElementTree as ET
ET.parse(p)
for rid in ['account_name','account_type_label','version_text','account_section','edit_profile_button','tab_home','tab_installations','tab_skin','tab_settings_icon']:
    assert ('@+id/' + rid) in t, rid
print('MC14 OK')
