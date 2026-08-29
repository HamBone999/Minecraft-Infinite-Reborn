# Making Infinite a real mod loader

Goal: Infinite loads as a mod itself, and other people can ship addons that add content and
change behaviour. Same job NeoForge does, at Alpha 1.0.4 scale.

This doc is the design plus what I've actually verified. Anything not marked verified is a
plan, not a fact.

## The good news, measured

I probed the built jar rather than guessing. Three things I expected to be blockers aren't.

### Registries are already real, and mostly empty

| Registry | Capacity | Used | Free |
| --- | ---: | ---: | ---: |
| `BlockList.blocks` | 4,096 | 322 | **3,774** |
| `ItemList.items` | 10,240 | 698 | **9,542** |

Vanilla alpha had 256 block IDs. Upstream widened it to 4,096 and nobody has spent it. There
is room for a very large modding ecosystem before IDs become a problem.

Better, `Block(int id, Material)` **self-registers**:

```java
BlockList.blocks[id] = this;      // and throws on collision:
// Error: Slot 42 is already occupied by <block> when adding <block>
```

So an addon registers a block by constructing it. No registry API to write, and ID collisions
already fail loudly instead of silently overwriting.

### Textures are stitched by name, not a fixed grid

```java
public interface IconRegister {
   Icon registerIcon(String name);
}
```

159 classes use it, backed by `TextureMap`. This is the modern stitched-atlas system, not
alpha's fixed `terrain.png` layout. **Addons can ship their own textures.** In an alpha-era
codebase this is normally the thing that kills content modding, and it's already solved.

### Infinite isn't obfuscated

Every class has a real name (`net.minecraft.game.block.Block`). NeoForge mods need refmaps and
mapping files because Minecraft is obfuscated and names change every version. **Addons here
target names directly.** No refmap, no mapping service, no remapper in the toolchain.

That deletes an entire subsystem NeoForge cannot avoid.

## Verified: Mixin transforms Infinite

The whole architecture rests on third-party bytecode being able to change Infinite without
Infinite knowing. So I built the smallest thing that proves it.

```java
@Mixin(Block.class)
public class BlockMixin {
   @Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
   private void infinite$everythingIsButter(CallbackInfoReturnable<Float> cir) {
      cir.setReturnValue(0.125F);
   }
}
```

Result:

```
Block loader  : net.minecraft.launchwrapper.LaunchClassLoader@a74868d
getHardness() : 0.125          vanilla is 1.5
```

**A jar outside Infinite changed Infinite's behaviour with zero changes to Infinite.** That is
the architecture working. Runnable copy in `spike/`.

## Java 8 only, decided

> [!IMPORTANT]
> **Decision: Java 8 + LaunchWrapper.** Reasoning below, kept because it will get asked again.

The proven stack is **LaunchWrapper 1.12 + Mixin 0.8.5**, which is what Forge and Sponge used
through the 1.7 to 1.12 era. It works. It also cannot run on Java 9 or newer:

```
java.lang.ClassCastException: jdk.internal.loader.ClassLoaders$AppClassLoader
   cannot be cast to java.net.URLClassLoader
      at net.minecraft.launchwrapper.Launch.<init>(Launch.java:34)
```

I tried to route around it by constructing `LaunchClassLoader` directly and skipping
`Launch.main`, since our loader already builds its own classloader. That gets the classloader
up on Java 21, but Mixin's LaunchWrapper service refuses to finish initialising outside the
tweaker handshake (`MixinBootstrap.doInit() called during a tweak constructor!`) and no
transform applies. Driving `MixinTweaker` manually didn't fix it either.

So it's not one bad cast. Java 8 is genuine for this stack.

### The three ways out

| Option | Java | Work | Notes |
| --- | --- | --- | --- |
| **LaunchWrapper + Mixin** | 8 only | **none, proven today** | The 1.7-1.12 stack. Pin Java 8 in the launcher, which Prism and MultiMC do per instance anyway. |
| **ModLauncher + Mixin** | 9+ | large | What NeoForge actually uses. Module layers, service loaders, a lot of machinery for a game from 2010. |
| **Own classloader + custom `IMixinService`** | any | medium | Implement Mixin's service interface against our own transforming loader. Most control, no LaunchWrapper. |

Alpha 1.0.4 wants LWJGL 2.9.4, which is happiest on Java 8 anyway, and every launcher lets you
pin a Java version per instance. Java 8 is the right answer here, not a compromise. If that
ever changes, the third row is the way out and nothing above the classloader has to move.

## Architecture

```
InfiniteLoader.jar
  |
  +- discover mods/            scan jars for infinite.mods.toml
  +- resolve dependencies      version ranges, ordering, cycle detection
  +- build LaunchClassLoader   Infinite.jar + every mod jar
  +- MixinBootstrap            register each mod's mixin configs
  +- construct mods            @Mod entry points, in dependency order
  +- fire lifecycle            construct -> common setup -> client setup
  +- hand off                  net.minecraft.client.Minecraft.main
```

Infinite itself becomes the first mod in that list. Same code path as everyone else's, which
is the only way to keep the API honest.

### Mapping to NeoForge

