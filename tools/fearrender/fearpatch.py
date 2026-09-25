#!/usr/bin/env python3
"""FEARPATCH: MobileGlues source patches for FearRender (idempotent per fresh clone).

Patches applied to the MobileGlues source tree before building:

1. gl/glsl/glsl_for_es.cpp - make translator errors ALWAYS visible.
   The stock build hides glslang / SPIRV-Cross errors behind debug-only
   LOG_E/LOG_D. When translation fails, the ORIGINAL desktop GLSL is passed
   to the GLES driver, which then fails with a confusing error such as
   '0:948: L0001: Expected identifier, found uniform'. Surface the real cause.

2. gl/shader.cpp - on shader compile failure, log the driver info log and
   append the full source that was passed to the driver to
   <MG_DIR>/failed_shader_dump.glsl so the exact breaking GLSL/ESSL can be
   inspected afterwards.

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

print("FEARPATCH DONE")
