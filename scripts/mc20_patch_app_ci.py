#!/usr/bin/env python3
"""MC20 diag patch v3 loader: assembles pairs from scripts/diag_parts/, then
patches osm_bridge.c starting from the pristine file at commit fffcc05,
sha1-verifies, and commits+pushes only on exact match."""
import glob
import hashlib
import subprocess
import sys
from pathlib import Path

PATH = 'app_pojavlauncher/src/main/jni/ctxbridges/osm_bridge.c'
URL = 'https://raw.githubusercontent.com/minetwice/FearLauncher/fffcc05/' + PATH
EXPECTED_SHA = 'caf5ab28ce7b3e9d455a050040cb82d52ef92874'

pairs = []
for f in sorted(glob.glob('scripts/diag_parts/part_*.py')):
    g = {}
    exec(compile(Path(f).read_text(), f, 'exec'), g)
    pairs.append((g['OLD'].decode('utf-8'), g['NEW'].decode('utf-8')))
if len(pairs) != 9:
    print('expected 9 parts, got %d' % len(pairs))
    sys.exit(1)

def main():
    p = Path(PATH)
    t = p.read_text()
    if hashlib.sha1(t.encode()).hexdigest() == EXPECTED_SHA:
        print('already patched (sha ok) - nothing to do')
        return
    print('downloading pristine from fffcc05 ...')
    bust = hashlib.sha1(t.encode()).hexdigest()[:8]
    r = subprocess.run(['curl', '-sSL', URL + '?v=' + bust],
                       capture_output=True, text=True, check=True)
    t = r.stdout
    if len(t) < 5000 or 'osm_swap_buffers' not in t:
        print('pristine download failed (%d bytes)' % len(t))
        sys.exit(1)
    for i, (old, new) in enumerate(pairs):
        if new in t:
            continue
        if old not in t:
            print('pair %d anchor missing: %s' % (i, old[:60]))
            sys.exit(1)
        t = t.replace(old, new, 1)
    got = hashlib.sha1(t.encode()).hexdigest()
    if got != EXPECTED_SHA:
        print('sha mismatch: %s (want %s)' % (got, EXPECTED_SHA))
        sys.exit(1)
    p.write_text(t)
    print('patched OK, sha %s' % got)
    subprocess.run(['git', 'add', PATH], check=True)
    q = subprocess.run(['git', 'diff', '--cached', '--quiet'])
    if q.returncode == 0:
        print('no changes to commit')
        return
    subprocess.run(['git', '-c', 'user.name=Twicefear',
                    '-c', 'user.email=ytd82774@gmail.com',
                    'commit', '-m',
                    'MC20 diag: osm_bridge OSMDIAG instrumentation (CI script v3)'], check=True)
    subprocess.run(['git', 'push'], check=True)
    print('committed + pushed')

main()
