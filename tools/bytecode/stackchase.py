#!/usr/bin/env python3
"""
Map a stack trace frame to the exact bytecode it came from.

A crash report gives you Class.method(File.java:219). On a decompiled codebase that line
number belongs to the ORIGINAL source, which you do not have -- so it does not match your
decompiled file and you end up guessing which statement threw.

javap -l carries the real LineNumberTable. This resolves the frame to the bytecode offset
range for that line and prints the instructions, so you can see exactly which call threw.

    stackchase.py game.jar 'net.minecraft.client.gamemode.GameMode' 219
    stackchase.py game.jar --frame 'at net.minecraft.client.gamemode.GameMode.a(SourceFile:219)'
"""
import re, subprocess, sys, tempfile, zipfile, os

def dump(jar, cls):
    with zipfile.ZipFile(jar) as z:
        entry = cls.replace('.', '/') + '.class'
        if entry not in z.namelist():
            sys.exit(f"{entry} is not in {jar}")
        tmp = tempfile.mkdtemp()
        p = os.path.join(tmp, 'T.class')
        open(p, 'wb').write(z.read(entry))
    return subprocess.run(['javap', '-p', '-c', '-l', p], capture_output=True, text=True).stdout

def main(argv):
    args = [a for a in argv[1:] if not a.startswith('--')]
    if '--frame' in argv:
        frame = argv[argv.index('--frame') + 1]
        m = re.search(r'at ([\w.$]+)\.[\w<>$]+\([^:]*:(\d+)\)', frame)
        if not m: sys.exit("could not parse that frame")
        jar, cls, line = args[0], m.group(1), int(m.group(2))
    elif len(args) == 3:
        jar, cls, line = args[0], args[1], int(args[2])
    else:
        print(__doc__); return 2

    text = dump(jar, cls)
    cur, code, table, hits = None, [], [], []
    for raw in text.splitlines():
        s = raw.strip()
        if raw.startswith('  ') and s.endswith(');'):
            cur, code, table = s, [], []
        elif re.match(r'^\s+\d+:', raw):
            code.append(raw)
        elif re.match(r'^\s+line \d+: \d+$', raw):
            ln, off = map(int, re.findall(r'\d+', s))
            table.append((ln, off))
            if ln == line:
                hits.append((cur, list(code), list(table)))

    if not hits:
        lines = sorted({l for _, _, t in [(None, None, [])] for l in []})
        allines = sorted({int(x) for x in re.findall(r'line (\d+):', text)})
        sys.exit(f"line {line} is not in {cls}'s LineNumberTable. It has: {allines[:20]}"
                 + (" ..." if len(allines) > 20 else ""))

    for method, code, table in hits:
        starts = sorted(set(o for _, o in table))
        start = max(o for l, o in table if l == line)
        after = [o for o in starts if o > start]
        end = min(after) if after else 10 ** 9
        print(f"### {method}")
        print(f"    source line {line} -> bytecode offset {start}"
              + (f" .. {end - 1}" if end < 10 ** 9 else " .. end"))
        for c in code:
            off = int(re.match(r'^\s+(\d+):', c).group(1))
            if start <= off < end:
                print('   ' + c.rstrip())
        print()
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
