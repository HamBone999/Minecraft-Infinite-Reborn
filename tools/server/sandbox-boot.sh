#!/bin/sh
# Boot a server jar in a throwaway sandbox, capture the log, and ALWAYS clean up.
#
# Two hard-won rules are baked in:
#
#  1. It kills its own JVM by PID. `pkill -f` matches the pattern against your own shell
#     and the grep itself, which kills the wrong thing.
#  2. It always cleans up. Orphaned sandbox servers from earlier testing once held ~2 GB
#     and pushed the live server into swap, which looked exactly like the live server
#     being broken.
#
#   sandbox-boot.sh <server.jar> [seconds] [port]
set -e
JAR=$1; SECS=${2:-45}; PORT=${3:-25599}
[ -f "$JAR" ] || { sed -n '2,14p' "$0"; exit 2; }
# Support files (InfiniteLoader, ServerBoot, libs/, mods/) come from the server directory,
# which is usually NOT where the jar you are testing lives. Point INFINITE_HOME at it.
SRC=${INFINITE_HOME:-$(dirname "$(readlink -f "$JAR")")}
[ -f "$SRC/InfiniteLoader.jar" ] || {
  echo "ERROR: no InfiniteLoader.jar in $SRC"
  echo "       set INFINITE_HOME=/path/to/server to say where the support files are"
  exit 1
}
SBX=$(mktemp -d /tmp/mcsbx.XXXXXX)
trap 'kill "$PID" 2>/dev/null; rm -rf "$SBX"' EXIT INT TERM

cp "$JAR" "$SBX/minecraft-infinite-server.jar"
for f in InfiniteLoader.jar ServerBoot.class 'ServerBoot$1.class'; do
  [ -f "$SRC/$f" ] && cp "$SRC/$f" "$SBX/"
done
[ -d "$SRC/libs" ] && cp -r "$SRC/libs" "$SBX/"
[ -d "$SRC/mods" ] && cp -r "$SRC/mods" "$SBX/"
printf 'eula=true\n' > "$SBX/eula.txt"
printf 'server-port=%s\nlevel-name=world\nonline-mode=false\nmax-players=2\n' "$PORT" > "$SBX/server.properties"

CP=".:InfiniteLoader.jar:minecraft-infinite-server.jar"
for j in "$SBX"/libs/*.jar; do [ -e "$j" ] && CP="$CP:libs/$(basename "$j")"; done

JAVA=${SANDBOX_JAVA:-/usr/lib/jvm/temurin-8-jre-amd64/bin/java}
[ -x "$JAVA" ] || JAVA=$(command -v java)

echo "sandbox: $SBX   port $PORT   ${SECS}s"
( cd "$SBX" && "$JAVA" -Xms512M -Xmx512M -Dinfinite.side=server -cp "$CP" ServerBoot nogui > boot.log 2>&1 ) &
PID=$!
i=0
while [ "$i" -lt "$SECS" ]; do
  kill -0 "$PID" 2>/dev/null || break
  sleep 1; i=$((i+1))
done
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

echo "--- boot log ---"
grep -iE 'mods *:|mixins *:|constructed|Starting minecraft server|Done \(|Exception|FAILED|ERROR: ' "$SBX/boot.log" || tail -20 "$SBX/boot.log"
cp "$SBX/boot.log" /tmp/sandbox-boot.log 2>/dev/null && echo "--- full log: /tmp/sandbox-boot.log ---"
