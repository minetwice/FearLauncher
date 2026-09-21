import base64, hashlib, os

b64 = open('scripts/fear_bg_full.b64').read()
jpg = base64.b64decode(b64)
assert hashlib.sha256(jpg).hexdigest() == '12653b7b31f5590778eb54f8acdf2f1240894dc477a704f4e136fc1f091a540a', 'bg sha mismatch'
os.makedirs('app_pojavlauncher/src/main/res/drawable-nodpi', exist_ok=True)
open('app_pojavlauncher/src/main/res/drawable-nodpi/fear_bg_full.jpg', 'wb').write(jpg)
os.remove('scripts/fear_bg_full.b64')

B = 'app_pojavlauncher/src/main/res/layout/fragment_launcher.xml'
J = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'

t = open(B, encoding='utf-8').read()
old_bg = '''    <com.kdt.mcgui.BackgroundAnimationView
        android:id="@+id/background_animation_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />'''
new_bg = '''    <ImageView
        android:id="@+id/background_animation_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:src="@drawable/fear_bg_full"
        android:contentDescription="@null" />'''
assert t.count(old_bg) == 1
t = t.replace(old_bg, new_bg)

old_dc = '''        <ImageButton
            android:id="@+id/social_discord_btn"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:src="@drawable/ic_discord"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:alpha="0.9"
            android:layout_marginEnd="12dp" />'''
new_dc = '''        <FrameLayout
            android:layout_width="34dp"
            android:layout_height="34dp"
            android:background="@drawable/premium_glass_black_bg"
            android:layout_marginEnd="10dp">
            <ImageButton
                android:id="@+id/social_discord_btn"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:src="@drawable/ic_discord"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:padding="5dp"
                android:scaleType="fitCenter" />
        </FrameLayout>'''
assert t.count(old_dc) == 1
t = t.replace(old_dc, new_dc)

old_tg = '''        <ImageButton
            android:id="@+id/social_telegram_btn"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:src="@drawable/ic_telegram"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:alpha="0.9"
            android:layout_marginEnd="12dp" />'''
new_yt = '''        <FrameLayout
            android:layout_width="34dp"
            android:layout_height="34dp"
            android:background="@drawable/premium_glass_black_bg"
            android:layout_marginEnd="10dp">
            <ImageButton
                android:id="@+id/social_youtube_btn"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:src="@drawable/ic_youtube_logo"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:padding="6dp"
                android:scaleType="fitCenter" />
        </FrameLayout>'''
assert t.count(old_tg) == 1
t = t.replace(old_tg, new_yt)

i = t.index('android:id="@+id/tray_downloads_btn"')
e = t.index('/>', i)
block = t[i:e]
assert 'ic_px_file_dl' in block
t = t[:i] + block.replace('ic_px_file_dl', 'ic_download') + t[e:]

open(B, 'w', encoding='utf-8', newline='').write(t)
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(t)

j = open(J, encoding='utf-8').read()
old_a = '''        com.kdt.mcgui.BackgroundAnimationView animBgView = view.findViewById(R.id.background_animation_view);
        if (animBgView != null) {
            animBgView.setAnimationType(bgAnimType);
            animBgView.setThemeColors(primaryColor, secondaryColor);
        }'''
new_a = '''        View animBgHolder = view.findViewById(R.id.background_animation_view);
        if (animBgHolder instanceof com.kdt.mcgui.BackgroundAnimationView) {
            com.kdt.mcgui.BackgroundAnimationView animBgView = (com.kdt.mcgui.BackgroundAnimationView) animBgHolder;
            animBgView.setAnimationType(bgAnimType);
            animBgView.setThemeColors(primaryColor, secondaryColor);
        }'''
assert j.count(old_a) == 1
j = j.replace(old_a, new_a)

old_b = 'R.id.social_telegram_btn'
assert j.count(old_b) == 1
j = j.replace(old_b, 'R.id.social_youtube_btn')

open(J, 'w', encoding='utf-8', newline='').write(j)
import xml.etree.ElementTree as ET
ET.parse(B)
print('MC15 OK')
