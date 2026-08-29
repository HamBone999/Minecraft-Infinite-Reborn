# Writing an addon

How to make a mod for Minecraft Infinite. If you've written a NeoForge mod, this will look
familiar on purpose.

> [!IMPORTANT]
> **Java 8.** The transforming classloader is LaunchWrapper, which doesn't run on Java 9+.
> Compile with `--release 8` and pin Java 8 in your launcher. Reasons in
> [MODLOADER.md](MODLOADER.md).

## Your jar

```
yourmod.jar
  META-INF/infinite.mods.toml     required, this is what makes it a mod
  yourmod.mixins.json             only if you use mixins
  com/example/yourmod/...         your classes
```

Drop it in `mods/` next to the install. That's the whole install process.

## infinite.mods.toml

```toml
modLoader = "javafml"
loaderVersion = "[1,)"
license = "MIT"

[[mods]]
modId = "examplemod"
version = "1.0.0"
displayName = "Example Mod"
description = "Does a thing."
authors = "you"
entrypoint = "com.example.ExampleMod"

[[mixins]]
config = "examplemod.mixins.json"

[[dependencies.examplemod]]
modId = "infinite"
type = "required"
versionRange = "[1.0,)"
ordering = "AFTER"
```

Same keys as `neoforge.mods.toml`, with one addition: **`entrypoint`** names your `@Mod` class
outright. NeoForge scans the jar for the annotation; scanning would mean loading classes before
the transformer is ready, so this names it instead.

`versionRange` is Maven notation: `[1.0,2.0)`, `[1.0,)`, `(,2.0]`, `[1.2.3]` for an exact pin.
`type` is `required` or `optional`. `ordering` is `BEFORE`, `AFTER` or `NONE`.

A missing or wrong-version dependency stops the load **before any game class is touched**, with
a message naming what's missing.

## Entry point

```java
package com.example;

import infinite.api.Mod;
import infinite.api.ModContext;

@Mod("examplemod")
public class ExampleMod {

   public ExampleMod(ModContext ctx) {
      // Construction is early. Register callbacks, don't do work here.
      ctx.onSetup(this::registerContent);
      ctx.onClientSetup(this::registerRenderers);
   }

   private void registerContent() { }
   private void registerRenderers() { }
}
```

A no-argument constructor works too if you don't need the context.

| Phase | When | For |
| --- | --- | --- |
| constructor | before any game class loads | subscribing to events, nothing else |
| `onSetup` | registries exist | blocks, items, entities, recipes, worldgen |
| `onClientSetup` | after setup, client only | renderers, textures |

## Blocks and items

Infinite's `Block` constructor self-registers, so there's no registry call:

```java
public class Greenstone extends Block {
   public Greenstone(int id) {
      super(id, MaterialList.rock);
      setName("greenstone");
      setHardness(1.5F);
   }
}

// in onSetup:
new Greenstone(1500);
```

**Pick an id and keep it.** Ids go into save files and over the wire. There are 3,774 free
block ids and 9,542 free item ids, so collisions are avoidable; the constructor throws
`Slot N is already occupied` if you do collide.

Textures register by name through `IconRegister`, so ship your own PNGs. No fixed atlas grid.

## Entities

```java
import infinite.api.EntityRegistry;

EntityRegistry.register(Wraith.class, "wraith", 210);
```

Your class must extend `net.minecraft.game.entity.Entity` and have a **public constructor
taking a single `World`**. The game builds entities reflectively when loading a world and from
spawn packets, so there's no other way in. Both are checked up front with a real error message.

Ids run 1 to 255. 124 are taken, so there's room but not unlimited room.

**Multiplayer handles id disagreement for you.** At login the server sends its whole
name-to-id table and the client remaps to match, so two machines that allocated different
numbers for the same entity still agree. Names are what must be stable, not ids.

Still pass an explicit id where you can. It keeps save files portable between installs, and
`registerAuto` picks whatever is free, which changes as you add mods.

### Rendering

```java
// in onClientSetup, never onSetup
EntityRendererRegistry.register(Wraith.class, new WraithRenderer());
```

You may not need it. `getRenderer` walks up the superclass chain, so an entity extending
`Mob` already draws with `MobRenderer`. Register only when you want a different look.

It has to be `onClientSetup`, because the render manager caches superclass lookups and a
renderer registered later is ignored for anything already drawn. The registry evicts stale
inherited entries for you, but it can't fix a frame that already went out.

## Worldgen

