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
p = os.path.join(root, 'gl/glsl/glsl_for_es.cpp')
s = open(p).read()

a = 'LOG_D("GLSL Compiling ERROR: \\n%s", shader.getInfoLog())'
b = 'LOG_W_FORCE("[FearRender] GLSL(glslang)->SPIRV COMPILE ERROR:\\n%s", shader.getInfoLog())'
if a in s:
    s = s.replace(a, b, 1)
    print("FEARPATCH OK: glslang compile errors now always logged")
elif b in s:
    print("FEARPATCH SKIP: glslang compile errors already upgraded")
else:
    fail("glslang compile-error anchor not found")

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

print("FEARPATCH DONE")
