import os

def patch(path, pairs, replace_all=None):
    t = open(path, encoding='utf-8').read()
    for old, new, cnt in pairs:
        c = t.count(old)
        assert c == cnt, '%s: anchor count %d != %d for %r' % (path, c, cnt, old[:60])
        t = t.replace(old, new)
    if replace_all:
        for old, new in replace_all:
            assert t.count(old) >= 1, '%s: %r not found' % (path, old)
            t = t.replace(old, new)
    open(path, 'w', encoding='utf-8', newline='').write(t)

# 1. delete fear render files (core + assets + ui string)
for f in [
 'app_pojavlauncher/src/main/assets/fearrender/profiles/bliss.json',
 'app_pojavlauncher/src/main/assets/fearrender/profiles/complementary.json',
 'app_pojavlauncher/src/main/assets/fearrender/profiles/solas.json',
 'app_pojavlauncher/src/main/res/values/strings_fear_render.xml',
 'app_pojavlauncher/src/main/jniLibs/arm64-v8a/libFearCore.so',
 'app_pojavlauncher/src/main/jniLibs/arm64-v8a/libfear_render.so',
 'app_pojavlauncher/src/main/jniLibs/armeabi-v7a/libFearCore.so',
 'app_pojavlauncher/src/main/jniLibs/armeabi-v7a/libfear_render.so',
 'app_pojavlauncher/src/main/jniLibs/x86/libFearCore.so',
 'app_pojavlauncher/src/main/jniLibs/x86_64/libFearCore.so',
]:
    if os.path.exists(f):
        os.remove(f)
for d in ['app_pojavlauncher/src/main/assets/fearrender/profiles',
          'app_pojavlauncher/src/main/assets/fearrender']:
    try:
        os.rmdir(d)
    except OSError:
        pass

# 2. remove FEAR RENDER option from renderer arrays (UI)
patch('app_pojavlauncher/src/main/res/values/headings_array.xml', [
 ('        <item>@string/mcl_setting_renderer_fear_render</item>\n', '', 1),
 ('        <item>fear_render</item>\n', '', 1),
])

# 3. RendererCompatUtil: drop fear_render special case
patch('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/RendererCompatUtil.java', [
 ('if(rendererId.equals("fear_render") || rendererId.equals("panvk_zink")) {',
  'if(rendererId.equals("panvk_zink")) {', 1),
])

# 4. JREUtils: remove fear_render renderer case, keep panvk_zink
patch('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java', [
 ('            case "turnip_zink":\n            case "vulkan_zink":\n            case "fear_render":\n            case "panvk_zink":\n                if ("fear_render".equals(renderer) || "panvk_zink".equals(renderer)) {\n                    Logger.appendToLog("[FearRender] Initializing Fear Render (Panfrost Vulkan + Zink)...");\n                    Logger.appendToLog("[FearRender] Mali texture fix: PAN_MESA_DEBUG=noafbc");\n                } else {',
  '            case "turnip_zink":\n            case "vulkan_zink":\n            case "panvk_zink":\n                if ("panvk_zink".equals(renderer)) {\n                    Logger.appendToLog("[PanVK] Initializing PanVK Zink renderer (Panfrost Vulkan + Zink)...");\n                    Logger.appendToLog("[PanVK] Mali texture fix: PAN_MESA_DEBUG=noafbc");\n                } else {', 1),
])

patch('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java', [
 ('                if ("fear_render".equals(renderer) || "panvk_zink".equals(renderer)) {\n                    envMap.put("MESA_VK_DEVICE_SELECT_FORCE_DEFAULT_DEVICE", "1");',
  '                if ("panvk_zink".equals(renderer)) {\n                    envMap.put("MESA_VK_DEVICE_SELECT_FORCE_DEFAULT_DEVICE", "1");', 1),
 ('boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer) || "fear_render".equals(renderer);',
  'boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer);', 1),
 ('            case "mesa_softpipe":\n            case "turnip_zink":\n            case "vulkan_zink":\n            case "fear_render":\n            case "panvk_zink":\n                if ("fear_render".equals(renderer) || "panvk_zink".equals(renderer)) {\n                    Logger.appendToLog("[FearRender] Loading OSMesa + libvulkan_panfrost.so...");\n                    setUseTurnip(false);\n                } else {',
  '            case "mesa_softpipe":\n            case "turnip_zink":\n            case "vulkan_zink":\n            case "panvk_zink":\n                if ("panvk_zink".equals(renderer)) {\n                    Logger.appendToLog("[PanVK] Loading OSMesa + libvulkan_panfrost.so...");\n                    setUseTurnip(false);\n                } else {', 1),
 ('Logger.appendToLog("[FearRender] OSMesa namespace load failed (continuing)");',
  'Logger.appendToLog("[PanVK] OSMesa namespace load failed (continuing)");', 1),
])

# 5. GameRunner: remove fear_render from the two renderer checks
patch('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java', [
 ('|| rendererName.equals("panvk_zink") || rendererName.equals("fear_render")) {',
  '|| rendererName.equals("panvk_zink")) {', 1),
 ('if (rendererName.equals("fear_render") || rendererName.equals("panvk_zink")\n                || rendererName.equals("turnip_zink")',
  'if (rendererName.equals("panvk_zink")\n                || rendererName.equals("turnip_zink")', 1),
])

# 6. recorder export dialog: black bg, crimson accents, crimson gradient buttons
patch('app_pojavlauncher/src/main/res/layout/dialog_recorder_export.xml', [
 ('@drawable/premium_gradient_bg', '@drawable/fear_bg_dark', 1),
], replace_all=[
 ('#FF003C', '#DC143C'),
 ('@drawable/premium_button_bg', '@drawable/crimson_button_bg'),
])

for p in ['app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java',
          'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java']:
    t = open(p, encoding='utf-8').read()
    assert t.count('{') == t.count('}')
    assert 'fear_render' not in t, p
print('fear render removal + recorder export restyle OK')
