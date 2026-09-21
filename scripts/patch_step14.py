t = open('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java', encoding='utf-8').read()
start = '        // Setup Chat Bubble typed greetings loop\n'
end = '            mChatBubbleHandler.postDelayed(mChatBubbleRunnable, 1000);\n        }\n'
i = t.index(start); j = t.index(end) + len(end)
removed = t[i:j]
assert 'homepage_chat_bubble' in removed and 'CHAT_MESSAGES' in removed and removed.count('{') == removed.count('}')
t = t[:i] + t[j:]
assert 'homepage_chat_bubble' not in t
assert t.count('{') == t.count('}')
open('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java', 'w', encoding='utf-8', newline='').write(t)
print('chat bubble java refs removed')
