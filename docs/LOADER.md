# The loader

Two artifacts replaced the jarmod.

| | What it is | Ship it? |
| --- | --- | --- |
| `Infinite.jar` | The whole mod. All classes, all our art. | **yes** |
| `InfiniteLoader.jar` | 6 KB launcher | **yes** |
| The Alpha 1.0.4 client jar | 27 Mojang textures | **no, everyone brings their own** |

```sh
java -jar InfiniteLoader.jar
```

Most people won't touch this. `InfiniteInstaller.jar` registers with your launcher and the
launcher does the launching, see [PROFILES.md](PROFILES.md). The loader is for playing without
one, and it's the path I've actually run end to end.

## The measurement that killed the jarmod

I checked whether the mod uses *any* code from the official jar, by resolving every
`CONSTANT_Class` entry in all 1,665 project classes against the 375 classes in Alpha 1.0.4.

**It references none of them.** The only base jar types it touches are `paulscode/*`, the
sound library, which the mod already bundles itself.

So the jarmod setup was lying to me. Prism merges the official jar with the overlay, but
because the mod uses full names and the official jar is obfuscated (`a.class`, `aa.class`, …)
nothing collides and nothing calls across. Those 304 obfuscated classes have been dead weight
in every build I've ever shipped.

> [!IMPORTANT]
> That means a bytecode injection loader would solve a problem I don't have. There is nothing
> to inject into. The only real dependency on the official jar is **27 texture files**.

<details>
<summary><b>Why the first scan said 73 references and was wrong</b></summary>

The naive version matched any UTF-8 constant in the pool, and `a`, `b`, `c` are also field
names, method names and locals. 73 false positives. Resolving only tag 7 `CONSTANT_Class`
entries, which are the real type references, gives the true answer: zero.

</details>

## What the loader does

1. Finds your Alpha 1.0.4 jar. `-Dinfinite.base=`, `INFINITE_BASE_JAR`, or a bounded breadth
   first scan of where launchers actually keep things, portable installs included.
2. Verifies it's sha1 `e5838277b3bb193e58408713f1fc6e005c5f3c0c` and refuses otherwise.
3. Extracts the 27 assets into `assets-cache/` beside itself, handling the ones the mod
   renamed (`environment/clouds.png` comes from `clouds.png`, and so on).
4. Resolves LWJGL. Its own cache first, then your launcher's `libraries/`, then the network.
5. Builds a classloader over `[Infinite.jar, assets-cache]` and calls
   `net.minecraft.client.Minecraft.main`.

Assets resolve through `getResourceAsStream("/…")`, so a classpath overlay is all it takes. No
patching, no injection, no reflection into game internals.

The parent classloader is the **platform** loader, not the app loader, so the mod can't
accidentally resolve classes out of the loader's own jar.

## Verified

| Case | Result |
| --- | --- |
| No base jar configured | Refuses, explains where to find it, doesn't offer a download |
| Wrong base jar | Refuses on hash mismatch, prints expected vs actual |
| Correct base jar | Verified, 27 assets extracted |
| Extracted assets | **27/27 byte identical** to the ones previously baked into the jar |
| Classpath overlay | Mojang assets, renamed Mojang assets and mod assets all load |

`--check` runs all of that without launching the game. It's wired for CI.

## What this fixes and what it doesn't

**Fixes:** shipping Mojang *art*. `Infinite.jar` contains none.

**Doesn't fix:** the derivative code question. Nothing references the obfuscated classes, but
that isn't the same as the code being independent. See
[DISTRIBUTION.md](DISTRIBUTION.md).

Real improvement, not a clean bill of health.
