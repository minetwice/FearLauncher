import re, glob

V = 'app_pojavlauncher/src/main/res/values/'
seen = set()
removed_total = 0
order = ['strings.xml'] + sorted(p.split('/')[-1] for p in glob.glob(V + 'strings_*.xml'))
for fname in order:
    path = V + fname
    t = open(path, encoding='utf-8').read()
    entries = re.findall(r'<string name="([^"]+)"[^>]*>.*?</string>\n?', t, re.S)
    names = set(entries)
    dups = names & seen
    for name in dups:
        t2 = re.sub(r'[ \t]*<string name="%s"[^>]*>.*?</string>\n' % re.escape(name), '', t, count=1)
        assert ('<string name="%s"' % name) not in t2, 'failed to remove ' + name
        t = t2
    seen |= (names - dups)
    removed_total += len(dups)
    open(path, 'w', encoding='utf-8', newline='').write(t)
    if dups:
        print('%s: removed %d duplicates' % (fname, len(dups)))

allnames = []
for fname in order:
    allnames += re.findall(r'<string name="([^"]+)"[^>]*>', open(V + fname, encoding='utf-8').read())
assert len(allnames) == len(set(allnames)), 'duplicates remain!'
print('DEDUP OK, total removed:', removed_total)
