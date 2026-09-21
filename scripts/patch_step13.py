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
# 1. remove bottom live status strip (the irrational bottom dashboard)
patch(B+'fragment_launcher.xml', [
 ('''        <!-- Live status strip -->
        <TextView
            android:id="@+id/homepage_chat_bubble"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@drawable/home_news_card_bg"
            android:paddingHorizontal="10dp"
            android:paddingVertical="7dp"
            android:textColor="#CCFFFFFF"
            android:textSize="9sp"
            android:textAlignment="center"
            android:fontFamily="monospace"
            android:text="[SYSTEM] Boot sequence ready."
            android:layout_marginTop="@dimen/_12sdp" />
''', '', 1),
])
src = open(B+'fragment_launcher.xml', encoding='utf-8').read()
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(src)

# 2. SoundManager: REAL sound effects (soft tone ticks, never crash)
sm = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/SoundManager.java'
patch(sm, [
 ('''    public static void playClick() {
        // Fallback standard touch click played in the View layer
    }''',
 '''    private static android.media.ToneGenerator sTone;

    public static void playClick() {
        try {
            if (sTone == null) sTone = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 30);
            sTone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 60);
        } catch (Throwable ignored) {}
    }

    public static void playShine() {
        try {
            if (sTone == null) sTone = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 50);
            sTone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 160);
        } catch (Throwable ignored) {}
    }''', 1),
])

# 3. splash: shine sound at red ray flash
mm = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'
patch(mm, [
 ('overlay.postDelayed(() -> net.kdt.pojavlaunch.SoundManager.playClick(), 1650);',
  'overlay.postDelayed(() -> net.kdt.pojavlaunch.SoundManager.playShine(), 1650);', 1),
])

# 4. login: Zimoxy-style - crimson gradient login button
patch(B+'fragment_local_login.xml', [
 ('@drawable/premium_play_button_bg', '@drawable/crimson_button_bg', 1),
], replace_all=[
 ('android:text="Sign in"', 'android:text="LOGIN"'),
])
print('v13 OK')
