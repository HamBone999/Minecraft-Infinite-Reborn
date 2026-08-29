#!/usr/bin/env python3
"""
Which entries in a jar contain a given string?

The workhorse for "where does this message come from" and "what still says the old version".
Reads the constant pool as raw bytes, so it finds strings in classes as well as resources.

    findstring.py game.jar '1.0-300826'
    findstring.py game.jar --regex '1\\.0-[0-9]{6}'    # print every match, with counts
"""
import re, sys, zipfile

def main(argv):
    if len(argv) < 3:
        print(__doc__); return 2
    jar = argv[1]
    regex = '--regex' in argv
    pat = argv[-1]
    rx = re.compile(pat.encode() if regex else re.escape(pat).encode())
    counts, hits = {}, 0
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith('/'): continue
            data = z.read(n)
            found = rx.findall(data)
            if found:
                hits += 1
                print(f"  {n}")
                for f in sorted(set(found)):
                    s = f.decode('utf-8', 'replace')
                    counts[s] = counts.get(s, 0) + found.count(f)
                    if regex: print(f"      {s}")
    print(f"\n{hits} entr{'y' if hits==1 else 'ies'}")
    if regex and counts:
        print("distinct matches:")
        for k, v in sorted(counts.items()):
            print(f"  {v:5d}  {k}")
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