```java
import infinite.api.WorldgenRegistry;

WorldgenRegistry.add(new MyOreFeature(), 12, 4, 48);   // 12 tries/chunk, y 4 to 48
```

Your feature needs `place(World, Random, int, int, int)` — same shape as Infinite's own
`Feature` subclasses. It runs at the tail of chunk population, so terrain and the generator's
own features already exist.

The registry seeds its `Random` from the chunk coordinates, so worlds regenerate identically
and one mod's draws can't perturb another's. It also applies the **+8 block offset** that
population needs; get that wrong yourself and features get clipped at chunk borders.

A feature that throws is reported and removed rather than allowed to break generation.

## Events

```java
import infinite.api.event.*;

EventBus.get().subscribe(BlockBreakEvent.class, EventBus.Priority.HIGH, "examplemod",
   event -> System.out.println("broke a block at " + event.x + "," + event.y + "," + event.z));
```

| Event | Fired from | Cancellable |
| --- | --- | --- |
| `BlockBreakEvent` | `Block.onBlockRemoval` | flag only, the break already happened |
| `BlockPlaceEvent` | `Block.onBlockAdded` | no |
| `EntitySpawnEvent` | `World.spawnEntity` HEAD | **yes, really prevents the spawn** |
| `ChunkPopulateEvent` | `OverworldGenerator.populate` TAIL | no |

Listeners on a supertype see subtypes, so subscribing to `Event` gets you everything.

Priorities run `HIGHEST` → `LOWEST`, and within a priority in registration order. Cancelling
skips later listeners unless their event type is annotated `@ReceiveCancelled`.

**A listener that throws is reported and unsubscribed**, not allowed to take down every other
mod's listeners. Pass your modId as the owner so the message names you.

## Multiplayer and the registry sync

Entity ids are allocated locally but travel over the wire, so a client and server with mods
loaded in a different order would disagree about what id 210 means. The symptom is a mob
spawning as the wrong creature, with nothing in the log to explain it.

At login the server sends its entity table on the `infinite|registry` channel and the client
rewrites its own ids to match. The server is the authority; only the client moves, which is
what makes it work with several clients at once.

Two things worth knowing when your addon is involved:

- **Names are the contract, ids are not.** A name goes into save files and into this table.
  Changing an entity's name in an update breaks both. Changing its id is now harmless.
- **A client missing your addon fails loudly.** The sync names what's missing and applies
  nothing, rather than remapping half the table and spawning wrong entities.

A vanilla client ignores the channel, so this doesn't break anyone.

## Mixins

Infinite is **not obfuscated**, so mixins target real names and you need no refmap, no mapping
file, and no remapper. This is meaningfully simpler than NeoForge.

```java
@Mixin(Block.class)
public class BlockMixin {
   @Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
   private void examplemod$softer(CallbackInfoReturnable<Float> cir) {
      cir.setReturnValue(0.125F);
   }
}
```

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": [ "BlockMixin" ],
  "injectors": { "defaultRequire": 1 }
}
```

Compile with **`-proc:none`**. Mixin's annotation processor otherwise fails with
`Unable to locate obfuscation mapping`, because it assumes an obfuscated game.

Prefix your injected methods with your modId (`examplemod$`) so two mods injecting into the
same class can't collide.

Don't put your mixins in a package starting `infinite.loader.` or `infinite.api.` — those are
classloader-excluded so the loader and the API stay single-copy, and mixins there won't apply.

## Building

```sh
javac -proc:none --release 8 \
      -cp "libs/*:Infinite.jar" \
      -d out $(find src -name '*.java')
cp -r res/* out/
cd out && jar cf ../yourmod.jar .
```

`libs/` needs Mixin, LaunchWrapper and ASM. They're listed in the release's `version.json`
under `libraries`, all fetched from their own upstreams.

Working example in [`examples/greenstone`](../examples/greenstone) — it registers a block and
mixins into Infinite, and it's the jar used in the loader's own tests.

## Debugging

| Symptom | Cause |
| --- | --- |
| Mod not listed at startup | No `META-INF/infinite.mods.toml`, or it failed to parse. The loader says which. |
| `names entrypoint X but that class is not in its jar` | `entrypoint` is wrong or the class didn't get packaged. |
| Mixin silently does nothing | The target class loaded before the transformer was ready, or your mixin package is classloader-excluded. |
| `Unable to locate obfuscation mapping` at compile | Add `-proc:none`. |
| `ClassCastException` on `ModContext` | You bundled a copy of `infinite.api` in your jar. Don't; it's provided. |
