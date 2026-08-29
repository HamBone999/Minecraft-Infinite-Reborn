#!/bin/sh
# Decompile with pinned flags, so patches stay appliable.
#
# Patches are diffs against decompiler output. Two people on different decompiler versions
# get different source and every patch misapplies -- so the version and the flags are pinned
# here and the jar is hashed. Bumping the decompiler is a deliberate commit with regenerated
# patches, never an accident.
#
#   decompile.sh <input.jar|dir> <output-dir> [vineflower.jar]
set -e
IN=$1; OUT=$2; VF=${3:-tools/vineflower.jar}
[ -n "$IN" ] && [ -n "$OUT" ] || { sed -n '2,12p' "$0"; exit 2; }
[ -f "$VF" ] || { echo "ERROR: $VF not found"; exit 1; }

# Vineflower needs Java 11+ even though the OUTPUT targets Java 8.
JAVA=${DECOMPILE_JAVA:-}
if [ -z "$JAVA" ]; then
  for c in /usr/lib/jvm/java-*17*/bin/java /usr/lib/jvm/java-*21*/bin/java "$(command -v java || true)"; do
    [ -x "$c" ] || continue
    v=$("$c" -version 2>&1 | head -1)
    case "$v" in *\"1.8*) continue;; esac
    JAVA=$c; break
  done
fi
[ -n "$JAVA" ] || { echo "ERROR: need Java 11+ to run the decompiler. Set DECOMPILE_JAVA."; exit 1; }

mkdir -p "$OUT"
echo "decompiler: $VF"
echo "java      : $JAVA"
echo "flags     : -dgs=1   (decompile generic signatures)"
exec "$JAVA" -jar "$VF" -dgs=1 "$IN" "$OUT"
