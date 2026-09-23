#!/usr/bin/env python3
"""MC20 diag patch v5 loader: assembles pairs from scripts/diag_parts/ (osm_bridge.c)
and scripts/hook_parts/ (lwjgl_dlopen_hook.c), sha-verifies, commits+pushes on exact match."""
import glob
import hashlib
import subprocess
import sys
from pathlib import Path

PATH = 'app_pojavlauncher/src/main/jni/ctxbridges/osm_bridge.c'
URL = 'https://raw.githubusercontent.com/minetwice/FearLauncher/fffcc05/' + PATH
EXPECTED_SHA = '5a070a3f8242cf57b2c66caa4176abdc2bdffbcd'
MARKER = 'osm_swap_buffers'

HOOKPATJH	Ø\ÜÚ˜]›][˜Ú\‹ÜÜ˜ËÛXZ[‹Ú›šKÚ›WÚÛÚÜËÛÚ™ÛÙÜ[—ÚÛÚË˜ÉÂ’ÓÒÕU$ÂÒv‡GG3¢ò÷&ræv—F‡V'W6W&6öçFVçBæ6öÒöÖ–æWGv–6RôfV$ÆVæ6†W"ó6Fc“S–†6C&3“c3†S6cs3vVC“#3C&##FRòr²„ôôµD )!==-}aAQ}M!€ô€œàÕ„ØàÄÌäÅ•ˆáˆÔÄäàÌÄĞÍ‘”ÙÌĞá”åÀÍˆÕŒÈäÍŒœ)!==-5AKER = 'hooked_glfwGetProcAddress_impl'


def apply_pairs(path, url, expected, globpat, n_expected, tag, marker):
    pairs = []
    for f in sorted(glob.glob(globpat)):
        g = {}
        exec(compile(Path(f).read_text(), f, 'exec'), g)
        pairs.append((g['OLD'].decode(), g['NEW'].decode()))
    if len(pairs) != n_expected:
        print(tag + ': expected ' + str(n_expected) + ' parts, got ' + str(len(pairs)))
        sys.exit(1)
    p = Path(path)
    t = p.read_bytes().decode('utf-8', 'replace')
    if hashlib.sha1(t.encode()).hexdigest() == expected:
        print(tag + ': already patched (sha ok) - nothing to do')
        return False
    print(tag + ': downloading pristine ...')
    bust = hashlib.sha1(t.encode()).hexdigest()[:8]
    r = subprocess.run(['curl', '-sL', url + '?v=' + bust],
                       capture_output=True, text=True)
    t = r.stdout
    if len(t) < 5000 or marker not in t:
        print(tag + ': pristine download failed (' + str(len(t)) + ' bytes)')
        sys.exit(1)
    for i, (o, n) in enumerate(pairs):
        if n in t:
            continue
        if o not in t:
            print(tag + ' pair ' + str(i) + ' anchor missing: ' + o[:60])
            sys.exit(1)
        t = t.replace(o, n, 1)
    got = hashlib.sha1(t.encode()).hexdigest()
    if got != expected:
        print(tag + ': sha mismatch: ' + got + ' (want ' + expected + ')')
        sys.exit(1)
    p.write_text(t)
    print(tag + ': patched OK, sha ' + got)
    return True


def main():
    a = apply_pairs(PATH, URL, EXPECTED_SHA, 'scripts/diag_parts/part_*.py', 14, 'osm', MARKER)
    b = apply_pairs(HOOOPATH, HOOKURL, HOOK_EXPECTED_SHA, 'scripts/hook_parts/hook_*.py', 3, 'hook', HOOKMARKER)
    if not (a or b):
        print('no changes to commit')
        return
    subprocess.run(['git', 'add', PATH, HOOOPATH], check=True)
    q = subprocess.run(['git', 'diff', '--cached', '--quiet'])
    if q.returncode == 0:
        print('no changes to commit')
        return
    subprocess.run(['git', '-c', 'user.name=Twicefear',
                    '-c', 'user.email=ytd82774@gmail.com',
                    'commit', '-m',
                    'MC20 diag v2.5: glBlitFramebuffer CPU fallback (CI v9)'], check=True)
    subprocess.run(['git', 'push'], check=True)
    print('committed + pushed')

main()
