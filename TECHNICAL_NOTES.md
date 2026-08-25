# Technical Notes: FearLauncher Quasar Renderer Shaderpack & Color Correctness Fixes

## 1. Overview & Architecture
FearLauncher translates desktop Minecraft OpenGL calls into Mobile OpenGL ES 3.2 calls at runtime when running under the **Quasar** renderer (`backend = libltw.so`). Because vendor GLES drivers (such as ARM Mali-G615 MC2 on Android 16 / API 36) enforce strict ESSL rules and do not implement Mesa/Zink-style desktop GLSL workarounds, all desktop GLSL compatibility patching and color format management are handled at the native GL interception layer (`app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c`) and Java preprocessor pipeline.

---

## 2. Complementary Reimagined Construct Mapping & LTW Implementation
| Complementary Reimagined Construct | Desktop GLSL Specification | ESSL 3.20 / LTW Transformation |
| :--- | :--- | :--- |
| `noperspective in/out vec2 texCoord;` | Desktop interpolation qualifier | Replaced with 13 spaces (`"             "`), falling back to default smooth interpolation |
| `#extension GL_NV_shader_noperspective_interpolation` | Vendor extension directive | Sanitized into same-length single-line comment `//extension GL_NV...` |
| `#version 120` / `#version 130` | Legacy Desktop GLSL profile | Rewritten to `#version 320 es` |
| `gl_FragData[0..7]` | Legacy desktop MRT array | Translated to `ltw_FragData0..7` with `layout(location = n) out vec4 ltw_FragDataN;` declarations |
| `gl_FragColor` | Legacy single-target output | Translated to `ltw_FragColor` with `layout(location = 0) out vec4 ltw_FragColor;` declaration |
| `texture2D()`, `texture2DLod()` | Legacy sampler functions | Renamed to `texture  ()` and `textureLod  ()` with space padding to keep line offsets stable |
| `ftransform()` | Legacy MVP matrix transform | Synthesized into `gl_Position ` |
| `#extension GL_ARB_shader_texture_lod` | Desktop texture LOD extension | Sanitized into same-length comment `//extension GL_ARB...` |

---

## 3. Bug #1: Complementary Reimagined Shader Compilation Fix
### Root Cause
Desktop shaderpacks (such as Complementary Reimagined r5.8.1) target desktop GLSL specifications and declare desktop extensions like `#extension GL_NV_shader_noperspective_interpolation : enable` alongside the `noperspective` interpolation qualifier.
In ESSL 3.20 on ARM Mali drivers, `noperspective` is a **reserved keyword**, triggering a fatal compiler error in `deferred1.vsh`:
`0:74: L0003: Keyword 'noperspective' is reserved` -> `ShaderCompileException` -> Iris disables shader pipeline.

### Fix Locations
- **`app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c`**:
  - Implemented `patch_es_compat_glsl()` in `glShaderSource_hook`.
  - Concatenates input shader source strings and applies line-number preserving AST/token transformations before calling the native GL driver.
- **`app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/quasar/transpile/ShaderPreprocessor.java`**:
  - Updated Java-side preprocessor rules to match native sanitization rules.

---

## 4. Bug #2: Solas Desaturated / Milky Purple Fog Color Correctness Fix
### Root Cause
- **sRGB Encode Mismatch**: Desktop Iris expects an sRGB-encoded backbuffer (`GL_FRAMEBUFFER_SRGB` semantics). When Iris renders linear color values to default framebuffer 0 without sRGB write control / gamma correction active on present, output suffers from gamma ~1.0 presentation mismatch—producing a washed-out, desaturated, milky purple/fog tint across the scene.
- **Color Rescaling**: `LIBGL_COLOR_RESCALE=1` enabled gl4es/LTW color clamping/rescaling passes on float attachments (`RGBA16F`, `RGBA32F`), corrupting composite color buffer values.

### Fix Locations
- **`app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c`**:
  - Captured `g_ltw_handle` to resolve underlying GL symbols directly from LTW.
  - Tracked `GL_FRAMEBUFFER_SRGB` (0x8DB9) state across `glEnable`, `glDisable`, and `glIsEnabled`.
  - Implemented strict internal format preservation in `glTexImage2D_hook`, `glTexStorage2D_hook`, `glRenderbufferStorage_hook`, `glFramebufferTexture2D_hook`, `glReadPixels_hook`, and `glBlitFramebuffer_hook` for floating-point and sRGB formats (`GL_RGBA16F`, `GL_RGBA32F`, `GL_RG16F`, `GL_R16F`, `GL_R11F_G11F_B10F`, `GL_SRGB8_ALPHA8`, `GL_SRGB8`, `GL_RGB8`, `GL_RGBA8`, `GL_DEPTH24_STENCIL8`, `GL_DEPTH32F_STENCIL8`, etc.).
- **`app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java`**:
  - Configured `LIBGL_COLOR_RESCALE=0` for the `quasar` renderer profile to disable color rescale on float/sRGB attachments.

---

## 5. Debugging & Shader Dumping Environment Variables
- **`LTW_DEBUG=1`**: Enables verbose `LOGI` logging in logcat for extension sanitization, qualifier rewrites, sampler renames, and precision injections.
- **`LTW_SHADER_DUMP=1`**: Dumps pre-patch (`quasar_shader_XXXX_pre_patch.glsl`) and post-patch (`quasar_shader_XXXX_post_patch.glsl`) source files into the cache folder (`MESA_GLSL_CACHE_DIR` or `MOD_ANDROID_RUNTIME`).

---

## 6. Test Matrix Table

| Shaderpack / Preset | Platform | Shader Compilation | Visual Quality & Color Correctness |
| :--- | :--- | :---: | :---: |
| **Complementary Reimagined r5.8.1** | ARM Mali-G615 MC2 (Android 16 API 36) | PASS | OK (Pipeline remains active, deferred1.vsh compiles cleanly) |
| **Complementary Reimagined r5.8.1** | Qualcomm Adreno (Adreno 600/700 series) | PASS | OK |
| **Solas Shaders** | ARM Mali-G615 MC2 (Android 16 API 36) | PASS | OK (Colors correct, normal saturation/contrast, no milky purple/fog tint) |
| **Solas Shaders** | Qualcomm Adreno (Adreno 600/700 series) | PASS | OK |
| **Bliss v2.1.2** | ARM Mali-G615 MC2 (Android 16 API 36) | PASS | OK (Zero new warnings, visuals identical) |
| **Bliss v2.1.2** | Qualcomm Adreno (Adreno 600/700 series) | PASS | OK |
| **Vanilla Minecraft (Shaders Off)** | ARM Mali-G615 MC2 & Adreno | PASS | OK (Pixel-identical to stock, zero performance loss) |
