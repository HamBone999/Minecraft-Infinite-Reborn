# How the installer registers with launchers

Infinite registers itself as a version your launcher already knows how to run, so it shows up
in the version list next to everything else. The launcher then handles Java, memory, accounts,
natives and the game directory. Same shape as installing NeoForge.

That's what `InfiniteInstaller.jar` does with no arguments: find the launchers on the machine,
show them in a list with checkboxes, register into the ticked ones.

```sh
java -jar InfiniteInstaller.jar                    # find them and ask
java -jar InfiniteInstaller.jar --headless         # find them and do all the eligible ones
java -jar InfiniteInstaller.jar --register-mojang "%APPDATA%\.minecraft"
java -jar InfiniteInstaller.jar --register-prism  "<prism>/instances/Minecraft Infinite"
```

`--dir` overrides where `Infinite.jar` and `version.json` are read from. Default is the folder
the installer jar is sitting in. `--version` overrides the version string, which otherwise
comes out of `version.json`.

## Why this is so small

I checked the assumption before building any of it. Put the official Alpha 1.0.4 jar and
`Infinite.jar` on one classpath, run `net.minecraft.client.Minecraft`, and it runs. 33,856
recipes loaded, straight through to the display call.

So: **no loader, no tweaker, no asset extraction, no patching.** The 27 Mojang textures resolve
straight out of the official jar because it's already on that classpath, so everything the
standalone loader does to borrow them is pointless here.

Registration is a JSON file. That's the whole feature.

<details>
<summary><b>One gotcha about the vanilla a1.0.4 profile</b></summary>

The official a1.0.4 profile does **not** run `Minecraft.main`. It goes through LaunchWrapper
with `--tweakClass net.minecraft.launchwrapper.AlphaVanillaTweaker`, because vanilla alpha is
an applet and the injector fakes an applet context around it.

Infinite has a real `main`, so none of that applies. The Infinite profile overrides `mainClass`
and drops the tweaker argument.

If a tweaker were ever needed: LaunchWrapper 1.5 reads `--tweakClass` with `valueOf`, not
`valuesOf`, so it takes exactly one. A tweaker here would have to subclass
`AlphaVanillaTweaker` rather than sit next to it.

</details>

## Detection

`Launchers.findAll()` looks for two things.

**Mojang launchers.** A directory holding `launcher_profiles.json` or `versions/`. Checked at
`%APPDATA%\.minecraft`, `~/.minecraft`, `~/Library/Application Support/minecraft`, and
`~/curseforge/minecraft/Install`.

**Prism / MultiMC.** A directory holding both `instances/` and `libraries/`. These are portable
all the time so a fixed path list isn't enough. The scan walks three levels down from
`%APPDATA%`, `%LOCALAPPDATA%`, home, Desktop, Documents, Downloads, `~/.local/share`,
`~/Library/Application Support`, Program Files, and `<drive>:\Games`.

A Mojang launcher without `versions/a1.0.4/a1.0.4.json` gets listed but not selectable, with
the reason shown. The profile inherits from a1.0.4 so it has to be there first. Prism and
MultiMC pull it down themselves on first launch, so they're always selectable.

## Mojang launcher

Writes three things:

```
libraries/gg/infinite/infinite/<ver>/infinite-<ver>.jar   the mod, as a library
versions/infinite-<ver>/infinite-<ver>.json               the version profile
launcher_profiles.json                                    one added entry
```

The profile `inheritsFrom` a1.0.4, so it picks up that version's assets, arguments and
libraries and lists its own in front. That's also how LWJGL gets pulled forward from the 2.9.0
a1.0.4 ships with to the 2.9.4 the mod wants.

> [!WARNING]
> The launcher holds `launcher_profiles.json` in memory and writes it back on exit. **Close it
> before registering.** If you don't, your profile gets overwritten and it looks like the
> installer did nothing. This is the single most common reason a fresh profile "doesn't
> appear".

`launcher_profiles.json` is edited textually rather than reparsed and rewritten, because it
holds your accounts and every other profile you have and I'm not risking a reserialise
dropping a field I didn't model. A `.infinite-backup` is taken first, and re-running is a no-op
instead of a duplicate.

## Prism / MultiMC

Writes an instance directory under `<launcher>/instances/Minecraft Infinite <ver>`:

