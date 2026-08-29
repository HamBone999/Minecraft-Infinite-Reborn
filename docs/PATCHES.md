# What each patch does

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

`0001-title-menu-mods-discord-and-scaling` — adds the Mods and Discord buttons, centres the
icon row, and falls back to a compact layout when the GUI is short. `ScreenRescaler` allows
heights down to 240 and the roomy layout needs 152px, so it stops fitting under about 283.

Client classes that are entirely ours live in `client/src` instead: `ModsScreen`.

## Loader, `loader/patches/`

| Patch | Fixes |
| --- | --- |
| `0001-profiles` | the installer now installs the instance icon |
| `0002-launchers` | finds Prism and MultiMC installed through Flatpak (`~/.var/app`), which is how they come on the Steam Deck. It was not a search root, **and** the scanner skips dot-directories, so walking `~` could never reach it. |

> [!NOTE]
> These are diffs against a decompile of `InfiniteLoader.jar`. If you still have the original
> loader source, make the same change there rather than applying these -- the decompiled form
> is only a stand-in.
