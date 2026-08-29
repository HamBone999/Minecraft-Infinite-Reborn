import sys, struct, zlib, glob, os
def nbt_read(b,o):
    t=b[o]; o+=1
    if t==0: return None,o,None
    ln=struct.unpack('>H',b[o:o+2])[0]; o+=2
    nm=b[o:o+ln].decode('utf8','replace'); o+=ln
    v,o=nbt_val(b,o,t); return nm,o,v
def nbt_val(b,o,t):
    if t==1: return b[o],o+1
    if t==2: return struct.unpack('>h',b[o:o+2])[0],o+2
    if t==3: return struct.unpack('>i',b[o:o+4])[0],o+4
    if t==4: return struct.unpack('>q',b[o:o+8])[0],o+8
    if t==5: return struct.unpack('>f',b[o:o+4])[0],o+4
    if t==6: return struct.unpack('>d',b[o:o+8])[0],o+8
    if t==7:
        n=struct.unpack('>i',b[o:o+4])[0]; o+=4; return b[o:o+n],o+n
    if t==8:
        n=struct.unpack('>H',b[o:o+2])[0]; return b[o+2:o+2+n].decode('utf8','replace'),o+2+n
    if t==9:
        it=b[o]; n=struct.unpack('>i',b[o+1:o+5])[0]; o+=5
        out=[]
        for _ in range(n):
            v,o=nbt_val(b,o,it); out.append(v)
        return out,o
    if t==10:
        out={}
        while True:
            nm,o,v=nbt_read(b,o)
            if nm is None: return out,o
            out[nm]=v
    if t==11:
        n=struct.unpack('>i',b[o:o+4])[0]; o+=4; return b[o:o+n*4],o+n*4
    raise ValueError(f"tag {t}")

f=sys.argv[1]
d=open(f,'rb').read()
found=0
for i in range(1024):
    off,=struct.unpack('>I', b'\x00'+d[i*4:i*4+3])
    cnt=d[i*4+3]
    if off==0: continue
    st=off*4096
    ln=struct.unpack('>I',d[st:st+4])[0]; comp=d[st+4]
    raw=d[st+5:st+4+ln]
    try:
        raw = zlib.decompress(raw) if comp==2 else __import__('gzip').decompress(raw)
    except Exception: continue
    _,_,root=nbt_read(raw,0)
    lvl=root.get('Level') or root
    secs=lvl.get('Sections')
    if not secs: 
        print("  no Sections; keys:", list(lvl.keys())[:12]); break
    prof={}
    for s in secs:
        y0=s.get('Y',0)*16
        blocks=s.get('Blocks')
        if not blocks: continue
        for yy in range(16):
            solid=0
            for xx in range(16):
                for zz in range(16):
                    if blocks[yy*256+zz*16+xx]!=0: solid+=1
            if solid: prof[y0+yy]=solid
    ys=sorted(prof)
    print(f"  chunk with {len(secs)} sections: solid y-range {min(ys)}..{max(ys)}")
    band={}
    for y in ys:
        band[y//16]=band.get(y//16,0)+prof[y]
    for k in sorted(band): print(f"    y {k*16:>3}-{k*16+15:>3}: {band[k]:>6} solid blocks")
    found=1; break
if not found: print("  no chunks parsed")