```
mmc-pack.json                        org.lwjgl + net.minecraft + custom.jarmod.infinite
patches/net.minecraft.json           local override of the a1.0.4 component
patches/custom.jarmod.infinite.json  the two jar mod entries
jarmods/InfiniteAssets-<ver>.jar     27 Mojang textures, built from the player's own copy
jarmods/Infinite-<ver>.jar           the mod, MMC-hint: local
instance.cfg                         name and JVM args
```

### Why it overrides net.minecraft

This is the part that is not obvious, and it cost two rounds to find.

Upstream's a1.0.4 component launches the game like this:

```json
"mainClass": "ax",
"+traits": [ "legacyLaunch", "no-texturepacks" ]
```

`legacyLaunch` is not a hint. It selects a **different launcher path** inside MultiMC, one
that wraps vanilla's obfuscated `ax` as an applet. Adding an Infinite component next to it
changes nothing: the mod lands on the classpath and is never called into.

> [!CAUTION]
> The failure mode is that **the instance launches perfectly into the base game**. No error,
> no warning, nothing in the log. It looks like a successful install that simply has no mod in
> it, which is a much more expensive bug than a crash would have been.

So the instance carries its own `net.minecraft` patch that drops `legacyLaunch` and names
Infinite's real main class:

```json
"mainClass": "net.minecraft.client.Minecraft",
"minecraftArguments": "${auth_player_name} ${auth_session}",
"+traits": [ "texturepacks" ]
```

A local patch **replaces** the component rather than merging into it, which is why the file
repeats `assetIndex`, `mainJar` and `requires` verbatim. Leave any of them out and the
instance loses that piece entirely.

### Why there is a second jar of textures

Infinite reads 27 original textures from Alpha 1.0.4, and it looks for most of them under
names it chose rather than Mojang's:

```
misc/shadow.png                <- shadow.png
gui/background/background.png  <- dirt.png
mob/creeper/creeper.png        <- mob/creeper.png
```

The standalone loader extracts them into `assets-cache/` with the rename applied. A launcher
install has no loader, and **a jar mod merge does not rename anything**, so every renamed
asset resolves to nothing.

> [!CAUTION]
> The visible result is entity shadows drawing as hard black-and-pink squares and the menu
> backgrounds drawing as the purple missing-texture checkerboard. The game runs fine, which
> is what makes it easy to mistake for a shader or GL problem rather than a missing file.

So the installer extracts those 27 from **the player's own jar** at install time, applies the
rename, and installs the result as a second jar mod listed before the mod itself. Nothing is
redistributed; the jar is assembled locally from a file they already have.

If the base jar is not on disk yet, because the launcher has never downloaded a1.0.4, the
install still completes and prints what will look wrong and how to fix it. Launch a1.0.4 once
and re-run the installer.

### Why a jar mod and not a library

MultiMC merges jar mods into the main jar, so Infinite's classes are present under the names
the game asks for, and the 27 Mojang textures still resolve out of the vanilla half of the
merge. Shipping it as a library instead puts it on the classpath but leaves the launch path
pointing at vanilla, which is the failure above.

Both of these were taken from a working instance rather than derived, after the derived
version was wrong twice.

Prism picks up new folders in `instances/` when it restarts. You can also zip that folder and
use **Add Instance → Import from zip**.

## Tested

| | |
| --- | --- |
| Classpath composition actually runs the mod | yes, launched it |
| Detection finds a Mojang launcher and a portable Prism, skips a Mojang without a1.0.4 | yes |
| All four generated files parse as JSON | yes |
| Existing profiles and `selectedProfile` survive | yes, checked against a populated file |
| Backup written before editing | yes |
| Re-running doesn't duplicate the profile or the instance | yes |
| Prism instance drops `legacyLaunch` and sets the real main class | yes, asserted in CI |
| Jar mod written to `jarmods/` and referenced by exact filename | yes, asserted in CI |
| 27 renamed textures extracted and byte-identical to the official jar | yes, asserted in CI |
| Install still completes when a1.0.4 has not been downloaded yet | yes, with a warning |
| An older library-layout instance is cleaned up on upgrade | yes |
| Mojang library lands in the maven tree | yes, unchanged |

> [!NOTE]
> MultiMC 0.7.0 accepts the generated instance and launches it. The component layout above is
> copied from a working instance rather than reasoned out, after reasoning it out produced a
> base-game launch twice. The Mojang launcher path is still only structurally verified.

## Versus the standalone loader

`InfiniteLoader.jar` still works and still self updates the mod jar. Registration is the
alternative for launching from Prism or the Mojang launcher, and in that mode the loader isn't
involved at all. Use one or the other, not both.
