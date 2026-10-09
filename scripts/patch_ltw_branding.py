#!/usr/bin/env python3
"""Build-time, same-length rebranding of the upstream LTW renderer's strings.

The LTW renderer ships as a prebuilt AAR (``ltw-release.aar``) whose
``jni/<abi>/libltw.so`` leaks the upstream launcher's name into the strings the
game reads back from OpenGL:

    GL_VENDOR            -> "MojoLauncher"
    renderer/GL version  -> "%u.%u OpenLTW (Built on: ...)"

Both substitutions below are EXACTLY the same byte length as the originals, so
the size, layout, offsets and code of the shared object are untouched - only the
bytes of those literals change. This is why a build-time byte patch is safe,
unlike a runtime hook into ``glGetString``.

Rules enforced here:
  * a length mismatch between original and replacement is FATAL (we refuse to
    patch, because it would change the binary);
  * a string that is simply absent is logged and skipped (the upstream binary
    may change) - the build must never fail just because a string moved;
  * the patched ``.so`` is asserted to be byte-for-byte the same length as the
    original before the AAR is rewritten.

Nothing here touches app source or any other native file.
"""

import os
import sys
import zipfile

# (original, replacement) - byte lengths MUST match.
REPLACEMENTS = [
    (b"MojoLauncher", b"FearLauncher"),  # 12 -> 12  (GL_VENDOR)
    (b"OpenLTW", b"FearLTW"),            #  7 ->  7  (version banner substring)
]

# Only entries matching this predicate inside the AAR are considered.
SO_SUFFIX = "/libltw.so"


def patch_blob(data, label):
    """Return (new_data, lines). Never changes the length."""
    lines = []
    for orig, repl in REPLACEMENTS:
        if len(orig) != len(repl):
            raise SystemExit(
                "FATAL: length mismatch for %r (%d bytes) -> %r (%d bytes); "
                "refusing to patch." % (orig, len(orig), repl, len(repl))
            )
        count = data.count(orig)
        if count == 0:
            lines.append("      [not found] %-14s (nothing to do)" % orig.decode())
            continue
        data = data.replace(orig, repl)
        lines.append(
            "      [patched]   %-14s -> %-14s : %d occurrence(s)"
            % (orig.decode(), repl.decode(), count)
        )
    return data, lines


def patch_aar(path):
    if not os.path.isfile(path):
        print("  !! AAR not found: %s - skipping" % path)
        return False

    print("== LTW AAR: %s" % path)
    with zipfile.ZipFile(path) as zin:
        infos = zin.infolist()
        blobs = {i.filename: zin.read(i.filename) for i in infos}

    patched_entries = 0
    for name in list(blobs):
        if not (name.startswith("jni/") and name.endswith(SO_SUFFIX)):
            continue
        orig = blobs[name]
        new, lines = patch_blob(orig, name)
        print("   %s" % name)
        for line in lines:
            print(line)
        if len(new) != len(orig):
            raise SystemExit(
                "FATAL: %s changed size %d -> %d; aborting (would alter the binary)."
                % (name, len(orig), len(new))
            )
        if new != orig:
            blobs[name] = new
            patched_entries += 1

    if patched_entries == 0:
        print("   (no libltw.so entry changed)")
        return False

    # Rebuild the AAR, preserving entry order, names and per-entry metadata.
    tmp = path + ".patched"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for i in infos:
            zout.writestr(i, blobs[i.filename])
    os.replace(tmp, path)
    print("   -> rewrote AAR with %d patched libltw.so entr(y/ies)" % patched_entries)
    return True


def main(argv):
    targets = argv or []
    if not targets:
        print("No AAR paths given; nothing to patch.")
        return 0
    any_patched = False
    for path in targets:
        try:
            any_patched = patch_aar(path) or any_patched
        except SystemExit:
            raise
    print("== LTW branding patch complete (patched=%s)" % ("yes" if any_patched else "no"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
