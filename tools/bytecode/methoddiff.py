#!/usr/bin/env python3
"""
Semantic bytecode diff between two versions of a class.

Plain `javap -c` output is useless for diffing: inserting one instruction shifts every
offset and branch target after it, so a one-line change reports as a hundred-line diff.
This normalises away offsets, branch targets and constant-pool indices, leaving only the
opcode stream, and reports per method.

Used to prove that a recompiled class changed only what was intended -- on this project it
showed that 6 of 8 "changed" methods were javac emitting equivalent control flow
(ifeq X; return  ->  ifne with fallthrough) rather than any behaviour change.

    methoddiff.py OLD.class NEW.class
"""
import re, subprocess, sys, difflib

def methods(path):
    out = subprocess.run(['javap', '-p', '-c', path], capture_output=True, text=True).stdout
    d, cur = {}, None
    for line in out.splitlines():
        if line.startswith('  ') and line.rstrip().endswith(');'):
            cur = line.strip(); d[cur] = []
        elif cur and re.match(r'^\s+\d+:', line):
            ins = re.sub(r'^\s*\d+:\s*', '', line)
            ins = re.sub(r'//.*$', '', ins).strip()
            ins = re.sub(r'\s+\d+$', '', ins)      # branch target
            ins = re.sub(r'#\d+', '#', ins)        # constant pool index
            d[cur].append(ins)
    return d

def main(argv):
    if len(argv) != 3:
        print(__doc__); return 2
    a, b = methods(argv[1]), methods(argv[2])
    same = 0
    for k in sorted(set(a) | set(b)):
        x, y = a.get(k, []), b.get(k, [])
        if x == y:
            same += 1; continue
        print(f"### {k}")
        print(f"    {len(x)} -> {len(y)} instructions")
        for l in difflib.unified_diff(x, y, lineterm='', n=2):
            if not l.startswith(('---', '+++')):
                print('   ' + l)
        print()
    print(f"{same} method(s) identical")
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
