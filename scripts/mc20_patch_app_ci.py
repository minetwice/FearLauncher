#!/usr/bin/env python3
"""MC20 diag patch v5 loader (osm_bridge.c + lwjgl_dlopen_hook.c)."""
import glob
import hashlib
import subprocess
import sys
from pathlib import Path


def apply(path, url, expected, pat, n, tag, marker):
    pairs = []
    for f in sorted(glob.glob(pat)):
        g = {}
        exec(compile(Path(f).read_text(), f, 'exec'), g)
        pairs.append((g['OLD'].decode(), g['NEW'].decode()))
    if len(pairs) != n:
        print(tag + ': parts ' + str(len(pairs)) + ' != ' + str(n))
        sys.exit(1)
    p = Path(path)
    t = p.read_bytes().decode('utf-8', 'replace')
    if hashlib.sha1(t.encode()).hexdigest() == expected:
        print(tag + ': already ok')
        return False
    print(tag + ': downloading pristine ...')
    r = subprocess.run(['curl', '-sL', url + '?v=' + str(len(t))], capture_output=True, text=True)
    t = r.stdout
    if len(t) < 5000 or marker not in t:
        print(tag + ': download failed ' + str(len(t)))
        sys.exit(1)
    for i, (o, n2) in enumerate(pairs):
        if n2 in t:
            continue
        if o not in t:
            print(tag + ' anchor ' + str(i) + ': ' + o[:50])
            sys.exit(1)
        t = t.replace(o, n2, 1)
    got = hashlib.sha1(t.encode()).hexdigest()
    if got != expected:
        print(tag + ': sha ' + got + ' want ' + expected)
        sys.exit(1)
    p.write_text(t)
    print(tag + ': patched ' + got)
    return True


def main():
    a = apply('app_pojavlauncher/src/main/jni/ctxbridges/osm_bridge.c',
              'https://raw.githubusercontent.com/minetwice/FearLauncher/fffcc05/app_pojavlauncher/src/main/jni/ctxbridges/osm_bridge.c',
              '5a070a3f8242cf57b2c66caa4176abdc2bdffbcd',
              'scripts/diag_parts/part_*.py', 14, 'osm', 'osm_swap_buffers')
    b = apply('app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c',
              'https://raw.githubusercontent.com/minetwice/FearLauncher/3df959a8cd2c961c8e3a06737ed1a923402b1b4e/app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c',
              'e238728c42d58f43fe8ea37ec6b0eac1787dd2a5',
              'scripts/hook_parts/hook_*.py', 3, 'hook', 'hooked_glfwGetProcAddress_impl')
    if not (a or b):
        print('no changes')
        return
    subprocess.run(['git', 'add',
                    'app_pojavlauncher/src/main/jni/ctxbridges/osm_bridge.c',
                    'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'], check=True)
    if subprocess.run(['git', 'diff', '--cached', '--quiet']).returncode == 0:
        print('no changes')
        return
    subprocess.run(['git', '-c', 'user.name=Twicefear',
                    '-c', 'user.email=ytd82774@gmail.com',
                    'commit', '-m',
                    'MC20 diag v2.7: glBlitNamedFramebuffer CPU fallback + req log (CI v11)'],
                   check=True)
    subprocess.run(['git', ''push'], check=True)
    print('pushed')


main()
