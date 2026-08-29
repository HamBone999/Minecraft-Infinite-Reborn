#!/bin/bash
# Verify the pinned jar, decompile only the classes we patch, snapshot them, apply patches.
# Safe to re-run: it always starts from a clean decompile.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

JAR=${JAR:-upstream/minecraft-infinite-server.jar}
JAVA8=${JAVA8:-/usr/lib/jvm/temurin-8-jre-amd64/bin/java}
JAVA17=${JAVA17:-/usr/lib/jvm/java-17-openjdk-amd64/bin/java}
VINEFLOWER=${VINEFLOWER:-tools/vineflower.jar}

[ -f "$JAR" ] || { echo "ERROR: $JAR not found"; exit 1; }
[ -f "$VINEFLOWER" ] || { echo "ERROR: $VINEFLOWER not found -- see README"; exit 1; }

echo "==> verifying the pinned jar"
if [ -f "$JAR.sha1" ]; then
   want=$(tr -d ' \n\r' < "$JAR.sha1"); got=$(sha1sum "$JAR" | cut -d' ' -f1)
   if [ "$want" != "$got" ]; then
      echo "ERROR: jar sha1 does not match the pin."
      echo "  expected $want"
      echo "  actual   $got"
      echo "Upstream changed. Re-pin deliberately and expect to rebase the patches."
      exit 1
   fi
   echo "    sha1 $got  OK"
else
   sha1sum "$JAR" | cut -d' ' -f1 > "$JAR.sha1"
   echo "    no pin existed, recorded $(cat "$JAR.sha1")"
fi

# The classes to decompile come from the patches themselves, so adding a patch is enough.
echo "==> classes referenced by patches/"
mapfile -t SRCS < <(grep -h '^+++ b/' patches/*.patch 2>/dev/null | sed 's|^+++ b/||' | sort -u)
[ ${#SRCS[@]} -gt 0 ] || { echo "ERROR: no patches found"; exit 1; }
printf '    %s\n' "${SRCS[@]}"

echo "==> extracting those classes (plus inner classes)"
rm -rf work; mkdir -p work/in work/src
for s in "${SRCS[@]}"; do
   base=${s%.java}
   (cd work/in && unzip -oq "$ROOT/$JAR" "$base.class" "$base\$*.class" 2>/dev/null) || true
done
find work/in -name '*.class' | sed "s|work/in/|    |"

echo "==> decompiling (Vineflower needs Java 11+; the game still targets Java 8)"
"$JAVA17" -jar "$VINEFLOWER" -e="$JAR" -dgs=1 -jrt=1 -log=WARN work/in/ work/src/ >/dev/null 2>&1

echo "==> snapshotting pristine sources"
cp -r work/src work/src-pristine

# Wholly new classes live in sources/ rather than as new-file patches: they are ours, not
# modifications of upstream, and a diff against /dev/null is a poor way to review 300 lines.
# Copied in AFTER the pristine snapshot so mkpatch never tries to diff them.
if [ -d sources ]; then
   echo "==> adding new sources"
   ( cd sources && find . -name '*.java' | sed 's|^\./|    |' )
   cp -r sources/. work/src/
fi

echo "==> applying patches"
for p in patches/*.patch; do
   printf '    %s ... ' "$(basename "$p")"
   ( cd work/src && patch -p1 --no-backup-if-mismatch -s < "$ROOT/$p" ) && echo "ok"
done
echo "==> done. Edit work/src, then scripts/mkpatch.sh to regenerate patches."
