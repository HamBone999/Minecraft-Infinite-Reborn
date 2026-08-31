# What each patch does

All patches are generated against upstream **Minecraft Infinite 1.0-280826**, pinned in
`base/`. Reborn is a set of diffs on top of that release, so an upstream bump means re-pinning
and rebasing rather than merging two histories.

> [!IMPORTANT]
> Upstream changed `PROTOCOL_VERSION` to `20260823` and `NetLoginHandler` rejects any
> mismatch outright, so client and server must ship together. A client on the old protocol is
> turned away with "Outdated client!".

When this set was rebased onto 280826 it was 9 patches and 7 applied unchanged; the two that
did not were context drift rather than conflicts — upstream had moved code inside
`Player.onTick` and renamed a parameter of `NetServerHandler.handleSlashCommand`. Expect the
same shape on the next bump: re-pin `base/`, re-run setup, and hand-rebase whatever rejects.

## Server, `patches/`

| Patch | Fixes |
| --- | --- |
| `0001-register-extra-commands-in-help` | custom commands were missing from `/help` |
| `0002-teleporters-shared-and-safe-landing` | teleporters only worked for whoever placed them; arrivals landed inside rock |
| `0003-portal-leads-to-the-void-not-hell` | the void portal frame sent you to the Crimson |
| `0004-void-fall-and-clients-see-fire` | makes `Entity.onFellOutOfWorld` a hook; also **upstream #33** -- the client blanked `Entity.fire` every tick, and `EntityRenderer` draws flames off that field, so nothing ever looked alight in multiplayer |
| `0005-pets-stay-sitting-when-told-to` | **upstream #5 / #27** -- the sit action bailed out while the owner was fighting nearby, which cleared the mob's sitting flag while the order stayed set, so pets stood up and teleported mid-fight |
| `0006-fox-held-item-is-visible` | **upstream #18** -- `Fox.heldItem` was server-only, so `FoxRenderer.renderEquippedItems` always drew an empty-handed fox |
| `0007-pet-interaction-consumes-the-click` | **upstream #26** -- the pet interaction toggled sitting then reported the click unhandled, so the caller also ate the food you were holding |
| `0008-frozen-mobs-look-frozen` | **upstream #23** -- `Mob.freeze` never left the server, so `MobRenderer`'s tint never triggered |
| `0009-dimension-requests-and-velocity-hook` | dreamcatcher/void-fall routing, crawl persistence, and the velocity hook `Explosion` needs |
| `0010-explosion-knockback-reaches-the-player` | **upstream #21** -- knockback was applied server-side and never transmitted; ordinary explosions hid it because the damage still landed, the wind creeper deals none |
| `0011-random-block-ticks-must-not-generate-chunks` | random ticks were driving terrain generation, causing `Can't keep up` |
| `0012-dimension-arrival-at-slipgate-and-commands` | arrivals landed on the Crimson's roof instead of at the gate |
| `0013-connection-throttle-window-and-no-re-arm` | random `End of stream` rejections |
| `0014-teleports-velocity-and-chunk-queue` | teleports tripped the movement check; chunks arrived one per movement packet |
| `0015-server-can-locate-structures` | `ChunkProviderServer.findClosestStructure` was a hardcoded `return null`, so nothing server-side could ever find a structure. The Crimson Eye always answered "no slipgate found nearby" in multiplayer, and slipgate arrivals could never aim at the gate on the far side |

### Diagnosed but NOT fixed

**Upstream #30, item sorting.** `ChestScreen`'s sort button calls `setSlotContents` and sends
no packet -- it reorders the container purely client-side. That works in singleplayer, where
client and server share a world, and is overwritten by the next container sync in multiplayer.
Fixing it needs a new packet and a server-side handler, which is a protocol addition rather
than a bug fix.

### Three issues investigated and found NOT to be bugs

Recorded so nobody re-treads them: **#22** (whip) -- the released-item dispatch chain is wired
correctly through `NetServerHandler`; **#36** (variation) -- `Mob` persists it as the `Skin` tag
and re-applies it via `setType()` on load; **#3** (sheep feeding) -- `Animal` has no breeding
code at all, so it is a missing feature rather than a broken one.

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

| Patch | Adds |
| --- | --- |
| `0001-title-menu-mods-discord-and-scaling` | removes the Changelog button, adds the Mods and Discord buttons, centres the icon row |
| `0002-controller-drives-look` | polls the gamepad and folds the right stick into the camera delta |
| `0003-controls-opens-controller-screen` | the **Controller...** button on the Controls screen |

`0001-title-menu-mods-discord-and-scaling` — removes the Changelog button, adds the Mods and
Discord buttons, and centres the icon row under Options and Quit Game.

`ScreenRescaler` raises the scale while `height / (scale + 1)` stays at or above 240, so the
GUI can be as short as 240 units — which is what AUTO picks on a large display. Dropping the
Changelog row brought the block down to 124px, which fits at 240 once it is clamped off the
bottom edge, so there is a single layout at every scale rather than a compact fallback.

Client classes that are entirely ours live in `client/src` instead: `ModsScreen`.

### Controller support

Gamepad buttons drive the **existing key binds** rather than the movement code, so a controller
behaves exactly like the keyboard everywhere — including inside addons, which never learn a
controller was involved. Sticks are the only analogue path: the left one presses the movement
binds past a deadzone, the right one feeds `MouseHelper` the same delta the mouse does.

`ControllerInput` and `ControllerScreen` are entirely ours and live in `client/src`. Bindings
persist to `controller.properties` in the game folder.

> [!IMPORTANT]
> The poll is hooked into `MouseHelper.moveMouse`, which `GameRenderer` calls every frame while
> playing — **not** `Minecraft.runTick`. `Minecraft.java` is one of the classes that does not
> survive a decompile/recompile round trip here (it loses `Minecraft$2`), so it is left alone.

> [!WARNING]
> Build `ControllerInput` against a real `lwjgl.jar`, never hand-written stubs. A wrong method
> descriptor compiles cleanly and then throws `NoSuchMethodError` in front of players. The
> `Controllers` / `Controller` API is stable across 2.9.x, so 2.9.3 from Maven Central is a valid
> compile-time stand-in for the 2.9.4-nightly the launcher ships.

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
