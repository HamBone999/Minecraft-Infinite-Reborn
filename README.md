# Minecraft Infinite

Source for the Infinite client patches, the server patches, the mod loader changes and the server addons.

To install download the client zip from [Releases](https://github.com/HamBone999/Minecraft-Infinite-Reborn/releases).
Follow install instructions included.

> [!IMPORTANT]
> **The game is not in this repo and never will be.** Infinite grew out of decompiled
> Minecraft, so handing anyone a copy is distribution either way -- public repo, private
> repo, three collaborators, it reads the same. This repo holds *my* source plus **patches
> as diffs**; your machine supplies your own jars and applies them locally.
> Full reasoning in [docs/DISTRIBUTION.md](docs/DISTRIBUTION.md).

## Layout

```
base/                  hashes of the jars you supply, and the pinned decompiler
patches/               server: one unified diff per touched class
src/main/java/         server: classes that are entirely mine, committed in full
client/patches/        client: diffs against decompiled classes
client/src/            client: classes that are entirely mine (ModsScreen)
client/src/main/resources/   changelog, lang additions, instance icon
loader/patches/        installer and loader changes
mods/                  anticheat, landclaim, perms -- full source, all mine
scripts/               the setup / build / regenerate pipeline
tools/                 the debugging toolkit, see tools/README.md
docs/                  how this thing is put together
```

**Committed:** our source, our art, diffs, build logic, hashes.
**Never committed:** the base jars, decompiled output, built jars.

## Building

You need **Java 8** to build and run, and **Java 11+** available for the decompiler.

```sh
cat > local.properties <<'PROPS'
serverJar=/path/to/minecraft-infinite-server.jar
clientJar=/path/to/Infinite.jar
PROPS

./gradlew setup     # verify hashes, decompile, apply patches
./gradlew build     # compile and overlay onto a copy of the base jar
```

To change a patched class, edit `work/src`, then:

```sh
./gradlew regeneratePatches
```

`./gradlew check` runs the round-trip gate.

> [!WARNING]
> Some classes decompile to source that compiles fine but produces a **different set of
> class files**, dropping members the original bytecode still references — a patch there
> ships a broken build with no compile error anywhere. Known cases: `Minecraft.java` loses
> `Minecraft$2`, and the enum `Launchers$Kind` loses its synthetic `$values()` on a Java 8
> round trip. Run `./gradlew check` before trusting a recompiled class, and if one fails the
> gate, transform its bytecode or leave it alone.

## Two rules that are easy to get wrong

**Version strings are inlined.** The version is a `static final String`, and javac bakes it
into 4 server and 7 client classes. Editing `SharedConstants` changes nothing the game
prints, and recompiling any client class silently drags the *old* version back in from the
decompiled literal. Run `tools/jar/reversion.py` after every client recompile, whether or
not the version changed.

**`-proc:none` when building addons.** Mixin's annotation processor assumes an obfuscated
game and fails on this one.

## Addons

```sh
./gradlew :mods:anticheat:jar -PserverJar=... -PloaderJar=...
```

Every anticheat check ships with `kick=false`. Nothing kicks anyone until you turn it on.
