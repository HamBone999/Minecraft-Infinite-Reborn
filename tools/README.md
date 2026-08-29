# Infinite dev tools

Tools written while debugging Minecraft Infinite. Every one exists because a specific
problem could not be solved by reading the code — they are the ones that actually found
answers, cleaned up and documented.

Python 3, no third-party packages. `png.py` is a pure-Python PNG codec precisely so none of
this needs `pip install`.

```
bytecode/     read compiled classes
deobfuscate/  decompile, and prove the round trip is safe
jar/          compare and rewrite jars
assets/       PNG and GUI atlas work
world/        Anvil regions and NBT
server/       sandboxed boots and addon builds
stubs/        LWJGL stubs for compiling client classes headless
```

## bytecode/

### `stackchase.py` — map a crash to the instruction that threw
A crash gives you `GameMode.java:219`, but on a decompiled codebase that line number belongs
to source you do not have, so it does not match your file. This reads the real
`LineNumberTable` and prints the bytecode for that line.

```sh
stackchase.py Infinite.jar net.minecraft.client.gamemode.GameMode 219
stackchase.py Infinite.jar --frame 'at net.minecraft.client.gamemode.GameMode.a(SourceFile:219)'
```

This is what identified the adventure-mode crash: line 219 resolved to offset 0,
`invokevirtual Block.breakStrength` — a call on a block that was null because
`BlockList.blocks[0]` is air.

### `methoddiff.py` — semantic bytecode diff
`javap -c` is useless for diffing: insert one instruction and every offset and branch target
after it shifts, so a one-line change reports as a hundred-line diff. This normalises
offsets, branch targets and constant-pool indices away.

It is how we established that **6 of 8 "changed" methods in a release were javac emitting
equivalent control flow**, not behaviour changes — which kept six invented entries out of a
changelog.

### `findstring.py` — which entry contains this string
The workhorse for "where does this message come from" and "what still claims the old
version".

```sh
findstring.py Infinite.jar --regex '1\.0-[0-9]{6}'
```

## deobfuscate/

### `decompile.sh`
Vineflower with the flags pinned. Patches are diffs against decompiler output, so two people
on different versions get different source and every patch misapplies. Also finds a Java 11+
runtime for the decompiler even when the project itself targets Java 8.

### `roundtrip-check.py` — the gate that stops silent breakage
Some classes decompile to source that compiles fine but produces a **different set of class
files**, dropping members the original bytecode still references. A patch against one of
those ships a broken build with no compile error anywhere.

Two real cases here: `Minecraft.java` loses `Minecraft$2`, and the enum `Launchers$Kind`
loses its synthetic `$values()` on a Java 8 round trip. Run this before trusting any
recompiled class.

## jar/

### `reversion.py` — rewrite the version everywhere it was inlined
The version is a `static final String`, and javac **inlines** those, so it is baked into 4
server and 7 client classes. Editing `SharedConstants` alone changes nothing the game prints.

Replacement must be the same byte length — that is why the version format is `1.0-DDMMYY`.

> [!IMPORTANT]
> Run this after **any** recompile of a client class, even when the version is not changing.
> The decompiled source carries the old version as a literal, so a rebuild silently
> reintroduces it. This shipped a wrong version on the title screen twice before it became a
> fixed step.

### `jardiff.py`
Entry-level diff. The check to run before shipping an overlay build — it is what proved a
release changed exactly the 9 intended classes out of 7,772 entries.

## assets/

`png.py` reads and writes PNG with only `zlib`. `atlasmap.py` shows which 16x16 cells of a
GUI atlas are used, and `--free` lists slots where **both** the icon cell and its hover cell
16px below are empty, which is what a new button actually needs.

## world/

`anvil.py` reads region files, `nbt.py` reads NBT. Used to measure the Crimson dimension's
geometry — roof at y304–319 fully solid, empty y176–287 — which is why arrivals were landing
on top of the world.

## server/

### `sandbox-boot.sh`
Boots a jar in a throwaway directory, greps the interesting lines, and **always** cleans up.

> [!WARNING]
> It kills its JVM by PID, never with `pkill -f` — that pattern matches your own shell and
> the grep itself. And it always cleans up, because orphaned sandbox servers from earlier
> testing once held about 2 GB and pushed the live server into swap, which looked exactly
> like the live server being broken.

### `build-mod.sh`
Compiles an addon against the server jar. `-proc:none` is not optional: Mixin's annotation
processor assumes an obfuscated game and fails on this one.

## stubs/

LWJGL stubs so client classes compile on a machine with no LWJGL. Descriptors must match the
real library exactly — a wrong one compiles and then throws `NoSuchMethodError` in front of
players. Read them out of the shipped bytecode rather than writing them from memory; see
`stubs/lwjgl/README.md`.
