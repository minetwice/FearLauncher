#!/usr/bin/env python3
"""
Relocate the ASM library inside a shaded javaagent jar.

Why this exists
---------------
fear-skin-agent.jar is passed to the game with `-javaagent:`. The JVM appends
every javaagent jar to java.class.path. Fabric's LoaderUtil.verifyClasspath()
then walks the classpath and aborts the launch if it sees the same library
class twice. Because the agent shades the whole of ASM under its original
package (org/objectweb/asm), and the game already has asm-9.x.jar on its
classpath, the launch dies with:

    java.lang.IllegalStateException: duplicate ASM classes found on classpath

The fix is to move the agent's private copy of ASM into the agent's own
namespace (org/objectweb/asm -> com/fear/skin/libs/asm) so the two copies no
longer collide. The agent stays fully self-contained; the game needs nothing
on its classpath.

How
---
Every reference to a class name in a .class file lives in the constant pool as
a UTF8 entry (and every other entry references it by index, never by byte
offset). So rewriting those UTF8 strings in place -- and fixing up each
entry's length prefix -- is enough to rename a package, with no bytecode
rewriting and no JDK required. This script does exactly that for every .class
in the jar.

Usage:
    python3 relocate_asm.py <input.jar> <output.jar>
"""

import struct
import sys
import zipfile

OLD_DOTTED = b"org.objectweb.asm"
NEW_DOTTED = b"com.fear.skin.libs.asm"
OLD_SLASH = b"org/objectweb/asm"
NEW_SLASH = b"com/fear/skin/libs/asm"

# Constant-pool tag -> number of payload bytes after the tag byte.
# Tag 1 (UTF8) is variable-length and handled separately.
FIXED_TAG_SIZE = {
    3: 4,   # Integer
    4: 4,   # Float
    5: 8,   # Long      (occupies two constant-pool slots)
    6: 8,   # Double    (occupies two constant-pool slots)
    7: 2,   # Class
    8: 2,   # String
    9: 4,   # Fieldref
    10: 4,  # Methodref
    11: 4,  # InterfaceMethodref
    12: 4,  # NameAndType
    15: 3,  # MethodHandle
    16: 2,  # MethodType
    17: 4,  # Dynamic
    18: 4,  # InvokeDynamic
    19: 2,  # Module
    20: 2,  # Package
}


def _relocate_string(raw: bytes) -> bytes:
    if OLD_SLASH in raw:
        raw = raw.replace(OLD_SLASH, NEW_SLASH)
    if OLD_DOTTED in raw:
        raw = raw.replace(OLD_DOTTED, NEW_DOTTED)
    return raw


def relocate_classfile(data: bytes) -> bytes:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    cp_count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    out = bytearray(data[:10])
    index = 1
    while index < cp_count:
        tag = data[pos]
        if tag == 1:  # UTF8
            length = struct.unpack_from(">H", data, pos + 1)[0]
            text = data[pos + 3:pos + 3 + length]
            text = _relocate_string(text)
            out.append(1)
            out += struct.pack(">H", len(text))
            out += text
            pos += 3 + length
        else:
            size = FIXED_TAG_SIZE[tag]
            out += data[pos:pos + 1 + size]
            pos += 1 + size
        index += 2 if tag in (5, 6) else 1
    out += data[pos:]  # access flags, fields, methods, attributes -- untouched
    return bytes(out)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    changed = 0
    with zipfile.ZipFile(src, "r") as zin:
        infos = zin.infolist()
        with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in infos:
                name = info.filename
                # Drop the now-empty directory entries that belonged to the
                # old package; they carry no classes and only add noise.
                if name in ("org/", "org/objectweb/"):
                    continue
                data = zin.read(name)
                # 1. Move the entry to the relocated package path. A class's
                #    file path must match the name it declares, so the zip
                #    entry is renamed alongside the constant-pool rewrite.
                if name == "org/objectweb/asm" or name.startswith("org/objectweb/asm/"):
                    name = "com/fear/skin/libs/asm" + name[len("org/objectweb/asm"):]
                # 2. Rewrite class-name references inside every class file.
                if name.endswith(".class") and not name.endswith("module-info.class"):
                    new_data = relocate_classfile(data)
                    if new_data != data:
                        changed += 1
                    data = new_data
                zout.writestr(name, data)
    print(f"relocated {changed} class files -> {dst}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
