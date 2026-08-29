# What each patch does

All patches are generated against upstream **Minecraft Infinite 1.0-280826**, pinned in
`base/`. Reborn is a set of diffs on top of that release, so an upstream bump means re-pinning
and rebasing rather than merging two histories.

> [!IMPORTANT]
> Upstream changed `PROTOCOL_VERSION` to `20260823` and `NetLoginHandler` rejects any
> mismatch outright, so client and server must ship together. A client on the old protocol is
> turned away with "Outdated client!".

When rebasing onto a new upstream, 7 of these 9 applied unchanged; the two that did not were
context drift, not conflicts — upstream moved code around inside `Player.onTick` and renamed a
parameter in `NetServerHandler.handleSlashCommand`.

## Server, `patches/`

| Patch | Fixes |
| --- | --- |
| `0001-register-extra-commands-in-help` | custom commands were missing from `/help` |
| `0002-teleporters-shared-and-safe-landing` | teleporters only worked for whoever placed them; arrivals landed inside rock |
| `0003-portal-leads-to-the-void-not-hell` | the void portal frame sent you to the Crimson |
| `0004-void-fall-is-overridable` | makes `Entity.onFellOutOfWorld` a hook rather than an inline `kill()` |
| `0005-dimension-requests-and-void-fall-routing` | `switchDimension` was an empty method, so the dreamcatcher did nothing; crawl state was not persisted; void falls now route per dimension |
| `0006-random-block-ticks-must-not-generate-chunks` | random ticks were driving terrain generation, causing `Can't keep up` |
| `0007-dimension-arrival-at-slipgate-and-commands` | arrivals landed on the Crimson's roof instead of at the gate |
| `0008-connection-throttle-window-and-no-re-arm` | random `End of stream` rejections |
| `0009-teleports-and-chunk-queue-refill` | teleports tripped the movement check; chunks arrived one per movement packet |

### Two things deliberately NOT fixed

**The `tickBlocks` loop nesting.** It produces tens of thousands of samples per tick instead
of 80. Correcting it changes random tick rates -- crop growth, leaf decay, fire spread -- by
orders of magnitude. That is a gameplay change, not a bugfix.

**`mods/` do not load on a launcher-registered profile.** `Profiles.installPrism` writes
`mainClass: net.minecraft.client.Minecraft` with no tweaker, so LaunchWrapper and Mixin sit
on the classpath unused and `infinite.core.mixins.json` never applies. Only the standalone
`InfiniteLoader.jar` loads addons. The Mods screen reports what is *installed*, which is
honest, but nothing there is running until this is fixed.

## Client, `client/patches/`

`0001-title-menu-mods-discord-and-scaling` — removes the Changelog button, adds the Mods and
Discord buttons, and centres the icon row under Options and Quit Game.

`ScreenRescaler` raises the scale while `height / (scale + 1)` stays at or above 240, so the
GUI can be as short as 240 units — which is what AUTO picks on a large display. Dropping the
Changelog row brought the block down to 124px, which fits at 240 once it is clamped off the
bottom edge, so there is a single layout at every scale rather than a compact fallback.

Client classes that are entirely ours live in `client/src` instead: `ModsScreen`.

> [!NOTE]
> `ModsScreen` reads the `mods/` folder directly rather than asking `ModEngine.loadedMods()`.
> The `infinite/` classes are not in the client jar at all, and on a launcher-registered
> profile the loader never runs, so there would be nothing to ask. Reading the folder gives
> the same answer in every launch path.

## Loader, `loader/patches/`

| Patch | Fixes |
| --- | --- |
| `0001-profiles` | the installer now installs the instance icon |
| `0002-launchers` | finds Prism and MultiMC installed through Flatpak (`~/.var/app`), which is how they come on the Steam Deck. It was not a search root, **and** the scanner skips dot-directories, so walking `~` could never reach it. |

> [!NOTE]
> These are diffs against a decompile of `InfiniteLoader.jar`. If you still have the original
> loader source, make the same change there rather than applying these -- the decompiled form
> is only a stand-in.
