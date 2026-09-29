#!/usr/bin/env python3
"""FEARPATCH: MobileGlues source patches for FearRender (idempotent per fresh clone).

Patches applied to the MobileGlues source tree before building:

1. gl/glsl/glsl_for_es.cpp - make translator errors ALWAYS visible.
   The stock build hides glslang / SPIRV-Cross errors behind debug-only
   LOG_E/LOG_D. When translation fails, the ORIGINAL desktop GLSL is passed
   to the GLES driver, which then fails with a confusing error such as
   '0:948: L0001: Expected identifier, found uniform'. Surface the real cause.

2. gl/glsl/glsl_for_es.cpp - dump failing shader source to the MG dir so the
   exact breaking GLSL/ESSL can be inspected after a test run.

3. gl/glsl/glsl_for_es.cpp (FEARRENDER-UNIFORMW) - process_uniform_declarations
   matches the substring "uniform" ANYWHERE, including inside identifiers such
   as Complementary's 'uniformSkyIlluminance' variable, and mangles the code
   (e.g. turning an assignment into 'uniform SkyIlluminance ;' inside main()).
   Only match the standalone keyword.

4. gl/glsl/glsl_for_es.cpp (FEARRENDER-NOPERSPECTIVE) - GLES has no
   noperspective interpolation; SPIRV-Cross emits the
   GL_NV_shader_noperspective_interpolation extension for it, which Mali
   rejects. Strip the qualifier before glslang (demoting to smooth) and drop
   any leftover extension line in the ESSL.

5. gl/glsl/glsl_for_es.cpp (FEARRENDER-IMG) - GLES requires uniform images to
   carry a format layout qualifier and a readonly/writeonly memory qualifier.
   Desktop GLSL (and the SPIR-V from glslang) has neither, so the generated
   ESSL fails with S0001 on Mali. Repair image uniform declarations after
   translation: add the pack-declared formats (voxel_img=r32ui (GLES has no
   r16ui image qualifier, and the texture storage is rewritten to R32UI to
   match), floodfill_img/_copy=rgba16f, from Complementary's shaders.properties),
   fall back to r32ui for uimage*/rgba16f otherwise, and derive
   readonly/writeonly from imageLoad/imageStore usage in the same shader.

6. gl/texture.cpp (FEARRENDER-R16UI) - GLES image format qualifiers only allow
   r32ui/r8ui for integer images (Mali: S0059 'Expected layout qualifier
   identifier, got r16ui'). Rewrite R16UI texture storage to R32UI in the
   central internal_convert hook (existing GL_R16UI case) so the storage
   matches the r32ui shader declarations produced by patch 5.

7. FV4 gl/texture.cpp (FEARRENDER-RENDERABLE) - Complementary-class packs
   declare colortex formats RGBA16/RGB16/RG16/R16/RGB16F/RGB32F. GLES does
   NOT consider them color-renderable (norm16 adds the texture format only;
   RGB16F/RGB32F are not in the EXT_color_buffer_float list), so Iris's
   3-attachment color FBO failed with GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT
   (36054) - the Complementary failure in latestlog-51. Rewrite all of them
   to float formats that ARE renderable on Mali via EXT_color_buffer_float
   (RGBA16F/RG16F/R16F/RGBA32F).

8. FV4 gl/framebuffer.cpp (FEARRENDER-FBODIAG) - when an FBO still reports
   incomplete, dump every color attachment so the next latestlog names the
   exact offending colortex target instead of a bare status code.

9. FV5 egl/egl.cpp (FEARRENDER-FPSUNLOCK) - force the EGL swap interval to 0
   so presentation is not pinned to the display's refresh rate (the "60 fps
   lock"); the frame rate is then bounded only by the GPU. Escape hatch:
   MG_FORCE_VSYNC=1 in the environment restores vsync.

10. FV6 (FEAR-DOCTOR) - protection & diagnostic system: device/caps banner on
    first frame, 600-frame perf heartbeat (avg/worst ms, low-FPS entity/CPU
    diagnosis), GL error audit, shader-failure analysis with known-fix hints,
    and framebuffer status names with fix hints. One latestlog answers
    "what broke and how to fix it".

11. FV7 gl/texture.cpp (FEARRENDER-FV7 universal renderability) - probe each
    conditional format ONCE at runtime with a throwaway FBO and rewrite
    non-renderable requests to the nearest renderable one: R11F_G11F_B10F /
    RGBA16F / R16F / RG16F / RGBA32F fall back through the float chain to
    RGBA8 when EXT_color_buffer_float is missing; SNORM formats (RGB8_SNORM =
    Complementary colortex1, RGBA8_SNORM, R8/RG8/RGBA16_SNORM) always rewrite
    to a renderable target. Makes shader packs run on every GPU.

12. FV8 config/settings.cpp (FEARRENDER-FV8) - FEAR_FSR env override: the
    launcher profile's custom env switches FSR1 per launch (0 off, 1..4 =
    UltraQuality..Performance), winning over config.json, so fps-vs-sharpness
    needs no rebuild or MG file editing.

13. FV9 FSR1.cpp + egl/egl.cpp (FEAR-AUTOBALANCE) - adaptive resolution
    governor: steps FSR1 quality one preset at a time (fps < 40 -> sharper
    preset down toward Performance; fps > 57 -> back up toward UltraQuality),
    with a confirmation heartbeat and cooldown so loading spikes never
    trigger it. Preset changes re-run the FSR resize path live and log the
    actual render/upscale geometry. Kill switches: FEAR_AUTOBALANCE=0, or a
    pinned FEAR_FSR preset.

Usage: python3 tools/fearrender/fearpatch.py [mobileglues-cpp-dir]
"""
import os
import sys

def fail(msg):
    print("FEARPATCH FAIL: " + msg)
    sys.exit(1)

root = sys.argv[1] if len(sys.argv) > 1 else 'mg/MobileGlues-cpp'
if not os.path.isdir(os.path.join(root, 'gl')):
    fail("MobileGlues-cpp dir not found: %s" % root)

# ------------------------------------------------- glsl_for_es.cpp logging
# The anchor line contains a C string escape (backslash-n) before %s; building
# it dynamically instead of as a literal keeps this file free of fragile
# escape sequences (a doubled backslash slipped through a manual push once and
# broke the whole patch run).
p = os.path.join(root, 'gl/glsl/glsl_for_es.cpp')
s = open(p).read()

if '[FearRender] GLSL(glslang)->SPIRV COMPILE ERROR:' in s:
    print("FEARPATCH SKIP: glslang compile errors already upgraded")
else:
    idx = s.find('GLSL Compiling ERROR')
    if idx < 0:
        fail("glslang compile-error anchor not found")
    start = s.rfind('LOG_D(', 0, idx)
    end = s.find('shader.getInfoLog())', idx)
    if start < 0 or end < 0:
        fail("glslang compile-error anchor not found")
    end += len('shader.getInfoLog())')
    a = s[start:end]
    b = a.replace('LOG_D("GLSL Compiling ERROR: ', 'LOG_W_FORCE("[FearRender] GLSL(glslang)->SPIRV COMPILE ERROR: ')
    s = s.replace(a, b, 1)
    print("FEARPATCH OK: glslang compile errors now always logged")

a = 'LOG_D("Shader Linking ERROR: %s", program.getInfoLog())'
b = 'LOG_W_FORCE("[FearRender] GLSL(glslang)->SPIRV LINK ERROR: %s", program.getInfoLog())'
if a in s:
    s = s.replace(a, b, 1)
    print("FEARPATCH OK: glslang link errors now always logged")
elif b in s:
    print("FEARPATCH SKIP: glslang link errors already upgraded")
else:
    fail("glslang link-error anchor not found")

