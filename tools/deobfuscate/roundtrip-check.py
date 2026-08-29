#!/usr/bin/env python3
"""
The round-trip gate.

Some classes decompile to source that compiles fine but produces a DIFFERENT set of class
files, quietly dropping members or inner classes the original bytecode still references.
A patch against one of those ships a broken build with no compile error anywhere.

Seen on this project:
  * Minecraft.java loses Minecraft$2
  * Launchers$Kind (an enum) loses its synthetic $values() on a Java 8 round trip

This compares the member set of a recompiled class against the original. If it differs, that
class cannot safely be modified through source -- transform the bytecode or leave it alone.

    roundtrip-check.py ORIGINAL.jar RECOMPILED_DIR [class/name/Prefix ...]
"""
import os, subprocess, sys, tempfile, zipfile

def members(path):
    out = subprocess.run(['javap', '-p', path], capture_output=True, text=True).stdout
    return sorted(l.strip() for l in out.splitlines() if l.strip())

def main(argv):
    if len(argv) < 3:
        print(__doc__); return 2
    jar, built = argv[1], argv[2]
    prefixes = argv[3:]
    bad = ok = missing = 0
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for root, _, files in os.walk(built):
            for f in sorted(files):
                if not f.endswith('.class'): continue
                p = os.path.join(root, f)
                entry = os.path.relpath(p, built).replace(os.sep, '/')
                if prefixes and not any(entry.startswith(x) for x in prefixes): continue
                if entry not in names:
                    print(f"  ADDED    {entry}   (not in the original jar)")
                    missing += 1; continue
                tmp = tempfile.mkdtemp()
                o = os.path.join(tmp, 'O.class')
                open(o, 'wb').write(z.read(entry))
                a, b = members(o), members(p)
                if a == b:
                    ok += 1
                else:
                    bad += 1
                    print(f"  DIFFERS  {entry}")
                    for line in set(a) - set(b): print(f"      lost:  {line}")
                    for line in set(b) - set(a): print(f"      new:   {line}")
    print(f"\n{ok} identical, {bad} differing, {missing} new")
    return 1 if bad else 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