| NeoForge | Here | Status |
| --- | --- | --- |
| `neoforge.mods.toml` | `infinite.mods.toml`, same keys | **done** |
| ModLauncher | LaunchWrapper | **proven** |
| Mixin | Mixin, same version | **proven** |
| refmaps + mappings | not needed, unobfuscated | **n/a, free win** |
| `mods/` folder | `mods/` folder | **done** |
| `@Mod` entry point | `@Mod` | **done** |
| Event bus | `EventBus`, 4 core events | **done** |
| Deferred registers | direct construction, already self-registering | **already works** |
| Binary patching the game jar | not needed, we own the code | **n/a** |

## Build order

- [x] Prove Mixin transforms an Infinite class
- [x] Measure registry headroom and the texture path
- [x] Decide the Java question. **Java 8 + LaunchWrapper.**
- [x] TOML subset parser, no new dependency
- [x] Mod discovery, metadata, dependency resolution with version ranges
- [x] Classloader assembly and Mixin config registration
- [x] `@Mod` entry points and the lifecycle
- [x] **End to end: an addon jar that registers a block and mixins into Infinite**
- [x] Event bus with priorities, cancellation and fault isolation
- [x] Entity registry
- [x] Worldgen registry
- [x] Core mixins that fire the events, shipped inside the loader jar
- [x] Modding libraries added to the release manifest
- [x] Renderer registration for modded entities
- [x] Registry sync at login so client and server agree on modded ids
- [ ] Infinite itself converted to load as a mod

The last item matters more than it looks. Until Infinite goes through the same door as everyone
else, the API will quietly grow holes that only addon authors hit.

## It works

`examples/greenstone` is a real addon jar. It registers a block **and** mixins into Infinite,
and it is loaded from `mods/` with no changes to Infinite at all.

```
[infinite] mods      : 2 to load
[infinite]             infinite 1.0-210826
[infinite]             greenstone 1.2.0  (greenstone-1.2.0.jar)
[infinite] mixins    : 1 config(s)
[main/INFO]: Launching wrapped minecraft {infinite.loader.mods.GameStart}
[greenstone] constructed, version 1.2.0
[greenstone] registered a block at id 1500

  ================ RESULT ================
   mixin  : stone.getHardness() = 0.125   (vanilla 1.5)
   content: blocks[1500]        = net.minecraft.game.block.Block@3766c667
  ========================================
   mixin applied   : YES
   block registered: YES
```

Failure paths behave too:

| Case | Result |
| --- | --- |
| No `mods/` folder | Loads Infinite alone, no complaints |
| Missing required dependency | `Greenstone requires 'someothermod' [1.0,) but it is not installed.` |
| Dependency too old | `Greenstone requires 'infinite' [9.0,) but 1.0-210826 is installed.` |

All three fail **before** a single game class is loaded, which is the point of sorting first.

### Two things that cost me time, recorded so they don't again

**Mixin has to be driven by `Launch`, not called directly.**
`MixinServiceLaunchWrapper.getInitialPhase()` inspects the call stack for
`net.minecraft.launchwrapper.Launch.launch`. Bootstrapping Mixin reflectively from outside
half-initialises it, logs `MixinBootstrap.doInit() called during a tweak constructor!`, and
then silently transforms nothing. Driving `start()` / `doInit()` / `inject()` by hand does not
help either. The loader goes through `Launch.main` with its own tweaker, the way Forge always
did.

**The classloader boundary has exactly one correct shape.**
`infinite.loader.` and `infinite.api.` are classloader-excluded so they stay single copy. If
the transforming loader defined its own `ModContext`, a mod's constructor would be handed an
instance of a same-named different class and fail with a `ClassCastException`. Everything else,
the game included, stays transformable. `GameStart` is therefore in the app loader and has to
reach for `Launch.classLoader` explicitly.

## Registry sync

Entity ids are allocated locally and travel over the wire, so two machines with different mod
sets disagreed about what an id meant. Solved by making the server authoritative at login.

```
server  NetServerHandler.<init> TAIL  ->  CustomPayloadPacket("infinite|registry", table)
client  ClientHandler.handleCustomPayload HEAD  ->  decode, remap, cancel the packet
```

Both ends are mixins in the loader, on side-specific configs, because each targets a class the
other side does not have. The engine picks the config from the entry point, overridable with
`-Dinfinite.side=server`.

The remap is **all or nothing**. New tables are built first and swapped in only once every
entry resolves; a half-applied remap would leave some ids the server's and some the client's,
which is worse than refusing. A client missing an addon gets a message naming it.

Verified: a payload round trips, a deliberately swapped table is corrected in the live
`EntityManager`, a server entity the client lacks is detected with nothing changed, and the
server-side mixin applies against the real server jar (Mixin mangles constructor injections to
`handler$zzd000$infinite$sendRegistry`, which is worth knowing before you go looking for it).

## Open questions

**`CreatureType`, `ProjectileType` and `EntitySpawnPacket.EntityType` are enums.** Ordinary
mobs go through `EntityManager` and are fine, but anything wanting a new *object* spawn type
(a new kind of minecart, say) hits an enum that can't be extended at runtime. Mixin can widen
an enum, but it's ugly. Worth designing properly if someone actually needs it.

**Mixin packages must not collide with the loader's.** In the spike the mixin sat in the same
package as the tweaker and Mixin warned
`Classloader restrictions [PACKAGE_CLASSLOADER_EXCLUSION]`. It still applied, but the real
loader must keep its own classes well away from any package a mod might use for mixins.