a = 'LOG_E("Error: %s failed in spirv-cross: %s", what, spvc_context_get_last_error_string(context))'
b = 'LOG_W_FORCE("[FearRender] SPIRV-Cross ERROR: %s failed: %s", what, spvc_context_get_last_error_string(context))'
if a in s:
    s = s.replace(a, b, 1)
    print("FEARPATCH OK: spirv-cross errors now always logged")
elif b in s:
    print("FEARPATCH SKIP: spirv-cross errors already upgraded")
else:
    fail("spvc_ok anchor not found")

# --------------------------------- process_uniform_declarations word guard
a = '        if (glslCode.compare(scan_pos, 7, "uniform") == 0) {'
new_guard = (
    '        if (glslCode.compare(scan_pos, 7, "uniform") == 0 /* FEARRENDER-UNIFORMW: standalone keyword only */\n'
    '            && (scan_pos == 0 || !(std::isalnum((unsigned char)glslCode[scan_pos - 1]) || glslCode[scan_pos - 1] == \'_\'))\n'
    '            && (scan_pos + 7 >= length || !(std::isalnum((unsigned char)glslCode[scan_pos + 7]) || glslCode[scan_pos + 7] == \'_\'))) {'
)
if 'FEARRENDER-UNIFORMW' in s:
    print("FEARPATCH SKIP: process_uniform_declarations already guarded")
else:
    n = s.count(a)
    if n != 1:
        fail("process_uniform_declarations anchor count = %d" % n)
    s = s.replace(a, new_guard, 1)
    print("FEARPATCH OK: process_uniform_declarations word-boundary guard added")

# --------------------------------------------------- helper functions + calls
if 'FEARRENDER-NOPERSPECTIVE' in s:
    print("FEARPATCH SKIP: noperspective/image helpers already present")
else:
    anchor = 'std::string GLSLtoGLSLES_2(const char* glsl_code, GLenum glsl_type, uint essl_version, int& return_code) {'
    if s.count(anchor) != 1:
        fail("GLSLtoGLSLES_2 anchor not found")

    helpers = r'''
// ------------------------- FearRender shader-compat helpers -------------------------
// FEARRENDER-NOPERSPECTIVE: GLES has no noperspective interpolation; SPIRV-Cross
// would emit GL_NV_shader_noperspective_interpolation for it, which Mali rejects.
// Demote the qualifier to the default (smooth) interpolation before glslang runs.
static void fear_strip_noperspective(std::string& s) {
    static const std::string kw = "noperspective";
    size_t pos = 0;
    while ((pos = s.find(kw, pos)) != std::string::npos) {
        bool before = (pos == 0) || !(std::isalnum((unsigned char)s[pos - 1]) || s[pos - 1] == '_');
        size_t after = pos + kw.size();
        bool after_ok = (after >= s.size()) || !(std::isalnum((unsigned char)s[after]) || s[after] == '_');
        if (before && after_ok) {
            size_t len = kw.size();
            if (after < s.size() && (s[after] == ' ' || s[after] == '\t')) ++len;
            s.erase(pos, len);
        } else {
            pos += kw.size();
        }
    }
}

static bool fear_is_ident_char(char c) { return std::isalnum((unsigned char)c) || c == '_'; }

static bool fear_has_word(const std::string& s, const std::string& w) {
    const size_t wlen = w.size();
    size_t pos = s.find(w);
    while (pos != std::string::npos) {
        bool before = (pos == 0) || !fear_is_ident_char(s[pos - 1]);
        size_t after = pos + wlen;
        bool after_ok = (after >= s.size()) || !fear_is_ident_char(s[after]);
        if (before && after_ok) return true;
        pos = s.find(w, pos + 1);
    }
    return false;
}

// FEARRENDER-IMG: GLES requires uniform images to carry a format layout qualifier
// and a readonly/writeonly memory qualifier; desktop GLSL (and the SPIR-V produced
// from it) has neither, so the generated ESSL fails with S0001 on Mali. Repair
// image uniform declarations after translation.
static std::string fear_image_format_for(const std::string& name, const std::string& type) {
    // Formats declared by the shader packs (Complementary shaders.properties).
    if (name == "voxel_img") return "r32ui";
    if (name == "floodfill_img" || name == "floodfill_img_copy") return "rgba16f";
    if (type.rfind("uimage", 0) == 0) return "r32ui";
    return "rgba16f";
}

static bool fear_image_used(const std::string& essl, const char* func, const std::string& name) {
    std::string needle = std::string(func) + "(" + name;
    size_t pos = 0;
    while ((pos = essl.find(needle, pos)) != std::string::npos) {
        size_t after = pos + needle.size();
        if (after >= essl.size() || !fear_is_ident_char(essl[after])) return true;
        ++pos;
    }
    // tolerate "func( name"
    needle = std::string(func) + "( " + name;
    pos = 0;
    while ((pos = essl.find(needle, pos)) != std::string::npos) {
        size_t after = pos + needle.size();
        if (after >= essl.size() || !fear_is_ident_char(essl[after])) return true;
        ++pos;
    }
    return false;
}

static std::string fear_fix_image_uniforms(const std::string& essl) {
    static const char* k_formats[] = {
        "rgba32f", "rgba16f", "rgba8", "r32f", "r16f", "r8", "rg32f", "rg16f", "rg8",
        "r32ui", "r16ui", "r8ui", "rgba32ui", "rgba16ui", "rgba8ui",
        "r32i", "r16i", "r8i", "rgba32i", "rgba16i", "rgba8i", "r11f_g11f_b10f", "rgba16_snorm"
    };
    static const char* k_types[] = {
        "uimage2DArray", "image2DArray", "uimageCubeArray", "imageCubeArray",
        "uimageBuffer", "imageBuffer", "uimage2D", "image2D", "uimage3D", "image3D",
        "uimageCube", "imageCube", "uimage1D", "image1D"
    };

    std::string out;
    out.reserve(essl.size() + 1024);
    size_t line_start = 0;
    while (line_start < essl.size()) {
        size_t line_end = essl.find('\n', line_start);
        if (line_end == std::string::npos) line_end = essl.size();
        std::string line = essl.substr(line_start, line_end - line_start);

        if (line.find("GL_NV_shader_noperspective_interpolation") != std::string::npos) {
            // FEARRENDER-NOPERSPECTIVE: drop the unsupported extension requirement
            if (line_end < essl.size()) out += '\n';
            line_start = line_end + 1;
            continue;
        }

        if (line.find("uniform") == std::string::npos || line.find("image") == std::string::npos) {
            out += line;
        } else {
            std::string binding, precision, rw, type, name, fmt;
            bool has_layout_fmt = false;

            size_t lp = line.find("layout");
            if (lp != std::string::npos) {
                size_t open = line.find('(', lp);
                size_t close = (open == std::string::npos) ? std::string::npos : line.find(')', open);
                if (open != std::string::npos && close != std::string::npos) {
                    std::string inside = line.substr(open + 1, close - open - 1);
                    for (const char* f : k_formats) {
                        if (fear_has_word(inside, f)) { has_layout_fmt = true; fmt = f; break; }
                    }
                    size_t bp = inside.find("binding");
                    if (bp != std::string::npos) {
                        size_t eq = inside.find('=', bp);
                        if (eq != std::string::npos) {
                            size_t v = eq + 1;
                            while (v < inside.size() && inside[v] == ' ') ++v;
                            size_t ve = v;
                            while (ve < inside.size() && std::isdigit((unsigned char)inside[ve])) ++ve;
                            if (v < ve) binding = "binding = " + inside.substr(v, ve - v);
                        }
                    }
                }
            }

            if (fear_has_word(line, "readonly")) rw += "readonly ";
            if (fear_has_word(line, "writeonly")) rw += "writeonly ";
            for (const char* pr : {"highp", "mediump", "lowp"}) {
                if (fear_has_word(line, pr)) { precision = pr; break; }
            }
            for (const char* t : k_types) {
                if (fear_has_word(line, t)) { type = t; break; }
            }

            size_t semi = line.find(';');
            if (semi != std::string::npos) {
                size_t e = semi;
                while (e > 0 && line[e - 1] == ' ') --e;
                size_t b = e;
                while (b > 0 && fear_is_ident_char(line[b - 1])) --b;
                if (b < e) name = line.substr(b, e - b);
            }

            if (type.empty() || name.empty()) {
                out += line; // not a plain image uniform declaration, leave as-is
            } else {
                if (!has_layout_fmt) fmt = fear_image_format_for(name, type);
                if (fmt.empty()) fmt = "rgba16f";
                if (rw.empty()) {
                    bool loads = fear_image_used(essl, "imageLoad", name);
                    bool stores = fear_image_used(essl, "imageStore", name);
                    if (loads && stores) rw = "readonly writeonly ";
                    else if (loads) rw = "readonly ";
                    else rw = "writeonly ";
                }
                if (precision.empty()) precision = "highp";

                std::string nl = "    layout(";
                if (!binding.empty()) nl += binding + ", ";
                nl += fmt + ") uniform " + rw + precision + " " + type + " " + name + ";";
                out += nl;
            }
        }

        if (line_end < essl.size()) out += '\n';
        line_start = line_end + 1;
    }
    return out;
}
// ---------------------- end FearRender shader-compat helpers ----------------------

'''
    s = s.replace(anchor, helpers + anchor, 1)
    print("FEARPATCH OK: noperspective/image helper functions inserted")

    # call site 1: strip noperspective right after preprocess_glsl
    a = '    std::string correct_glsl_str = preprocess_glsl(glsl_code, glsl_type);'
    n = s.count(a)
    if n != 1:
        fail("preprocess_glsl call anchor count = %d" % n)
    s = s.replace(a, a + '\n    fear_strip_noperspective(correct_glsl_str); /* FEARRENDER-NOPERSPECTIVE */', 1)
    print("FEARPATCH OK: noperspective strip call wired into GLSLtoGLSLES_2")

    # call site 2: image uniform repair after ESSL post-processing
    a = '    essl = forceSupporterOutput(essl);'
    n = s.count(a)
    if n != 1:
        fail("forceSupporterOutput anchor count = %d" % n)
    s = s.replace(a, a + '\n    essl = fear_fix_image_uniforms(essl); /* FEARRENDER-IMG */', 1)
    print("FEARPATCH OK: image uniform repair wired into GLSLtoGLSLES_2")

