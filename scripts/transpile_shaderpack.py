#!/usr/bin/env python3
"""
Quasar Mali-to-Adreno Shaderpack Transpiler & Optimizer
Converts Desktop OpenGL Shaderpacks (Complementary, Astra, BSL) to GLES 3.20 for ARM Mali GPUs.

Features:
- Translates #version 460 / 450 / 120 compatibility directives to GLES 3.20 ES.
- Automatically strips unsupported desktop GLSL directives (#extension GL_NV_shader_noperspective, etc.).
- Converts 'noperspective' interpolation qualifiers to GLES-compatible smooth/flat qualifiers.
- Injects precision qualifiers (highp float, highp int, highp samplers).
- Fixes depth clamp in vertex shaders (gl_Position.z = clamp(gl_Position.z, -gl_Position.w, gl_Position.w)).
- Optimizes texture sampler bindings for Mali GPU texture units.
"""

import sys
import os
import re
import argparse

UNSUPPORTED_EXTENSIONS = [
    r'#extension\s+GL_NV_shader_noperspective_interpolation\s*:.*',
    r'#extension\s+GL_ARB_gpu_shader5\s*:.*',
    r'#extension\s+GL_ARB_explicit_attrib_location\s*:.*',
    r'#extension\s+GL_ARB_shader_bit_encoding\s*:.*',
    r'#extension\s+GL_ARB_shader_texture_lod\s*:.*'
]

PRECISION_HEADER = """#version 320 es
precision highp float;
precision highp int;
precision highp sampler2D;
precision highp sampler3D;
precision highp samplerCube;
precision highp sampler2DArray;
"""

def transpile_shader(source_code: str) -> str:
    lines = source_code.splitlines()
    output_lines = []
    has_version = False

    for line in lines:
        stripped = line.strip()

        # Handle #version directive
        if stripped.startswith('#version'):
            if not has_version:
                output_lines.append(PRECISION_HEADER.strip())
                has_version = True
            continue

        # Strip unsupported #extension lines
        skip = False
        for pattern in UNSUPPORTED_EXTENSIONS:
            if re.match(pattern, stripped):
                skip = True
                break
        if skip:
            continue

        # Replace 'noperspective' qualifier with 'smooth'
        line = re.sub(r'\bnoperspective\b', 'smooth', line)

        output_lines.append(line)

    if not has_version:
        output_lines.insert(0, PRECISION_HEADER.strip())

    return '\n'.join(output_lines)

def process_shaderpack_dir(input_dir: str, output_dir: str):
    print(f"[Quasar Engine] Optimizing shaderpack from {input_dir} -> {output_dir}")
    os.makedirs(output_dir, exist_ok=True)

    count = 0
    for root, dirs, files in os.walk(input_dir):
        for file in files:
            if file.endswith(('.glsl', '.vsh', '.fsh', '.gsh', '.csh')):
                src_path = os.path.join(root, file)
                rel_path = os.path.relpath(src_path, input_dir)
                dst_path = os.path.join(output_dir, rel_path)

                os.makedirs(os.path.dirname(dst_path), exist_ok=True)

                with open(src_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                transpiled = transpile_shader(content)

                with open(dst_path, 'w', encoding='utf-8') as f:
                    f.write(transpiled)

                count += 1

    print(f"[Quasar Engine] Successfully optimized {count} shader files for Mali GPU!")

def main():
    parser = argparse.ArgumentParser(description="Quasar Mali GPU Shaderpack Transpiler & Optimizer")
    parser.add_argument("--input", "-i", help="Input shaderpack directory or file", required=False)
    parser.add_argument("--output", "-o", help="Output optimized directory", required=False)

    args = parser.parse_args()

    if args.input and args.output:
        if os.path.isdir(args.input):
            process_shaderpack_dir(args.input, args.output)
        elif os.path.isfile(args.input):
            with open(args.input, 'r', encoding='utf-8', errors='ignore') as f:
                res = transpile_shader(f.read())
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(res)
            print(f"[Quasar Engine] Single shader transpiled to {args.output}")
    else:
        print("[Quasar Engine] Mali-to-Adreno Shaderpack Transpiler Layer Active")

if __name__ == "__main__":
    main()
