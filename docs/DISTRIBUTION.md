# How this gets distributed

The problem: I want this to be a community project, but Infinite grew out of decompiled
Minecraft, and I can't hand that to anyone. Not publicly, not in a private repo with three
collaborators. Giving a copy to someone else is distribution either way.

MCP, Forge and Fabric all hit this and all solved it the same way. This doc is how I'm
applying that here.

> [!NOTE]
> I'm not a lawyer and none of this is legal advice. It's the engineering pattern the whole
> modding scene runs on. The reason nobody commits decompiled Minecraft anywhere, public or
> private, is that everyone reads it the same way.

## The rule

**Ship our code. Never ship Mojang's. Everyone brings their own copy of the game.**

Three ways to do that.

<details>
<summary><b>A. Binary patch. Don't.</b></summary>

Ship a bsdiff between the original jar and the modded one. Tempting because it's easy. But a
jarmod delta contains whole replaced class files that *are* recompiled Mojang derived code, so
you're distributing their work with a hat on. Skip it.

</details>

### B. Build time assembly ← what this repo does

The MCP / ForgeGradle / Loom model.

The repo holds my source, plus **patches as diffs** against decompiled vanilla classes. Your
machine supplies your own a1.0.4 jar, decompiles it locally, applies the patches, compiles.
Nothing Mojang owns is ever committed or served from here.

### C. Runtime injection. The endgame.

Ship a loader plus a mod jar with only my classes in it. Changes to vanilla classes become
Mixins applied at classload, so the repo holds zero vanilla derived source, just "in method
`x`, inject before `y`".

Cleanest of the three and where I want to end up. Not practical today because it'd mean
Mixining across roughly a thousand game classes. It gets practical exactly as the number
below shrinks.

## What's actually in the jar

Worth measuring before designing anything, because the encumbered part is smaller than it
looks.

| Package | Files | Size | Whose |
| --- | ---: | ---: | --- |
| `it/unimi/dsi` (fastutil) | 3,396 | 15.0 MB | third party, Apache-2.0 |
| `net/minecraft/game` | 1,062 | 4.3 MB | **mixed, the problem area** |
| `net/minecraft/client` | 413 | 1.8 MB | **mixed** |
| `net/minecraft/network` | 82 | 181 KB | **mixed** |
| `net/minecraft/server` | 39 | 236 KB | **mixed** |
| `com/mojang/brigadier` | 54 | 178 KB | third party, MIT |
| `com/jcraft/jorbis` | 43 | 140 KB | third party, LGPL |
| `paulscode` | 34 | 223 KB | third party |
| `ca/spottedleaf/starlight` | 2 | 17 KB | third party, **LGPL-3.0** |
| resources | ~2,000 | ~10 MB | **mixed** |

Two things fall straight out of that.

**About 3,500 of the ~5,000 class files are vendored third party libraries.** They don't
belong in a repo at all, they belong in a dependency block. That alone removes most of the
bulk.

**Only ~1,600 classes are `net/minecraft`.** Vanilla alpha shipped a few hundred. So most of
that 1,600 is mine.

## Repo layout

```
minecraft-infinite/
  build.gradle.kts
  gradle/libs.versions.toml   # fastutil, brigadier, jorbis, paulscode as real dependencies
  base/
    base.jar.sha1             # expected hash of your a1.0.4 jar
    decompiler.lock           # decompiler coordinate + exact flags
    mojang-assets.txt         # the 27 files that must never be committed
  patches/                    # one unified diff per touched vanilla class
  src/main/java/              # every class I wrote, in full
  src/main/resources/         # only art I made
  loader/                     # installer + standalone loader
  tools/                      # round trip gate, manifest generator
  NOTICE.md, licenses/
```

**Committed:** our source, our art, diffs, build logic, hashes.
**Never committed:** the base jar, decompiled output, the built jar.

## Build pipeline

`./gradlew setup` once, then `./gradlew build`.

