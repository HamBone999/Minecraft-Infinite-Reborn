import sys, gzip, struct
def rd(b,o):
    t=b[o]; o+=1
    if t==0: return None,o,None
    ln=struct.unpack('>H',b[o:o+2])[0]; o+=2
    nm=b[o:o+ln].decode('utf8','replace'); o+=ln
    v,o=val(b,o,t); return nm,o,v
def val(b,o,t):
    if t==1: return struct.unpack('>b',b[o:o+1])[0],o+1
    if t==2: return struct.unpack('>h',b[o:o+2])[0],o+2
    if t==3: return struct.unpack('>i',b[o:o+4])[0],o+4
    if t==4: return struct.unpack('>q',b[o:o+8])[0],o+8
    if t==5: return struct.unpack('>f',b[o:o+4])[0],o+4
    if t==6: return struct.unpack('>d',b[o:o+8])[0],o+8
    if t==7:
        n=struct.unpack('>i',b[o:o+4])[0]; return None,o+4+n
    if t==8:
        n=struct.unpack('>H',b[o:o+2])[0]; return b[o+2:o+2+n].decode('utf8','replace'),o+2+n
    if t==9:
        it=b[o]; n=struct.unpack('>i',b[o+1:o+5])[0]; o+=5
        out=[]
        for _ in range(n):
            v,o=val(b,o,it); out.append(v)
        return out,o
    if t==10:
        out={}
        while True:
            nm,o,v=rd(b,o)
            if nm is None: return out,o
            out[nm]=v
    if t==11:
        n=struct.unpack('>i',b[o:o+4])[0]; return None,o+4+n*4
    raise ValueError(t)
data=open(sys.argv[1],'rb').read()
try: data=gzip.decompress(data)
except Exception: pass
_,_,root=rd(data,0)
pos=root.get('Pos'); dim=root.get('Dimension')
tp=[k for k in root if k.startswith(('input','output'))]
s=f"dim={dim}"
if pos: s+=f"  pos=({pos[0]:.0f},{pos[1]:.0f},{pos[2]:.0f})"
s+=f"  teleporter-keys={len(tp)}"
if 'Crawling' in root: s+=f"  Crawling={root['Crawling']}"
print(s)
