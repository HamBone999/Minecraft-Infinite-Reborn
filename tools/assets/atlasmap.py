#!/usr/bin/env python3
"""
Map which 16x16 cells of a GUI atlas are used, so you can find a free slot for a new icon.

Button icons come out of /gui/gui.png on a 16x16 grid, and each icon needs TWO cells: the
normal one at (x, y) and its hover variant 16 pixels below at (x, y+16). Guessing a free
slot silently overwrites something; this shows you.

    atlasmap.py gui.png              # which cells are occupied
    atlasmap.py gui.png --free       # which cell PAIRS are free for a new icon
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import read_png

def main(argv):
    if len(argv) < 2:
        print(__doc__); return 2
    w, h, px = read_png(argv[1])
    cw, ch = w // 16, h // 16
    used = [[False] * cw for _ in range(ch)]
    for cy in range(ch):
        for cx in range(cw):
            for y in range(cy * 16, cy * 16 + 16):
                for x in range(cx * 16, cx * 16 + 16):
                    if px[(y * w + x) * 4 + 3]:
                        used[cy][cx] = True; break
                if used[cy][cx]: break
    print(f"  {w}x{h}, {cw}x{ch} cells of 16px\n")
    if '--free' in argv:
        print("  free icon slots (cell and its hover row both empty):")
        n = 0
        for cy in range(ch - 1):
            for cx in range(cw):
                if not used[cy][cx] and not used[cy + 1][cx]:
                    print(f"    icon at ({cx*16}, {cy*16}), hover at ({cx*16}, {cy*16+16})")
                    n += 1
        print(f"  {n} free slot(s)")
    else:
        for cy in range(ch):
            cols = [cx for cx in range(cw) if used[cy][cx]]
            if cols: print(f"    y={cy*16:3d}: columns {cols}")
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
