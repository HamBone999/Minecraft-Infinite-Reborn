#!/usr/bin/env python3
"""
Rewrite the version constant everywhere it was inlined, without recompiling.

The version is a `static final String` in SharedConstants, and javac INLINES those -- so it
is baked into 4 server classes and 7 client classes. Editing SharedConstants alone changes
nothing the game actually prints.

Replacement must be the SAME BYTE LENGTH, which is why the version format is kept as
1.0-DDMMYY: the constant pool stores a length prefix, and a same-length swap keeps every
entry valid without touching the pool.

Run this after ANY recompile of a client class, even when the version is not changing: the
decompiled source carries the old version as a literal, so a rebuild silently reintroduces it.

    reversion.py IN.jar OUT.jar 1.0-OLDVER 1.0-NEWVER
"""
import sys, zipfile, shutil, os
src, dst, old, new = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
assert len(old) == len(new), "replacement must be the same byte length"
pairs = [
    (b"Minecraft Infinite " + old.encode(), b"Minecraft Infinite " + new.encode()),
    (old.encode(), new.encode()),
    (old.split("-")[-1].encode(), new.split("-")[-1].encode()),
]
zin = zipfile.ZipFile(src)
if os.path.exists(dst): os.remove(dst)
zout = zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=6)
touched = []
for it in zin.infolist():
    data = zin.read(it.filename)
    if it.filename.endswith(".class"):
        orig = data
        for o, n in pairs:
            if len(o) == len(n):
                data = data.replace(o, n)
        if data != orig:
            touched.append(it.filename)
    zout.writestr(it, data)
zout.close()
print(f"  rewrote {len(touched)} classes:")
for t in touched: print(f"    {t}")
