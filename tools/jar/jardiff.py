#!/usr/bin/env python3
"""
Entry-level diff of two jars: what changed, what was added, what vanished.

The check to run before shipping any overlay build. On this project it is what proved a
release contained exactly the intended 9 changed classes out of 7,772 entries.

    jardiff.py OLD.jar NEW.jar [--summary]
"""
import hashlib, sys, zipfile

def main(argv):
    if len(argv) < 3:
        print(__doc__); return 2
    a, b = zipfile.ZipFile(argv[1]), zipfile.ZipFile(argv[2])
    na, nb = set(a.namelist()), set(b.namelist())
    changed = [n for n in sorted(na & nb)
               if hashlib.sha1(a.read(n)).digest() != hashlib.sha1(b.read(n)).digest()]
    added, removed = sorted(nb - na), sorted(na - nb)
    if '--summary' not in argv:
        for n in added:   print(f"  + {n}")
        for n in removed: print(f"  - {n}")
        for n in changed: print(f"  ~ {n}")
    print(f"\n{len(changed)} changed, {len(added)} added, {len(removed)} removed, "
          f"{len(na)} -> {len(nb)} entries")
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