open(p, 'w').write(s)

# ------------------------------------------- shader.cpp failing-shader dump
p = os.path.join(root, 'gl/shader.cpp')
s = open(p).read()

if 'FEARRENDER-DUMP' in s:
    print("FEARPATCH SKIP: shader.cpp failing-shader dump already present")
else:
    a = '#include <cctype>'
    if a not in s:
        fail("cctype include anchor")
    s = s.replace(a, a + '\n#include <cstdio>', 1)

    a = '#include "../config/settings.h"'
    if a not in s:
        fail("settings include anchor")
    s = s.replace(a, a + '\n#include "../config/config.h"', 1)

    old = (
        '    GLES.glGetShaderiv(shader, pname, params);\n'
        '    if (global_settings.ignore_error >= IgnoreErrorLevel::Partial && pname == GL_COMPILE_STATUS && !*params) {'
    )
    new = (
        '    GLES.glGetShaderiv(shader, pname, params);\n'
        '    if (pname == GL_COMPILE_STATUS && !*params) { /* FEARRENDER-DUMP */\n'
        '        GLchar fearInfoLog[512];\n'
        '        GLES.glGetShaderInfoLog(shader, 512, nullptr, fearInfoLog);\n'
        '        LOG_W_FORCE("[FearRender] Shader %d compile FAILED: %s", shader, fearInfoLog)\n'
        '        FILE* fearDump = fopen((std::string(mg_directory_path ? mg_directory_path : "/sdcard/MG") + "/failed_shader_dump.glsl").c_str(), "a");\n'
        '        if (fearDump) {\n'
        '            fprintf(fearDump, "==== SHADER %d COMPILE FAILED ====\\nInfo log: %s\\n---- SOURCE PASSED TO DRIVER ----\\n%s\\n\\n", shader, fearInfoLog, shaderInfo.converted.c_str());\n'
        '            fclose(fearDump);\n'
        '        }\n'
        '    }\n'
        '    if (global_settings.ignore_error >= IgnoreErrorLevel::Partial && pname == GL_COMPILE_STATUS && !*params) {'
    )
    n = s.count(old)
    if n != 1:
        fail("glGetShaderiv anchor count = %d" % n)
    s = s.replace(old, new, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: shader.cpp failing-shader dump added")

# ------------------------------------------- texture.cpp R16UI->R32UI rewrite
# GLES integer image format qualifiers are only r32ui / r8ui; r16ui does not
# exist (Mali: S0059 'Expected layout qualifier identifier, got r16ui'). The
# shader side rewrites voxel_img declarations to r32ui (see above), so the
# texture storage must be rewritten to match. internal_convert is the central
# internalformat hook used by glTexImage* and glTexStorage3D (the DSA wrapper
# routes through it as well).
p = os.path.join(root, 'gl/texture.cpp')
s = open(p).read()
if 'FEARRENDER-R16UI' in s:
    print("FEARPATCH SKIP: texture.cpp R16UI rewrite already present")
else:
    a = (
        '    case GL_R16UI:\n'
        '        if (format) *format = GL_RED_INTEGER;\n'
        '        if (type) *type = GL_UNSIGNED_SHORT;\n'
        '        break;'
    )
    n = s.count(a)
    if n != 1:
        fail("internal_convert R16UI case anchor count = %d" % n)
    new_case = (
        '    case GL_R16UI: /* FEARRENDER-R16UI: GLES integer image formats are r32ui/r8ui only */\n'
        '        // GLES has no r16ui image format qualifier. FearRender rewrites shaderpack\n'
        '        // image declarations (e.g. Complementary voxel_img) from r16ui to r32ui,\n'
        '        // so the texture storage is rewritten to match (uploads arrive as\n'
        '        // UNSIGNED_INT per the pack declaration).\n'
        '        *internal_format = GL_R32UI;\n'
        '        if (format) *format = GL_RED_INTEGER;\n'
        '        if (type) *type = GL_UNSIGNED_INT;\n'
        '        break;'
    )
    s = s.replace(a, new_case, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: texture.cpp R16UI->R32UI rewrite added")


# -------------------------------------- FV4: color-renderable format rewrites
# Complementary-class shaderpacks declare colortex formats like RGBA16, RGB16,
# RG16, R16, RGB16F, RGB32F. Desktop GL renders into all of them; GLES does
# NOT: norm16 (EXT_texture_norm16) only adds the *texture* format RGBA16 (it is
# still not color-renderable), RGB16F/RGB32F are not in the EXT_color_buffer_float
# renderable list. The result was GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT (36054)
# the moment Iris attached 3 colortex targets - exactly the Complementary
# failure in latestlog-51. Rewrite every one of them to a float format that IS
# renderable on Mali (RGBA16F/RG16F/R16F/RGBA32F via EXT_color_buffer_float).
p = os.path.join(root, 'gl/texture.cpp')
s = open(p).read()
if 'FEARRENDER-RENDERABLE' in s:
    print("FEARPATCH SKIP: texture.cpp renderable rewrites already present")
else:
    pairs = []
    def fv4_pair(old, new):
        pairs.append((old, new))
    fv4_pair(
"""    case GL_RGBA16: {
        if (g_gles_caps.GL_EXT_texture_norm16) {
            if (type) *type = GL_UNSIGNED_SHORT;
        } else {
            *internal_format = GL_RGBA16F;
            if (type) *type = GL_FLOAT;
        }
        break;
    }
""",
"""    case GL_RGBA16: { /* FEARRENDER-RENDERABLE: RGBA16 is not color-renderable on GLES (EXT_texture_norm16 adds the texture format only); rewrite to RGBA16F so shaderpack colortex targets stay attachable */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_FLOAT;
        break;
    }
""")
    fv4_pair(
"""    case GL_RGB16: {
        if (g_gles_caps.GL_EXT_texture_norm16) {
            if (type) *type = GL_UNSIGNED_SHORT;
        } else {
            *internal_format = GL_RGB16F;
            if (type) *type = GL_HALF_FLOAT;
        }
        if (format) *format = GL_RGB;
        break;
    }
""",
"""    case GL_RGB16: { /* FEARRENDER-RENDERABLE: RGB16 not renderable on GLES; RGBA16F is (EXT_color_buffer_float) */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RGB;
        break;
    }
""")
    fv4_pair(
"""    case GL_RG16: {
        if (g_gles_caps.GL_EXT_texture_norm16) {
            if (type) *type = GL_UNSIGNED_SHORT;
        } else {
            *internal_format = GL_RG16F;
            if (type) *type = GL_HALF_FLOAT;
        }
        if (format) *format = GL_RG;
        break;
    }
""",
"""    case GL_RG16: { /* FEARRENDER-RENDERABLE: RG16 not renderable on GLES; RG16F is */
        *internal_format = GL_RG16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RG;
        break;
    }
""")
    fv4_pair(
"""    case GL_R16: {
        if (g_gles_caps.GL_EXT_texture_norm16) {
            if (type) *type = GL_UNSIGNED_SHORT;
        } else {
            *internal_format = GL_R16F;
            if (type) *type = GL_FLOAT;
        }
        if (format) *format = GL_RED;
        break;
    }
""",
"""    case GL_R16: { /* FEARRENDER-RENDERABLE: R16 not renderable on GLES; R16F is */
        *internal_format = GL_R16F;
        if (type) *type = GL_FLOAT;
        if (format) *format = GL_RED;
        break;
    }
""")
    fv4_pair(
"""    case GL_RGB16F:
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RGB;
        break;
""",
"""    case GL_RGB16F: /* FEARRENDER-RENDERABLE: RGB16F renderability is driver-dependent; RGBA16F is guaranteed */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RGB;
        break;
""")
    fv4_pair(
"""    case GL_RGBA32F:
    case GL_RGB32F:
        if (type) *type = GL_FLOAT;
        break;
""",
"""    case GL_RGBA32F:
        if (type) *type = GL_FLOAT;
        break;
    case GL_RGB32F: /* FEARRENDER-RENDERABLE: RGB32F not renderable on GLES; RGBA32F is */
        *internal_format = GL_RGBA32F;
        if (type) *type = GL_FLOAT;
        break;
""")
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            fail("FV4 renderable anchor count = %d for %s" % (n, old.strip().splitlines()[0]))
        s = s.replace(old, new, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: texture.cpp FV4 renderable format rewrites added (6 formats)")

# -------------------------------------- FV4: framebuffer completeness diagnostics
# When an FBO still reports incomplete, dump every color attachment we know
# about so the next latestlog tells us WHICH colortex/format is the offender.
p = os.path.join(root, 'gl/framebuffer.cpp')
s = open(p).read()
if 'FEARRENDER-FBODIAG' in s:
    print("FEARPATCH SKIP: framebuffer.cpp FBODIAG already present")
else:
    anchor = """GLenum glCheckFramebufferStatus(GLenum target) {
    GLenum status = GLES.glCheckFramebufferStatus(target);
"""
    n = s.count(anchor)
    if n != 1:
        fail("glCheckFramebufferStatus anchor count = %d" % n)
    diag = """GLenum glCheckFramebufferStatus(GLenum target) {
    GLenum status = GLES.glCheckFramebufferStatus(target);
    /* FEARRENDER-FBODIAG: dump the attachment layout when incomplete, so the
     * shaderpack failure in the log names the exact offending target. */
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        GLuint fbo = (target == GL_READ_FRAMEBUFFER) ? current_read_fbo : current_draw_fbo;
        framebuffer_t* rec = nullptr;
        {
            const auto it = framebuffers.find(fbo);
            if (it != framebuffers.end() && it->second) rec = it->second.get();
        }
        LOG_W_FORCE("FEARRENDER-FBODIAG: fbo=%u target=0x%x status=0x%x", fbo, target, status);
        if (rec) {
            for (size_t i = 0; i < rec->color_attachments.size(); ++i) {
                const attachment_t& a = rec->color_attachments[i];
                if (a.kind == attach_kind_t::None) continue;
                LOG_W_FORCE("FEARRENDER-FBODIAG: color%zu: kind=%d tex=%u level=%d", i, (int)a.kind, a.texture, a.level);
            }
        }
    }
"""
    s = s.replace(anchor, diag, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: framebuffer.cpp FV4 completeness diagnostics added")

# -------------------------------------- FV5: uncapped frame rate (vsync off)
# The game asks for swap interval 1 (Minecraft's VSync option), which pins
# presentation to the display's refresh - on a 60Hz surface that is the "60
# fps lock". Force interval 0 so the frame rate is bounded only by the GPU.
# Escape hatch: set MG_FORCE_VSYNC=1 in the environment to get vsync back.
p = os.path.join(root, 'egl/egl.cpp')
s = open(p).read()
if 'FEARRENDER-FPSUNLOCK' in s:
    print("FEARPATCH SKIP: egl.cpp fps unlock already present")
else:
    a = '#include <cstdio>'
    n = s.count(a)
    if n != 1:
        fail("egl.cpp cstdio include anchor count = %d" % n)
    s = s.replace(a, a + '\n#include <cstdlib> /* FEARRENDER-FPSUNLOCK */\n#include <cstring> /* FEARRENDER-FPSUNLOCK */', 1)

    anchor = """    EGL_API EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) {
        LOG_D("eglSwapInterval, dpy: %p, interval: %d", dpy, interval);
        LOAD_EGL(eglSwapInterval)
        return egl_eglSwapInterval(dpy, interval);
    }
"""
    n = s.count(anchor)
    if n != 1:
        fail("eglSwapInterval anchor count = %d" % n)
    new_impl = """    EGL_API EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) {
        LOG_D("eglSwapInterval, dpy: %p, interval: %d", dpy, interval);
        LOAD_EGL(eglSwapInterval)
        /* FEARRENDER-FPSUNLOCK: force interval 0 unless the user asks for
         * vsync back with MG_FORCE_VSYNC=1. */
        if (interval != 0) {
            static int fear_vsync_pref = -1;
            if (fear_vsync_pref == -1) {
                const char* e = getenv("MG_FORCE_VSYNC");
                fear_vsync_pref = (e != NULL && strcmp(e, "1") == 0) ? 1 : 0;
            }
            if (!fear_vsync_pref) interval = 0;
        }
        return egl_eglSwapInterval(dpy, interval);
    }
"""
    s = s.replace(anchor, new_impl, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: egl.cpp FV5 fps unlock added (vsync off by default)")

# ------------------------------------------ FV6: FEAR-DOCTOR protection system
# One log answers everything: device + caps banner, per-10s frame-rate
# heartbeat with lag diagnosis, GL error audit, shader-failure analysis with
# known-fix hints, and framebuffer status names with fix hints. Every line is
# FEAR- prefixed so the user just sends latestlog.
p = os.path.join(root, 'egl/egl.cpp')
s = open(p).read()
if 'FEAR-DOCTOR' in s:
    print("FEARPATCH SKIP: egl.cpp FEAR-DOCTOR already present")
else:
    a = '#include <cstring> /* FEARRENDER-FPSUNLOCK */'
    n = s.count(a)
    if n != 1:
        fail("egl.cpp cstring include anchor count = %d" % n)
    s = s.replace(a, a + '\n#include <ctime> /* FEAR-DOCTOR */', 1)

    anchor = """    // ApplyFSR upscales into the surface, the swap presents it, the resolution
    // check reacts to a surface that has changed size. The three belong together,
    // and every path that presents a frame has to go through here.
"""
    n = s.count(anchor)
    if n != 1:
        fail("presentSurface comment anchor count = %d" % n)
    heartbeat = """    // FEAR-DOCTOR (FV6): protection & diagnostic system. Device/caps banner on
    // the first presented frame, a 600-frame heartbeat with avg/worst frame
    // times, a low-FPS diagnosis line, and a periodic GL error audit. All
    // FEAR- prefixed so one latestlog answers "why is it slow / what broke".
    static void fear_doctor_heartbeat() {
        static bool fear_diag_boot = false;
        if (!fear_diag_boot) {
            fear_diag_boot = true;
            const GLubyte* fearR = GLES.glGetString(GL_RENDERER);
            const GLubyte* fearV = GLES.glGetString(GL_VERSION);
            const GLubyte* fearVen = GLES.glGetString(GL_VENDOR);
            LOG_W_FORCE("FEAR-DOCTOR: active (FV6) | device: %s | vendor: %s",
                        fearR ? (const char*)fearR : "?", fearVen ? (const char*)fearVen : "?");
            LOG_W_FORCE("FEAR-DOCTOR: GL: %s | caps: norm16=%d rg=%d bufstorage=%d basevertex=%d clipcull=%d",
                        fearV ? (const char*)fearV : "?",
                        g_gles_caps.GL_EXT_texture_norm16, g_gles_caps.GL_EXT_texture_rg,
                        g_gles_caps.GL_EXT_buffer_storage, g_gles_caps.GL_EXT_draw_elements_base_vertex,
                        g_gles_caps.GL_EXT_clip_cull_distance);
            LOG_W_FORCE("FEAR-DOCTOR: settings: fsr1=%d ignore_error=%d",
                        (int)global_settings.fsr1_setting, (int)global_settings.ignore_error);
        }
        static unsigned fear_frames = 0;
        static struct timespec fear_t0, fear_tprev;
        static double fear_worst_ms = 0.0;
        struct timespec fear_now;
        clock_gettime(CLOCK_MONOTONIC, &fear_now);
        if (fear_frames == 0) {
            fear_t0 = fear_tprev = fear_now;
            fear_worst_ms = 0.0;
        } else {
            const double fear_dt = (fear_now.tv_sec - fear_tprev.tv_sec) * 1000.0
                                 + (fear_now.tv_nsec - fear_tprev.tv_nsec) / 1000000.0;
            if (fear_dt > fear_worst_ms) fear_worst_ms = fear_dt;
        }
        fear_tprev = fear_now;
        ++fear_frames;
        if (fear_frames >= 600) {
            const double fear_s = (fear_now.tv_sec - fear_t0.tv_sec)
                                + (fear_now.tv_nsec - fear_t0.tv_nsec) / 1000000000.0;
            const double fear_fps = (fear_s > 0.0) ? fear_frames / fear_s : 0.0;
            const double fear_avg = (fear_s > 0.0) ? 1000.0 * fear_s / fear_frames : 0.0;
            LOG_W_FORCE("FEAR-PERF: %.1f fps | avg %.1f ms/frame | worst %.1f ms%s",
                        fear_fps, fear_avg, fear_worst_ms,
                        fear_fps < 20.0
                            ? " | LOW FPS: entity/CPU-bound or heavy shaderpack - send this log to the dev"
                            : "");
            const GLenum fear_err = GLES.glGetError();
            if (fear_err != GL_NO_ERROR) {
                LOG_W_FORCE("FEAR-PERF: GL error at present: 0x%x", fear_err);
            }
            fear_frames = 0;
            fear_worst_ms = 0.0;
            fear_t0 = fear_now;
        }
    }

"""
    s = s.replace(anchor, heartbeat + anchor, 1)

    anchor = """        LOAD_EGL(eglSwapBuffers)
        if (global_settings.fsr1_setting == FSR1_Quality_Preset::Disabled) {"""
    n = s.count(anchor)
    if n != 1:
        fail("presentSurface body anchor count = %d" % n)
    s = s.replace(anchor, """        LOAD_EGL(eglSwapBuffers)
        fear_doctor_heartbeat(); /* FEAR-DOCTOR */
        if (global_settings.fsr1_setting == FSR1_Quality_Preset::Disabled) {""", 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: egl.cpp FEAR-DOCTOR heartbeat + device banner added")

# ------------------------------------------ FV6: shader failure analysis
p = os.path.join(root, 'gl/shader.cpp')
s = open(p).read()
if 'FEAR-DOCTOR' in s:
    print("FEARPATCH SKIP: shader.cpp doctor analysis already present")
else:
    a = '#include "../config/config.h"'
    n = s.count(a)
    if n != 1:
        fail("shader.cpp config.h include anchor count = %d" % n)
    doctor_fn = a + '''
/* FEAR-DOCTOR (FV6): analyze a shader compile info log and name the class of
 * failure, with the fix that FearRender already applies for the known ones.
 * One glance at the latestlog tells the dev what to patch next. */
static void fear_doctor_analyze(const GLchar* log_) {
    if (log_ == nullptr) return;
    const std::string l(log_);
    bool named = false;
    if (l.find("S0001") != std::string::npos || l.find("layout qualifier") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: image-uniform layout/format issue (IMG auto-fix covers standard packs; if it persists, the pack uses an unusual image - send failed_shader_dump.glsl)");
        named = true;
    }
    if (l.find("S0059") != std::string::npos || l.find("r16ui") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: integer image format (r16ui-class) - storage is auto-rewritten to r32ui; if it persists the pack declares an unusual image");
        named = true;
    }
    if (l.find("L0001") != std::string::npos && l.find("uniform") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: uniform keyword mangling - the word-boundary guard covers standard cases; if it persists send the dump");
        named = true;
    }
    if (l.find("noperspective") != std::string::npos || l.find("NV_shader_noperspective") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: noperspective interpolation - auto-stripped to smooth; if it persists send the dump");
        named = true;
    }
    if (l.find("extension") != std::string::npos || l.find("not supported") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: unsupported extension/feature requested by the pack - GLES may lack it; send latestlog + dump");
        named = true;
    }
    if (l.find("S0032") != std::string::npos) {
        LOG_W_FORCE("FEAR-DOCTOR: too many uniforms/constants for one stage - pack is too large for this GPU; send the dump");
        named = true;
    }
    if (!named) {
        LOG_W_FORCE("FEAR-DOCTOR: unrecognized failure class - send latestlog + MG/failed_shader_dump.glsl to the dev");
    }
}
'''
    s = s.replace(a, doctor_fn, 1)

    a = '        LOG_W_FORCE("[FearRender] Shader %d compile FAILED: %s", shader, fearInfoLog)'
    n = s.count(a)
    if n != 1:
        fail("shader compile FAILED anchor count = %d" % n)
    s = s.replace(a, a + '\n        fear_doctor_analyze(fearInfoLog); /* FEAR-DOCTOR */', 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: shader.cpp FEAR-DOCTOR failure analysis added")

# ------------------------------------------ FV6: framebuffer status hints
p = os.path.join(root, 'gl/framebuffer.cpp')
s = open(p).read()
if 'FEAR-DOCTOR' in s:
    print("FEARPATCH SKIP: framebuffer.cpp doctor hints already present")
else:
    a = '        LOG_W_FORCE("FEARRENDER-FBODIAG: fbo=%u target=0x%x status=0x%x", fbo, target, status);'
    n = s.count(a)
    if n != 1:
        fail("FBODIAG status line anchor count = %d" % n)
    hints = a + '''
        if (status == GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
            LOG_W_FORCE("FEAR-DOCTOR: INCOMPLETE_ATTACHMENT - a colortex format is not renderable on this GPU (RGBA16/RGB16/RG16/R16/RGB16F/RGB32F are auto-rewritten; this target uses something else) - send this log");
        } else if (status == GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
            LOG_W_FORCE("FEAR-DOCTOR: MISSING_ATTACHMENT - the pack asked for a framebuffer with no image attached");
        } else if (status == GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER) {
            LOG_W_FORCE("FEAR-DOCTOR: INCOMPLETE_DRAW_BUFFER - a draw buffer has no attachment (MRT slot beyond the GLES limit?)");
        } else if (status == GL_FRAMEBUFFER_UNSUPPORTED) {
            LOG_W_FORCE("FEAR-DOCTOR: FRAMEBUFFER_UNSUPPORTED - the driver rejected the attachment combination - send this log");
        } else if (status == GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE) {
            LOG_W_FORCE("FEAR-DOCTOR: INCOMPLETE_MULTISAMPLE - sample-count mismatch in the pack's framebuffers");
        }'''
    s = s.replace(a, hints, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: framebuffer.cpp FEAR-DOCTOR status hints added")

# ------------------------------------------ FV7: universal renderability (FEAR-DOCTOR)
# ComplementaryReimagined r5.8.1 colortex formats (from pipelineSettings.glsl):
# colortex0=R11F_G11F_B10F, colortex1=RGB8_SNORM, colortex2=RGB16F. The failing
# FBO in latestlog-54 attached exactly draw buffers [0,1,2]: R11F needs
# EXT_color_buffer_float (missing on some Mali builds), SNORM is NEVER
# color-renderable in GLES, and every float rewrite also depends on
# EXT_color_buffer_float. So: probe each format ONCE at runtime with a
# throwaway FBO (state saved/restored so the app never notices) and rewrite
# non-renderable requests to the nearest renderable format. This makes shader
# packs run on any GPU, with or without the float extensions.
p = os.path.join(root, 'gl/texture.cpp')
s = open(p).read()
if 'FEARRENDER-FV7' in s:
    print("FEARPATCH SKIP: texture.cpp FV7 universal renderability already present")
else:
    a = '#include <cmath>'
    n = s.count(a)
    if n != 1:
        fail("texture.cpp cmath include anchor count = %d" % n)
    helpers = a + '''

// ---------------------- FEAR-DOCTOR (FV7): universal renderability ----------------------
// GLES drivers pick and choose which formats can be rendered to: SNORM is never
// color-renderable, float formats need EXT_color_buffer_float (missing on some
// Mali builds, e.g. the G615 r44 in latestlog-54). Probe each candidate once
// with a throwaway FBO and remember the verdict, then rewrite non-renderable
// requests to the nearest renderable format. GL state is saved/restored.
static int fear_probe_verdict(GLenum internal) {
    static GLenum fear_keys[24];
    static int fear_vals[24];
    static int fear_n = 0;
    for (int i = 0; i < fear_n; ++i) {
        if (fear_keys[i] == internal) return fear_vals[i];
    }
    GLenum probe_format = GL_RGBA;
    GLenum probe_type = GL_HALF_FLOAT;
    if (internal == GL_R11F_G11F_B10F) { probe_format = GL_RGB; probe_type = GL_UNSIGNED_INT_10F_11F_11F_REV; }
    else if (internal == GL_RGBA32F) { probe_type = GL_FLOAT; }
    else if (internal == GL_R16F) { probe_format = GL_RED; }
    else if (internal == GL_RG16F) { probe_format = GL_RG; }
    GLint fear_save_fbo = 0, fear_save_tex = 0;
    GLES.glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &fear_save_fbo);
    GLES.glGetIntegerv(GL_TEXTURE_BINDING_2D, &fear_save_tex);
    GLuint fear_tex = 0, fear_fbo = 0;
    GLES.glGenTextures(1, &fear_tex);
    GLES.glBindTexture(GL_TEXTURE_2D, fear_tex);
    GLES.glTexImage2D(GL_TEXTURE_2D, 0, (GLint)internal, 4, 4, 0, probe_format, probe_type, nullptr);
    GLES.glGenFramebuffers(1, &fear_fbo);
    GLES.glBindFramebuffer(GL_FRAMEBUFFER, fear_fbo);
    GLES.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fear_tex, 0);
    const int fear_ok = (GLES.glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    GLES.glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)fear_save_fbo);
    GLES.glBindTexture(GL_TEXTURE_2D, (GLuint)fear_save_tex);
    GLES.glDeleteFramebuffers(1, &fear_fbo);
    GLES.glDeleteTextures(1, &fear_tex);
    LOG_W_FORCE("FEAR-DOCTOR: FV7 probe 0x%x renderable: %s", (unsigned)internal,
                fear_ok ? "yes" : "NO (shaderpack formats using it will be rewritten)");
    if (fear_n < 24) { fear_keys[fear_n] = internal; fear_vals[fear_n] = fear_ok; ++fear_n; }
    return fear_ok;
}

// The HDR fallback: RGBA16F when floats are renderable, RGBA8 otherwise.
static GLenum fear_hdr_target() {
    static GLenum fear_hdr = 0;
    if (fear_hdr == 0) {
        fear_hdr = fear_probe_verdict(GL_RGBA16F) ? GL_RGBA16F : GL_RGBA8;
    }
    return fear_hdr;
}
// -------------------- end FEAR-DOCTOR (FV7) helpers --------------------
'''
    s = s.replace(a, helpers, 1)

    # ---- R11F_G11F_B10F (Complementary colortex0) ----
    a = """    case GL_R11F_G11F_B10F:
        if (type) *type = GL_UNSIGNED_INT_10F_11F_11F_REV;
        if (format) *format = GL_RGB;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("R11F case anchor count = %d" % n)
    s = s.replace(a, """    case GL_R11F_G11F_B10F: /* FEARRENDER-FV7: renderable only with EXT_color_buffer_float */
        if (type) *type = GL_UNSIGNED_INT_10F_11F_11F_REV;
        if (format) *format = GL_RGB;
        if (!fear_probe_verdict(GL_R11F_G11F_B10F)) {
            *internal_format = fear_hdr_target();
            if (format) *format = GL_RGBA;
            if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        }
        break;
""", 1)

    # ---- RGBA16F itself (fallback target of many FV4 rewrites) ----
    a = """    case GL_RGBA16F:
        if (type) *type = GL_HALF_FLOAT;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RGBA16F case anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGBA16F:
        if (type) *type = GL_HALF_FLOAT;
        if (!fear_probe_verdict(GL_RGBA16F)) { /* FEARRENDER-FV7 */
            *internal_format = GL_RGBA8;
            if (type) *type = GL_UNSIGNED_BYTE;
        }
        break;
""", 1)

    # ---- R16F / RG16F (single-channel float targets) ----
    a = """    case GL_R16F:
        if (format) *format = GL_RED;
        if (type) *type = GL_HALF_FLOAT;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("R16F case anchor count = %d" % n)
    s = s.replace(a, """    case GL_R16F:
        if (format) *format = GL_RED;
        if (type) *type = GL_HALF_FLOAT;
        if (!fear_probe_verdict(GL_R16F)) { /* FEARRENDER-FV7 */
            *internal_format = GL_R8;
            if (type) *type = GL_UNSIGNED_BYTE;
        }
        break;
""", 1)

    a = """    case GL_RG16F:
        if (format) *format = GL_RG;
        if (type) *type = GL_HALF_FLOAT;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RG16F case anchor count = %d" % n)
    s = s.replace(a, """    case GL_RG16F:
        if (format) *format = GL_RG;
        if (type) *type = GL_HALF_FLOAT;
        if (!fear_probe_verdict(GL_RG16F)) { /* FEARRENDER-FV7 */
            *internal_format = GL_RG8;
            if (type) *type = GL_UNSIGNED_BYTE;
        }
        break;
""", 1)

    # ---- RGBA32F (FV4 split it from RGB32F) ----
    a = """    case GL_RGBA32F:
        if (type) *type = GL_FLOAT;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RGBA32F case anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGBA32F:
        if (type) *type = GL_FLOAT;
        if (!fear_probe_verdict(GL_RGBA32F)) { /* FEARRENDER-FV7 */
            *internal_format = fear_hdr_target();
            if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        }
        break;
""", 1)

    # ---- upgrade the FV4 unconditional rewrites to probe-based ones ----
    a = """        *internal_format = GL_RGBA32F;
        if (type) *type = GL_FLOAT;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RGB32F rewrite anchor count = %d" % n)
    s = s.replace(a, """        *internal_format = fear_probe_verdict(GL_RGBA32F) ? GL_RGBA32F : fear_hdr_target();
        if (type) *type = (*internal_format == GL_RGBA32F || *internal_format == GL_RGBA16F) ? GL_FLOAT : GL_UNSIGNED_BYTE;
        break;
""", 1)

    a = """    case GL_RGBA16: { /* FEARRENDER-RENDERABLE: RGBA16 is not color-renderable on GLES (EXT_texture_norm16 adds the texture format only); rewrite to RGBA16F so shaderpack colortex targets stay attachable */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_FLOAT;
        break;
    }
"""
    n = s.count(a)
    if n != 1:
        fail("RGBA16 FV4 rewrite anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGBA16: { /* FEARRENDER-RENDERABLE + FV7: not color-renderable on GLES; nearest renderable target (probed at runtime) */
        *internal_format = fear_hdr_target();
        if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        break;
    }
""", 1)

    a = """    case GL_RGB16: { /* FEARRENDER-RENDERABLE: RGB16 not renderable on GLES; RGBA16F is (EXT_color_buffer_float) */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RGB;
        break;
    }
"""
    n = s.count(a)
    if n != 1:
        fail("RGB16 FV4 rewrite anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGB16: { /* FEARRENDER-RENDERABLE + FV7 */
        *internal_format = fear_hdr_target();
        if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        if (format) *format = GL_RGB;
        break;
    }
""", 1)

    a = """    case GL_RG16: { /* FEARRENDER-RENDERABLE: RG16 not renderable on GLES; RG16F is */
        *internal_format = GL_RG16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RG;
        break;
    }
"""
    n = s.count(a)
    if n != 1:
        fail("RG16 FV4 rewrite anchor count = %d" % n)
    s = s.replace(a, """    case GL_RG16: { /* FEARRENDER-RENDERABLE + FV7 */
        *internal_format = fear_probe_verdict(GL_RG16F) ? GL_RG16F : GL_RG8;
        if (type) *type = (*internal_format == GL_RG16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        if (format) *format = GL_RG;
        break;
    }
""", 1)

    a = """    case GL_R16: { /* FEARRENDER-RENDERABLE: R16 not renderable on GLES; R16F is */
        *internal_format = GL_R16F;
        if (type) *type = GL_FLOAT;
        if (format) *format = GL_RED;
        break;
    }
"""
    n = s.count(a)
    if n != 1:
        fail("R16 FV4 rewrite anchor count = %d" % n)
    s = s.replace(a, """    case GL_R16: { /* FEARRENDER-RENDERABLE + FV7 */
        *internal_format = fear_probe_verdict(GL_R16F) ? GL_R16F : GL_R8;
        if (type) *type = (*internal_format == GL_R16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        if (format) *format = GL_RED;
        break;
    }
""", 1)

    a = """    case GL_RGB16F: /* FEARRENDER-RENDERABLE: RGB16F renderability is driver-dependent; RGBA16F is guaranteed */
        *internal_format = GL_RGBA16F;
        if (type) *type = GL_HALF_FLOAT;
        if (format) *format = GL_RGB;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RGB16F FV4 rewrite anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGB16F: /* FEARRENDER-RENDERABLE + FV7: RGB16F is not renderable in core GLES; nearest probed target */
        *internal_format = fear_hdr_target();
        if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        if (format) *format = GL_RGB;
        break;
""", 1)

    # ---- SNORM formats: never color-renderable in GLES, rewrite to UNORM/float ----
    a = """    case GL_R8_SNORM:
        if (format) *format = GL_RED;
        if (type) *type = GL_BYTE;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("R8_SNORM case anchor count = %d" % n)
    s = s.replace(a, """    case GL_R8_SNORM: /* FEARRENDER-FV7: SNORM is never color-renderable in GLES */
        *internal_format = GL_R8;
        if (format) *format = GL_RED;
        if (type) *type = GL_UNSIGNED_BYTE;
        break;
""", 1)

    a = """    case GL_RG8_SNORM:
        if (format) *format = GL_RG;
        if (type) *type = GL_BYTE;
        break;
"""
    n = s.count(a)
    if n != 1:
        fail("RG8_SNORM case anchor count = %d" % n)
    s = s.replace(a, """    case GL_RG8_SNORM: /* FEARRENDER-FV7: SNORM is never color-renderable in GLES */
        *internal_format = GL_RG8;
        if (format) *format = GL_RG;
        if (type) *type = GL_UNSIGNED_BYTE;
        break;
""", 1)

    a = """    case GL_RGBA8_SNORM:
        if (format) *format = GL_RGBA;
        if (type) *type = GL_BYTE;
"""
    n = s.count(a)
    if n != 1:
        fail("RGBA8_SNORM case anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGBA8_SNORM: /* FEARRENDER-FV7: SNORM is never color-renderable in GLES; float target keeps negative values */
        *internal_format = fear_hdr_target();
        if (format) *format = GL_RGBA;
        if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
""", 1)

    # ---- NEW: RGB8_SNORM had no case at all (Complementary colortex1!) ----
    a = "    case GL_R8_SNORM:"
    n = s.count(a)
    if n != 1:
        fail("R8_SNORM insert anchor count = %d" % n)
    s = s.replace(a, """    case GL_RGB8_SNORM: /* FEARRENDER-FV7: Complementary colortex1; SNORM never renderable in GLES */
        *internal_format = fear_hdr_target();
        if (format) *format = GL_RGB;
        if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        break;
    case GL_R8_SNORM:""", 1)

    # ---- RGBA16_SNORM in the default block ----
    a = """        } else if (*internal_format == GL_RGBA16_SNORM) {
            if (type && *type != GL_SHORT) *type = GL_SHORT;
        }
"""
    n = s.count(a)
    if n != 1:
        fail("RGBA16_SNORM default anchor count = %d" % n)
    s = s.replace(a, """        } else if (*internal_format == GL_RGBA16_SNORM) { /* FEARRENDER-FV7 */
            *internal_format = fear_hdr_target();
            if (type) *type = (*internal_format == GL_RGBA16F) ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE;
        }
""", 1)

    open(p, 'w').write(s)
    print("FEARPATCH OK: texture.cpp FV7 universal renderability added (probe + 14 format rewrites)")

# ------------------------------------------ FV8: FEAR_FSR runtime override
# Per-launch FSR quality switch: the launcher profile's custom env can set
# FEAR_FSR (0=off/full res, 1=UltraQuality, 2=Quality, 3=Balanced,
# 4=Performance/half res) and it wins over the config.json value, so the user
# can trade sharpness for fps (or the reverse) without rebuilding anything.
p = os.path.join(root, 'config/settings.cpp')
s = open(p).read()
if 'FEARRENDER-FV8' in s:
    print("FEARPATCH SKIP: settings.cpp FEAR_FSR override already present")
else:
    a = '    global_settings.fsr1_setting = fsr1Setting;'
    n = s.count(a)
    if n != 1:
        fail("settings.cpp fsr1 apply anchor count = %d" % n)
    new = a + '''
    /* FEARRENDER-FV8: per-launch FSR override via custom env in the launcher
     * profile - FEAR_FSR=0 off (full native res), 1 UltraQuality, 2 Quality,
     * 3 Balanced, 4 Performance (half-res render, max fps). The env wins over
     * the config.json value so the user can switch without editing MG files. */
    const int fear_fsr_env = ReturnEnvVarIntDef("FEAR_FSR", -1);
    if (fear_fsr_env >= 0 && fear_fsr_env < static_cast<int>(FSR1_Quality_Preset::MaxValue)) {
        global_settings.fsr1_setting = static_cast<FSR1_Quality_Preset>(fear_fsr_env);
        LOG_W_FORCE("FEAR-DOCTOR: FEAR_FSR=%d env override applied (config value skipped)", fear_fsr_env);
    }
'''
    s = s.replace(a, new, 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: settings.cpp FEAR_FSR env override added")

# ------------------------------------------ FV9: FEAR-AUTOBALANCE adaptive resolution
# The performance system: keep the frame pace near the display's rhythm by
# stepping FSR1 quality down when fps sags and back up when there is headroom.
# One step at a time, a 3-heartbeat cooldown, and a confirmation heartbeat, so
# chunk-loading spikes never trigger it. Disabled by FEAR_AUTOBALANCE=0 or by
# pinning a preset with FEAR_FSR (FV8). Part A (FSR1.cpp) makes a preset change
# re-run the same resize path a surface-size change uses, and logs the actual
# render/upscale geometry; part B (egl.cpp) is the controller in the heartbeat.
p = os.path.join(root, 'gl/FSR1/FSR1.cpp')
s = open(p).read()
if 'FEARRENDER-FV9' in s:
    print("FEARPATCH SKIP: FSR1.cpp FV9 auto-recalc already present")
else:
    a = '    bool g_resolutionChanged = false;'
    n = s.count(a)
    if n != 1:
        fail("FSR1.cpp g_resolutionChanged anchor count = %d" % n)
    s = s.replace(a, a + '''
    FSR1_Quality_Preset g_appliedPreset = static_cast<FSR1_Quality_Preset>(-1); /* FEARRENDER-FV9 */''', 1)

    a = '''    if (FSR1_Context::g_resolutionChanged) {
        FSR1_Context::g_resolutionChanged = false;
        GLsizei width = FSR1_Context::g_pendingWidth;
        GLsizei height = FSR1_Context::g_pendingHeight;
        FSR1_Context::g_renderWidth = width;
        FSR1_Context::g_renderHeight = height;

        CalculateTargetResolution(global_settings.fsr1_setting, width, height,
                                  reinterpret_cast<int*>(&FSR1_Context::g_targetWidth),
                                  reinterpret_cast<int*>(&FSR1_Context::g_targetHeight));
        RecreateFSRFBO();
    }
'''
    n = s.count(a)
    if n != 1:
        fail("FSR1.cpp recalc block anchor count = %d" % n)
    s = s.replace(a, '''    if (FSR1_Context::g_resolutionChanged ||
        (FSR1_Context::g_appliedPreset != global_settings.fsr1_setting &&
         FSR1_Context::g_renderWidth > 0)) { /* FEARRENDER-FV9: preset changes re-run the resize path */
        FSR1_Context::g_resolutionChanged = false;
        GLsizei width = FSR1_Context::g_pendingWidth;
        GLsizei height = FSR1_Context::g_pendingHeight;
        FSR1_Context::g_renderWidth = width;
        FSR1_Context::g_renderHeight = height;

        CalculateTargetResolution(global_settings.fsr1_setting, width, height,
                                  reinterpret_cast<int*>(&FSR1_Context::g_targetWidth),
                                  reinterpret_cast<int*>(&FSR1_Context::g_targetHeight));
        FSR1_Context::g_appliedPreset = global_settings.fsr1_setting;
        RecreateFSRFBO();
        LOG_W_FORCE("FEAR-AUTOBALANCE: FSR preset %d applied | render %dx%d -> upscale %dx%d",
                    (int)FSR1_Context::g_appliedPreset,
                    (int)FSR1_Context::g_renderWidth, (int)FSR1_Context::g_renderHeight,
                    (int)FSR1_Context::g_targetWidth, (int)FSR1_Context::g_targetHeight);
    }
''', 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: FSR1.cpp FV9 preset-change recalc + geometry logging added")

# ---------------- FV9 part B: the controller, inside the FV6 heartbeat
p = os.path.join(root, 'egl/egl.cpp')
s = open(p).read()
if 'FEAR-AUTOBALANCE' in s:
    print("FEARPATCH SKIP: egl.cpp FV9 autobalance controller already present")
else:
    a = '''            const GLenum fear_err = GLES.glGetError();
            if (fear_err != GL_NO_ERROR) {
                LOG_W_FORCE("FEAR-PERF: GL error at present: 0x%x", fear_err);
            }
'''
    n = s.count(a)
    if n != 1:
        fail("egl.cpp heartbeat GL-error anchor count = %d" % n)
    s = s.replace(a, a + '''
            // FEAR-AUTOBALANCE (FV9): adaptive resolution governor. Keep the
            // frame pace near the display by stepping FSR1 quality one preset
            // at a time - down when fps sags, back up when there is headroom.
            // A confirmation heartbeat plus a 3-heartbeat cooldown keep chunk
            // loading spikes from triggering it. Kill switches: FEAR_AUTOBALANCE=0,
            // or pinning a preset with FEAR_FSR (which wins over this).
            {
                static int fear_ab_cooldown = 0;
                static int fear_ab_last_dir = 0;
                if (fear_ab_cooldown > 0) --fear_ab_cooldown;
                const char* fear_ab_kill = getenv("FEAR_AUTOBALANCE");
                const bool fear_ab_off = (fear_ab_kill != nullptr && strcmp(fear_ab_kill, "0") == 0);
                const bool fear_ab_pinned = (getenv("FEAR_FSR") != nullptr);
                const int fear_cur = static_cast<int>(global_settings.fsr1_setting);
                if (!fear_ab_off && !fear_ab_pinned && fear_cur >= 1) {
                    int fear_want = 0;
                    if (fear_fps < 40.0 && fear_cur < 4) fear_want = 1;   // more fps
                    else if (fear_fps > 57.0 && fear_cur > 1) fear_want = -1; // more sharpness
                    if (fear_want != 0 && fear_want == fear_ab_last_dir && fear_ab_cooldown == 0) {
                        const int fear_next = fear_cur + fear_want;
                        global_settings.fsr1_setting = static_cast<FSR1_Quality_Preset>(fear_next);
                        fear_ab_cooldown = 3;
                        fear_ab_last_dir = 0;
                        LOG_W_FORCE("FEAR-AUTOBALANCE: fps %.1f -> switching FSR preset to %d (%s)",
                                    fear_fps, fear_next,
                                    fear_next == 1 ? "UltraQuality" :
                                    fear_next == 2 ? "Quality" :
                                    fear_next == 3 ? "Balanced" : "Performance");
                    } else if (fear_want != 0) {
                        fear_ab_last_dir = fear_want; // first sighting: confirm on the next heartbeat
                    } else {
                        fear_ab_last_dir = 0;
                    }
                }
            }
''', 1)
    open(p, 'w').write(s)
    print("FEARPATCH OK: egl.cpp FV9 FEAR-AUTOBALANCE controller added")

print("FEARPATCH DONE")