1. **Find the base jar.** From `local.properties`, or your existing Prism/MultiMC instance.
   Never downloaded from anywhere I control.
2. **Verify its hash** against `base/base.jar.sha1`. Fail loud on mismatch.
3. **Decompile** with the pinned decompiler and exact flags from `decompiler.lock`, into a
   gitignored directory.
4. **Verify the decompiled tree.** If it differs, someone's toolchain drifted and every patch
   is about to misapply.
5. **Apply `patches/`.** Any rejected hunk fails the build.
6. **Compile** patched sources plus `src/main/java`, third party libs from Maven.
7. **Assemble** `Infinite.jar`.

To change vanilla code you edit the patched tree and run `regeneratePatches`, which re-diffs it
back into `patches/`. Same as Forge development felt. People already know this workflow.

## The hard parts

### Deterministic decompilation is the whole ballgame

Patches are diffs against decompiler output. Two people on different decompiler versions get
different source and every patch misapplies. So the version and the flags are pinned, hashes
of the input jar and the decompiled tree are committed, and bumping the decompiler is a
deliberate commit with regenerated patches.

### Round trip failures are real

> [!WARNING]
> Some classes decompile to source that compiles fine but produces a **different set of class
> files**, quietly dropping anonymous inner classes the original bytecode still references. On
> this project `Minecraft.java` does exactly that, its decompiled source loses `Minecraft$2`.
> A patch there ships a broken client with no compile error anywhere.

That's what `tools/roundtrip_check.py` is for, and why it runs in CI. It recompiles every
unpatched decompiled class and asserts the resulting class file set matches the original jar's.
If a class fails the gate, it can't be modified through source. Bytecode transform or leave it.

### Art is the sneaky part

`blocks/`, `items/`, `mob/`, `gui/`, `art/`, `font/` are ~10 MB and a mix of my textures and
Mojang's originals. Committing theirs is the same problem as committing their code, and it's
easy to miss because it doesn't look like source. Audited: **27 files are byte identical to
Mojang's** and are listed in `base/mojang-assets.txt`. The build fails if one shows up in
`src/main/resources`.

Four more are *modified* versions of Mojang originals (`armor/cloth_1.png`, `armor/cloth_2.png`,
`gui/gui.png`, `gui/icons.png`). Derivative works of their art. Treat them as encumbered until
someone redraws them.

### Getting the game is your problem

The build reads a jar you already have. It must not fetch one from a mirror I run, and this
repo will never link to a pirated copy. That turns a clean design into a distribution channel.

## What's still unsolved

Nothing in the mod references the obfuscated Alpha classes, which is better than I expected.
But that isn't the same as the code being independent. Fingerprinting string constants traces
**41 of the 109 identifiable Alpha classes** to descendants in the mod. `dg` → `GuiScreen`,
`ge` → `Creeper`, `he` → `Zombie`, `eu` → `Arrow`, and so on. Renamed and heavily modified, but
they descend from decompiled Mojang code.

So publishing `Infinite.jar` still distributes a derivative of Alpha 1.0.4. That's the same
spot every jarmod based mod has always been in. It's a lawyer question, not a diff question.

The engineering answer, if I want to close it, is rewriting those ~41 classes from scratch
against their behaviour instead of their inherited implementation. That's a real project, but
it's a *bounded* one now, because I can name the classes.

## Order of work

- [x] Audit the art. Find out whether it's a blocker. (It isn't. 27 files.)
- [x] Measure the class split. Decides everything downstream.
- [x] Prove the loader concept and the round trip gate on a handful of classes.
- [ ] Move the vendored libraries to Gradle dependencies. Kills 3,500 files and most of the
      licensing mess in one step.
- [ ] Migrate the rest of the patch set, converting small diffs to Mixins as I go.
- [ ] Rewrite the 41 traced classes.
- [ ] Open the repo.

The first four are worth doing even if this stays private forever, they just make the project
easier to work on.
