#!/bin/sh
# Compile an addon against the server jar and package it.
#
#   build-mod.sh <mod-dir> <server.jar> <InfiniteLoader.jar> <mixin.jar> [version]
#
# -proc:none is NOT optional. Mixin's annotation processor assumes an obfuscated game and
# fails on this one. Leaving it off is the first thing that breaks a rebuild from source.
set -e
MOD=$1; SERVER=$2; LOADER=$3; MIXIN=$4; VER=${5:-0.1.0}
[ -d "$MOD" ] || { sed -n '2,8p' "$0"; exit 2; }
NAME=$(basename "$MOD")
JDK=${MOD_JDK:-/usr/lib/jvm/temurin-8-jdk-amd64/bin}
rm -rf "$MOD/build"; mkdir -p "$MOD/build"
find "$MOD/src" -name '*.java' > /tmp/mod-srcs.txt
"$JDK/javac" -source 8 -target 8 -proc:none -nowarn \
  -cp "$SERVER:$LOADER:$MIXIN" -d "$MOD/build" @/tmp/mod-srcs.txt
[ -d "$MOD/resources" ] && cp -r "$MOD/resources/." "$MOD/build/"
( cd "$MOD/build" && "$JDK/jar" cf "../../$NAME-$VER.jar" . )
echo "BUILT: $NAME-$VER.jar"
