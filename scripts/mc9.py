import re, glob
# repair double-hash damage from alpha-prefixed hex remap: '#B3#FFFFFF' -> '#B3FFFFFF'
fixed = 0
for f in glob.glob('app_pojavlauncher/src/main/res/**/*.xml', recursive=True):
    t = open(f, encoding='utf-8').read()
    t2 = re.sub(r'#([0-9A-Fa-f]{2})#([0-9A-Fa-f]{6})', r'#\g<1>\g<2>', t)
    if t2 != t:
        open(f, 'w', encoding='utf-8', newline='').write(t2)
        fixed += 1
print('MC9 repaired:', fixed, 'files')
assert fixed >= 1
