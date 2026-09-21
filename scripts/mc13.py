B = 'app_pojavlauncher/src/main/res/layout/'
J = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'

HEAD = '''<?xml version="1.0" encoding="utf-8"?>
<!-- FEAR LAUNCHER - GIGACAT-style fresh home -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipChildren="false"
    android:clipToPadding="false"
    android:background="@drawable/theme_bg">

    <com.kdt.mcgui.BackgroundAnimationView
        android:id="@+id/background_animation_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- == TOP BAR == -->
    <LinearLayout
        android:id="@+id/header_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="@dimen/_12sdp"
        android:paddingVertical="@dimen/_8sdp"
        app:layout_constraintTop_toTopOf="parent">

        <ImageButton
            android:id="@+id/hamburger_menu_icon"
            android:layout_width="30dp"
            android:layout_height="30dp"
            android:src="@drawable/ic_skull_new"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:layout_marginEnd="10dp" />

        <FrameLayout
            android:layout_width="38dp"
            android:layout_height="38dp"
            android:background="@drawable/premium_glass_black_bg"
            android:padding="5dp">
            <ImageView
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:src="@drawable/ic_app_logo"
                android:contentDescription="@null" />
        </FrameLayout>

        <TextView
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:text="FEAR LAUNCHER"
            android:textColor="#FFFFFF"
            android:textSize="@dimen/_12ssp"
            android:textStyle="bold"
            android:letterSpacing="0.1"
            android:layout_marginStart="10dp" />

        <ImageButton
            android:id="@+id/social_discord_btn"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:src="@drawable/ic_discord"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:alpha="0.9"
            android:layout_marginEnd="12dp" />

        <ImageButton
            android:id="@+id/social_telegram_btn"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:src="@drawable/ic_telegram"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:alpha="0.9"
            android:layout_marginEnd="12dp" />

        <ImageButton
            android:id="@+id/header_notification_btn"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:src="@drawable/ic_px_bell"
            android:background="?attr/selectableItemBackgroundBorderless"
            app:tint="#4D9BFF"
            android:layout_marginEnd="12dp" />

        <FrameLayout
            android:layout_width="42dp"
            android:layout_height="42dp">
            <androidx.cardview.widget.CardView
                android:id="@+id/header_avatar_card"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:cardCornerRadius="21dp"
                app:cardBackgroundColor="#3313162C"
                app:cardElevation="0dp">
                <com.kdt.mcgui.MinecraftSkinView
                    android:id="@+id/homepage_skin_head"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </androidx.cardview.widget.CardView>
            <ImageButton
                android:id="@+id/header_account_hub_btn"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@null"
                android:contentDescription="@null" />
        </FrameLayout>

    </LinearLayout>

    <!-- == MAIN STACK == -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:scrollbars="none"
        android:clipToPadding="false"
        android:paddingBottom="@dimen/_10sdp"
        app:layout_constraintTop_toBottomOf="@id/header_layout"
        app:layout_constraintBottom_toBottomOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- HERO CARD -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:background="@drawable/premium_glass_black_bg"
                android:padding="@dimen/_16sdp"
                android:layout_marginHorizontal="@dimen/_12sdp"
                android:layout_marginTop="@dimen/_8sdp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="FEAR LAUNCHER"
                    android:textColor="#FFFFFF"
                    android:textSize="@dimen/_22ssp"
                    android:textStyle="bold"
                    android:letterSpacing="0.04" />

                <TextView
                    android:id="@+id/version_text_display"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="1.21.1"
                    android:textColor="#A855F7"
                    android:textSize="@dimen/_10ssp"
                    android:textStyle="bold"
                    android:layout_marginTop="2dp" />

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:layout_marginTop="8dp">

                    <TextView
                        android:id="@+id/card_runtime_info"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="JVM: INTERNAL"
                        android:textColor="#42FF42"
                        android:textSize="@dimen/_8ssp"
                        android:textStyle="bold"
                        android:background="#2042FF42"
                        android:paddingHorizontal="8dp"
                        android:paddingVertical="4dp"
                        android:layout_marginEnd="6dp" />

                    <TextView
                        android:id="@+id/card_loader_info"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="LOADER: AUTO"
                        android:textColor="#4D9BFF"
                        android:textSize="@dimen/_8ssp"
                        android:textStyle="bold"
                        android:background="#294D9BFF"
                        android:paddingHorizontal="8dp"
                        android:paddingVertical="4dp" />
                </LinearLayout>

                <TextView
                    android:id="@+id/account_name_display"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Guest"
                    android:textColor="#E6FFFFFF"
                    android:textSize="@dimen/_12ssp"
                    android:layout_marginTop="10dp" />

                <TextView
                    android:id="@+id/homepage_chat_bubble"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textColor="#80FFFFFF"
                    android:textSize="10sp"
                    android:textStyle="italic"
                    android:text="[SYSTEM] Boot sequence ready." />

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="#1AFFFFFF"
                    android:layout_marginTop="12dp"
                    android:layout_marginBottom="12dp" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <com.kdt.mcgui.mcVersionSpinner
                        android:id="@+id/mc_version_spinner"
                        android:layout_width="0dp"
                        android:layout_height="42dp"
                        android:layout_weight="1"
                        android:background="@drawable/premium_glass_black_bg" />

                    <ImageButton
                        android:id="@+id/settings_button_main"
                        android:layout_width="42dp"
                        android:layout_height="42dp"
                        android:src="@drawable/ic_sharp_settings_24"
                        android:background="@drawable/premium_glass_black_bg"
                        android:padding="9dp"
                        app:tint="#4D9BFF"
                        android:layout_marginStart="8dp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="12dp">

                    <com.kdt.mcgui.MineButton
                        android:id="@+id/play_button"
                        android:layout_width="0dp"
                        android:layout_height="52dp"
                        android:layout_weight="1"
                        android:text="PLAY"
                        android:textColor="#FFFFFF"
                        android:textSize="@dimen/_14ssp"
                        android:textStyle="bold"
                        android:letterSpacing="0.15"
                        android:drawablePadding="10dp"
                        android:background="@drawable/theme_button_bg" />

                    <ImageButton
                        android:id="@+id/edit_profile_button_main"
                        android:layout_width="52dp"
                        android:layout_height="52dp"
                        android:src="@drawable/ic_refresh"
                        android:background="@drawable/premium_glass_black_bg"
                        android:padding="12dp"
                        android:layout_marginStart="10dp" />
                </LinearLayout>

            </LinearLayout>

            <!-- INSTANCE CAROUSEL -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="INSTANCES"
                android:textColor="#A855F7"
                android:textSize="@dimen/_10ssp"
                android:textStyle="bold"
                android:letterSpacing="0.12"
                android:layout_marginStart="@dimen/_16sdp"
                android:layout_marginTop="@dimen/_14sdp"
                android:layout_marginBottom="@dimen/_8sdp" />

            <HorizontalScrollView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:scrollbars="none"
                android:clipToPadding="false"
                android:paddingStart="@dimen/_12sdp"
                android:paddingEnd="@dimen/_12sdp">

                <LinearLayout
                    android:id="@+id/homepage_instance_carousel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal" />

            </HorizontalScrollView>

        </LinearLayout>
    </ScrollView>

'''

