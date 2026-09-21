p = 'app_pojavlauncher/src/main/res/layout/fragment_launcher.xml'
t = open(p, encoding='utf-8').read()
start = t.index('        <!-- PANE 1: LEFT HAND SYSTEM TERMINAL')
end = t.index('    <!-- UI Logic Dummies -->')
new = '''        <!-- HERO: PLAYER CARD (Lunar-style) -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:background="@drawable/premium_glass_black_bg"
            android:padding="@dimen/_16sdp"
            android:layout_marginHorizontal="@dimen/_6sdp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <FrameLayout
                    android:layout_width="86dp"
                    android:layout_height="86dp"
                    android:background="@drawable/premium_glass_black_bg"
                    android:padding="6dp"
                    android:layout_marginEnd="@dimen/_12sdp">
                    <com.kdt.mcgui.MinecraftSkinView
                        android:id="@+id/homepage_skin_head"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:layout_gravity="center" />
                </FrameLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/account_name_display"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Guest"
                        android:textColor="#FFFFFF"
                        android:textSize="@dimen/_15ssp"
                        android:textStyle="bold"
                        android:letterSpacing="0.02" />

                    <TextView
                        android:id="@+id/version_text_display"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="1.21.1"
                        android:textColor="#80FFFFFF"
                        android:textSize="@dimen/_9ssp"
                        android:layout_marginTop="2dp" />

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginTop="6dp">

                        <TextView
                            android:id="@+id/card_runtime_info"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="JVM: INTERNAL"
                            android:textColor="#42FF42"
                            android:textSize="@dimen/_7ssp"
                            android:textStyle="bold"
                            android:background="#2042FF42"
                            android:paddingHorizontal="6dp"
                            android:paddingVertical="3dp"
                            android:layout_marginEnd="6dp" />

                        <TextView
                            android:id="@+id/card_loader_info"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="LOADER: AUTO"
                            android:textColor="#4D9BFF"
                            android:textSize="@dimen/_7ssp"
                            android:textStyle="bold"
                            android:background="#294D9BFF"
                            android:paddingHorizontal="6dp"
                            android:paddingVertical="3dp" />
                    </LinearLayout>
                </LinearLayout>
            </LinearLayout>

            <TextView
                android:id="@+id/homepage_chat_bubble"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/_10sdp"
                android:background="@drawable/premium_glass_black_bg"
                android:paddingHorizontal="10dp"
                android:paddingVertical="8dp"
                android:textColor="#E6FFFFFF"
                android:textSize="10sp"
                android:textAlignment="center"
                android:fontFamily="monospace"
                android:text="[SYSTEM] Boot sequence ready." />

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#1AFFFFFF"
                android:layout_marginTop="@dimen/_12sdp"
                android:layout_marginBottom="@dimen/_12sdp" />

            <com.kdt.mcgui.mcVersionSpinner
                android:id="@+id/mc_version_spinner"
                android:layout_width="match_parent"
                android:layout_height="@dimen/_36sdp"
                android:layout_marginBottom="@dimen/_12sdp"
                android:background="@drawable/premium_glass_black_bg" />

            <com.kdt.mcgui.MineButton
                android:id="@+id/play_button"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:text="PLAY"
                android:textColor="#FFFFFF"
                android:textSize="@dimen/_13ssp"
                android:textStyle="bold"
                android:letterSpacing="0.08"
                android:background="@drawable/theme_button_bg" />

        </LinearLayout>

        <!-- QUICK UTILITIES -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginHorizontal="@dimen/_6sdp"
            android:layout_marginTop="@dimen/_10sdp">

            <ImageButton
                android:id="@+id/edit_profile_button_main"
                android:layout_width="0dp"
                android:layout_height="50dp"
                android:layout_weight="1"
                android:src="@drawable/ic_px_edit"
                android:background="@drawable/premium_glass_black_bg"
                android:padding="10dp"
                app:tint="#4D9BFF"
                android:layout_marginEnd="6dp" />

            <ImageButton
                android:id="@+id/settings_button_main"
                android:layout_width="0dp"
                android:layout_height="50dp"
                android:layout_weight="1"
                android:src="@drawable/ic_sharp_settings_24"
                android:background="@drawable/premium_glass_black_bg"
                android:padding="10dp"
                app:tint="#4D9BFF" />

        </LinearLayout>

    </LinearLayout>

'''
t = t[:start] + new + t[end:]
for i in ['homepage_skin_head','homepage_chat_bubble','account_name_display','version_text_display',
          'card_runtime_info','card_loader_info','mc_version_spinner','play_button',
          'edit_profile_button_main','settings_button_main']:
    assert t.count('@+id/' + i) == 1, i
open(p, 'w', encoding='utf-8', newline='').write(t)
open('app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml', 'w', encoding='utf-8', newline='').write(t)
print('MC10 OK')
