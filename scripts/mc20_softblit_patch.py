#!/usr/bin/env python3
"""MC20 soft-blit patch (panfork). Args: path to mesa root. Idempotent."""
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'mesa')
assert (root / 'src').exists(), 'not a mesa checkout'

data_dir = Path(__file__).parent
ns = {}
for name in ('mc20_softblit_data.py', 'mc20_softblit_data2.py', 'mc20_softblit_data3.py'):
    exec((data_dir / name).read_text(), ns)

# patch 1: pan_blit.c
p = root / 'src/gallium/drivers/panfrost/pan_blit.c'
t = p.read_text()
if 'panfrost_soft_blit' in t:
    print('pan_blit.c: already patched')
else:
    NEW_INC = ns['NEW_INC_A'] + ns['NEW_INC_B']
    assert ns['OLD_INC'] in t, 'pan_blit.c include anchor missing'
    t = t.replace(ns['OLD_INC'], NEW_INC, 1)
    assert ns['OLD_BLIT'] in t, 'panfrost_blit anchor missing'
    t = t.replace(ns['OLD_BLIT'], ns['NEW_BLIT'], 1)
    p.write_text(t)
    print('pan_blit.c: soft-blit patched')

# patch 2: mesa main/blit.c
p = root / 'src/mesa/main/blit.c'
t = p.read_text()
if 'MC20BLIT' in t:
    print('blit.c: already patched')
else:
    fi = t.index('#include')
    t = t[:fi] + '#include <stdio.h>\n' + t[fi:]
    if ns['OLD_DST'] not in t:
        old = ns['OLD_DST'].replace('            if (dstSurf) {', '         if (dstSurf) {')
        old = old.replace('   blit.dst.resource', '      blit.dst.resource')
        ns['OLD_DST'] = old
    assert ns['OLD_DST'] in t, 'blit.c color dst anchor missing'
    t = t.replace(ns['OLD_DST'], ns['NEW_DST'], 1)
    p.write_text(t)
    print('blit.c: MC20BLIT diagnostics patched')

print('soft-blit patch: OK')
