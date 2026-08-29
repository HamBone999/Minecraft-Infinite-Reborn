#!/bin/bash
# Compile the patched classes and overlay them onto a copy of the upstream jar.
#
# Only patched classes are replaced. Everything else in the jar is untouched, so the
# output differs from upstream by exactly the classes we changed and nothing else.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

JAR=${JAR:-upstream/minecraft-infinite-server.jar}
JDK8=${JDK8:-/usr/lib/jvm/temurin-8-jdk-amd64/bin}
OUT=${OUT:-out/minecraft-infinite-server-patched.jar}

[ -d work/src ] || { echo "ERROR: run scripts/setup.sh first"; exit 1; }

echo "==> compiling patched sources (Java 8 target)"
rm -rf work/classes; mkdir -p work/classes
"$JDK8/javac" -source 8 -target 8 -proc:none -nowarn -encoding UTF-8 \
   -cp "$JAR" -d work/classes $(find work/src -name '*.java')

echo "==> classes rebuilt:"
find work/classes -name '*.class' | sed "s|work/classes/|    |"

echo "==> overlaying onto a copy of upstream"
mkdir -p "$(dirname "$OUT")"
cp "$JAR" "$OUT"
( cd work/classes && "$JDK8/jar" uf "$ROOT/$OUT" $(find . -name '*.class' | sed 's|^\./||') )

echo "==> verifying only the intended entries changed"
python3 - "$JAR" "$OUT" <<'PY'
import sys, zipfile, hashlib
a, b = (zipfile.ZipFile(p) for p in sys.argv[1:3])
na, nb = set(a.namelist()), set(b.namelist())
added = nb - na
changed = [n for n in sorted(na & nb)
           if hashlib.sha1(a.read(n)).digest() != hashlib.sha1(b.read(n)).digest()]
for n in sorted(added):   print(f"    + {n}")
for n in changed:         print(f"    ~ {n}")
print(f"    {len(changed)} changed, {len(added)} added, {len(na - nb)} removed")
PY
echo "==> $OUT"
sha1sum "$OUT" | sed 's/^/    /'
