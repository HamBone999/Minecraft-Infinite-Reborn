#!/bin/bash
# Regenerate patches/ from the current state of work/src against the pristine snapshot.
# One patch file per changed source file, numbered in sorted order.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -d work/src-pristine ] || { echo "ERROR: run scripts/setup.sh first"; exit 1; }

# Keep sources/ in step with any edits to our own new classes.
if [ -d sources ]; then
   while IFS= read -r f; do
      rel=${f#sources/}
      if [ -f "work/src/$rel" ] && ! diff -q "$f" "work/src/$rel" >/dev/null; then
         cp "work/src/$rel" "$f"
         echo "    updated sources/$rel"
      fi
   done < <(find sources -name '*.java' | sort)
fi

tmp=$(mktemp -d); n=0; changed=0
while IFS= read -r f; do
   rel=${f#work/src/}
   old="work/src-pristine/$rel"
   [ -f "$old" ] || continue
   if ! diff -q "$old" "$f" >/dev/null; then
      changed=$((changed+1)); n=$((n+1))
      slug=$(basename "$rel" .java | tr '[:upper:]' '[:lower:]')
      # Keep the descriptive name an existing patch already uses for this file. Regenerating
      # to a bare filename slug once silently replaced patches that carried work nothing else
      # had a copy of, and the set stopped reproducing the shipped jar. Names are matched by
      # the file the patch touches, which is the only thing that survives a rewrite.
      keep=$(grep -l "^+++ b/$rel$" patches/*.patch 2>/dev/null | head -1 || true)
      if [ -n "$keep" ]; then
         out="$tmp/$(basename "$keep")"
      else
         out=$(printf '%s/%04d-%s.patch' "$tmp" "$n" "$slug")
      fi
      # diff exits 1 when files differ, which under `set -e -o pipefail` would kill the
      # script on the first real change. That is exactly the case we are here for.
      diff -u "$old" "$f" | sed "1s|.*|--- a/$rel|; 2s|.*|+++ b/$rel|" > "$out" || true
      echo "    $(basename "$out")"
   fi
done < <(find work/src -name '*.java' | sort)

if [ "$changed" -eq 0 ]; then echo "no changes"; rm -rf "$tmp"; exit 0; fi
echo "==> replacing patches/ with $changed patch(es)"
echo "    Descriptive names are kept for files that already had one; new files get a slug."
rm -f patches/*.patch
mv "$tmp"/*.patch patches/
rm -rf "$tmp"
ls -1 patches/ | sed 's/^/    /'