t = open(B + 'fragment_launcher.xml', encoding='utf-8').read()
i = t.index('    <!-- ULTRA-PROFESSIONAL GLASS SIDEBAR TRAY')
tail = t[i:]
new = HEAD + tail
for rid in ['homepage_skin_head','homepage_chat_bubble','account_name_display','version_text_display',
 'card_runtime_info','card_loader_info','mc_version_spinner','play_button','edit_profile_button_main',
 'settings_button_main','header_layout','hamburger_menu_icon','header_account_hub_btn','header_avatar_card',
 'header_notification_btn','settings_tray','tray_container','homepage_instance_carousel','background_animation_view']:
    assert new.count('@+id/' + rid) >= 1, rid
open(B + 'fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(new)
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(new)
print('layout OK')

# JAVA: carousel + social buttons
t = open(J, encoding='utf-8').read()
anchor = '        view.post(() -> playSplashIntro(view));'
assert t.count(anchor) == 1
t = t.replace(anchor, anchor + '\n        loadInstanceCarousel(view);\n        bindSocialButtons(view);')
anchor2 = '    private void collapseTray(View settingsTray) {'
assert t.count(anchor2) == 1
methods = '''    private void loadInstanceCarousel(View view) {
        android.widget.LinearLayout carousel = view.findViewById(R.id.homepage_instance_carousel);
        if (carousel == null) return;
        carousel.removeAllViews();
        try {
            net.kdt.pojavlaunch.instances.Instances data = net.kdt.pojavlaunch.instances.Instances.loadDisplay();
            for (int i = 0; i < data.list.size(); i++) {
                final net.kdt.pojavlaunch.instances.DisplayInstance di = data.list.get(i);
                android.view.View card = getLayoutInflater().inflate(R.layout.item_home_instance_card, carousel, false);
                android.widget.TextView name = card.findViewById(R.id.home_inst_name);
                android.widget.TextView ver = card.findViewById(R.id.home_inst_version);
                if (name != null) name.setText(di.name);
                if (ver != null) ver.setText(di.versionId);
                card.setBackgroundResource(i == data.selectedIndex ? R.drawable.theme_button_bg : R.drawable.premium_glass_black_bg);
                card.setOnClickListener(v3 -> {
                    v3.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    net.kdt.pojavlaunch.SoundManager.playClick();
                    try { net.kdt.pojavlaunch.instances.Instances.setSelectedInstance(di); } catch (Throwable ignored) {}
                    for (int j = 0; j < carousel.getChildCount(); j++) {
                        carousel.getChildAt(j).setBackgroundResource(R.drawable.premium_glass_black_bg);
                    }
                    v3.setBackgroundResource(R.drawable.theme_button_bg);
                    android.widget.TextView vt = view.findViewById(R.id.version_text_display);
                    if (vt != null && di.versionId != null) vt.setText(di.versionId);
                });
                carousel.addView(card);
            }
        } catch (Throwable ignored) {}
    }

    private void bindSocialButtons(View view) {
        View discord = view.findViewById(R.id.social_discord_btn);
        if (discord != null) discord.setOnClickListener(v -> {
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.gg/9xBZSNG3Uc"))); } catch (Throwable ignored) {}
        });
        View tg = view.findViewById(R.id.social_telegram_btn);
        if (tg != null) tg.setOnClickListener(v -> {
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@twicefear3"))); } catch (Throwable ignored) {}
        });
    }

'''
t = t.replace(anchor2, methods + anchor2)
open(J, 'w', encoding='utf-8', newline='').write(t)
assert t.count('{') == t.count('}'), 'brace mismatch'
print('MC13 OK')
