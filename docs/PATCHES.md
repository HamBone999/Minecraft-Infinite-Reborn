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
| `0002-teleporters-shared-and-safe-landing` | teleporters only worked for whoever placed them; arrivals landed inside rock, or on the roof of the building the teleporter was in |
| `0003-portal-leads-to-the-void-not-hell` | the void portal frame sent you to the Crimson |
| `0004-leaving-the-world-top-and-bottom-and-fire` | makes `Entity.onFellOutOfWorld` a hook; also **upstream #33** -- the client blanked `Entity.fire` every tick, and `EntityRenderer` draws flames off that field, so nothing ever looked alight in multiplayer |
| `0005-pets-stay-sitting-when-told-to` | **upstream #5 / #27** -- the sit action bailed out while the owner was fighting nearby, which cleared the mob's sitting flag while the order stayed set, so pets stood up and teleported mid-fight |
| `0006-fox-held-item-is-visible` | **upstream #18** -- `Fox.heldItem` was server-only, so `FoxRenderer.renderEquippedItems` always drew an empty-handed fox |
| `0007-pet-interaction-consumes-the-click` | **upstream #26** -- the pet interaction toggled sitting then reported the click unhandled, so the caller also ate the food you were holding |
| `0008-frozen-mobs-and-boss-health-sync` | **upstream #23** -- `Mob.freeze` never left the server, so `MobRenderer`'s tint never triggered. Also **upstream #29**: `MobPacket` carries no health and nothing else sent it, so a client's copy of a boss stayed at its constructed value and the boss bar never moved |
| `0009-dimension-requests-velocity-hook-and-god-mode` | dreamcatcher/void-fall routing, crawl persistence, the velocity hook `Explosion` needs, and the `godMode` check in `damageEntity` that `/god` sets |
| `0010-explosion-knockback-reaches-the-player` | **upstream #21** -- knockback was applied server-side and never transmitted; ordinary explosions hid it because the damage still landed, the wind creeper deals none |
| `0011-random-block-ticks-must-not-generate-chunks` | random ticks were driving terrain generation, causing `Can't keep up` |
| `0012-slipgate-arrival-commands-and-wand` | slipgate arrivals: the entry position is captured before the player entity is replaced, and arrivals land in the gate itself -- the shaft in the Crimson, the surrounding chamber everywhere else. Also lets the selection wand consume clicks |
| `0016-chunk-relocation-actually-relocates` | a mislocated chunk could never be repaired, which froze any player who walked into it |
| `0018-respawn-loads-the-bed-chunk` | **upstream #25** -- `wakeUpPlayer` only reads the block, and an unloaded chunk answers with the dummy all-air chunk, so a bed reported itself missing whenever nobody was near it. Also lands a dimension arrival at the player's bed, which `recreatePlayerEntity` cannot do because it reads `getSpawn(dimension)` *before* assigning the new one |
| `0019-one-guardian-per-treasure-room` | **upstream #42** -- `hasSpawnedBoss` is an instance field on the structure piece and is never written to NBT, so every reconstruction spawned another Guardian on top of the last |
| `0017-help-grouped-ranked-and-paged` | `/help` was one flat alphabetical burst of every command; now grouped by origin, ordered player-then-operator, and paged |
| `0013-connection-throttle-window-and-no-re-arm` | random `End of stream` rejections |
| `0014-teleports-velocity-and-chunk-queue` | teleports tripped the movement check; chunks arrived one per movement packet |
| `0015-server-can-locate-structures` | `ChunkProviderServer.findClosestStructure` was a hardcoded `return null`, so nothing server-side could ever find a structure. The Crimson Eye always answered "no slipgate found nearby" in multiplayer, and slipgate arrivals could never aim at the gate on the far side |

## The command set

Almost everything added in 1.0-100926 is a **new class** in `sources/`, not a patch. The only
edits to existing code are two hooks: a `godMode` check in `Player.damageEntity` (patch 0009)
and the wand consuming clicks in `NetServerHandler` (patch 0012). The classes:

| Class | Does |
| --- | --- |
| `Names` | friendly names for blocks, items, effects and enchantments, read off the vanilla `*List` classes by reflection. A block added upstream is spellable the day it lands. Wrong names suggest right ones. |
| `Cmd` | argument parsing shared by all of them, including `~` relative coordinates |
| `Edits` | the one bulk-block-writing path, and the undo history behind it |
| `WorldCommands` | `/setblock` `/fill` `/clone` `/summon` `/kill` `/clear` `/effect` `/enchant` `/xp` `/say` `/spawnpoint` |
| `PlayerCommands` | `/god` `/killall` `/up` `/ascend` `/descend` `/light` |
| `Selection`, `WandHook`, `WorldEditCommands` | the `//` region editor |

### Why bulk edits go through `Edits`

Two things make them dangerous, and both are handled once rather than per command:

**Notify-per-block stalls the tick.** Writing a large region with neighbour physics is
hundreds of thousands of block updates inside one tick. So the bulk write is silent and the
region is marked dirty once at the end, which resends the chunks without running physics --
the same trade WorldEdit makes.

**Writing into an unloaded chunk generates one, mid-tick.** That is the exact cause of the
`Can't keep up` bug fixed in patch 0011, so every write is gated on `World.blockExists` and
edits report how many blocks they skipped rather than silently generating terrain.

Recording the overwritten blocks is built into the write path rather than bolted onto the
commands, so `/fill` and `/clone` land on the same eight-deep undo stack as the `//` commands
and `//undo` takes back the last edit whichever command made it.

> [!NOTE]
> `/kill` uses `DamageSource.outOfWorld`, which bypasses god mode deliberately -- otherwise an
> op could switch on `/god` and have no way to kill themselves.

> [!NOTE]
> The wand is **opt-in per player** and off by default. WorldEdit proper treats any wooden axe
> as a wand for anyone with permission; on a server where the ops also play, that means an op
> cannot chop a tree without moving their selection. `//wand` toggles it. There is no wooden
> axe in this build, so the wand is a stone axe.

### Not included: /fly, /noclip, /instantmine

These three are in every SinglePlayerCommands list and are **deliberately absent**, because
they cannot be done from the server.

Player movement here is simulated by the client and only checked by the server, and this build
has no player flight at all: nothing anywhere sets `Entity.flying` for a player, and
`LocalPlayer` reads the field only to widen the FOV. `Player.moveFlying` would supply
horizontal speed, but nothing cancels gravity. Creative and spectator are exempt from the
float kick in `NetServerHandler`, so the anticheat is not the obstacle -- the absence of any
flight code is.

Adding them means new client input and motion code plus a packet granting the capability, and
a matching `PROTOCOL_VERSION` bump. That is a feature spanning both jars, not a command, so it
belongs in its own release rather than riding along with thirty commands.

`/difficulty` is absent for a simpler reason: this build has no difficulty setting to change.

## Slipgates

A slipgate is **carved terrain, not a structure**: `Slipgate.generate` only clears blocks. In
the overworld it cuts through the floor, so you fall out of the bottom of the world and
`onFellOutOfWorld` sends you to the Crimson. In the Crimson it cuts through the roof instead,
which is why `onNearWorldTop` exists -- the same trick upside down.

Both now hand over **inside the shaft** rather than after a long fall or climb, gated on the
column actually being open at the carve height. Without that gate the bottom trigger would fire
for anyone standing in a mine at bedrock level; a sweep of 1089 overworld columns and 625 roof
columns near spawn produced zero false positives.

> [!IMPORTANT]
> `recreatePlayerEntity` builds a **new** `EntityPlayerMP` in the destination world and
> `setPosAndRot`s it to that dimension's spawn. Anything that needs to know where the player
> *came from* must capture it before that call. Reading `posX/posZ` afterwards made the slipgate
> lookup search around the destination's spawn instead of the gate that was walked into, so it
> answered "no slipgate nearby" for every gate in the world.

Arrival placement differs by dimension because the geometry does. The Crimson's shaft has a
floor -- the roof slab it was cut through -- so you land on it. Everywhere else the inner shaft
is bottomless and there is nothing to stand on, so arrivals go to the **outer ring**, which
`Slipgate.generate` clears only from y8 up and floors with scattered netherrack. That ring is
generated with a coin flip per column, so the search takes the nearest column that actually has
footing rather than assuming any given one does.

### Teleporters

> [!IMPORTANT]
> `Player.teleporterPositionsIn/Out` are **never written to NBT**. The stock per-player endpoints
> do not survive quitting, so `TeleporterRegistry` is the only durable record a teleporter has.
> Anything that stops the registry persisting takes every teleporter in the world with it.

The registry file is asked for through the world's save handler rather than hardcoded. It used
to be `world/teleporters.tsv`, which is the world folder only on a dedicated server -- a
singleplayer install keeps its worlds under `saves/`, so the file was never found and never
written. Combined with the NBT gap above, every singleplayer teleporter forgot its destination
on quit and then reported its output missing, which was true. A legacy file at the old path is
read once if the new one is absent, so servers written by an earlier build keep their endpoints.

The arrival check tests `isBlockFull`, not `id != 0`. A torch, rail, sign, snow layer or plant
above a teleporter counted as blocking when it merely asked whether the space was non-air.

The two failure modes are reported separately. `tile.teleporter.occupied` reads "Teleporter
output is missing!", which is accurate when there is no endpoint and badly misleading when the
endpoint is right there with something standing on it.


`TeleporterRegistry` is a new class of ours, and `Teleporter` -- a **shared** game class -- calls
it. Shared classes are patched into both jars, so a new class they depend on has to be shipped
in both too. It was only ever added to the server jar, so in a singleplayer world, where the
client runs the world itself, placing or breaking a teleporter threw
`NoClassDefFoundError: TeleporterRegistry` straight out of the game loop. The crash landed
between placing the block and consuming the item, which is where the duplicate teleporters came
from.

> [!IMPORTANT]
> Anything in `sources/` that a **shared** class references must be overlaid into the client jar
> as well as the server jar. The server build does this automatically; the client build is
> assembled separately and does not.

The arrival check is now just the column directly above the block: the two blocks a player
occupies have to be clear, or the teleporter reports itself occupied. It used to scan eight
blocks upward for any gap it could find, which put an arriving player through the ceiling and
onto the roof of their own base, and it fell back to `y + 3` when it found nothing -- a guess
that can be inside a wall. Only two blocks are required rather than three so an ordinary
two-high room still works.

> [!WARNING]
> 1.0-200926 shipped the caller for this without the function itself. A multi-part edit had its
> first hunk fail, which aborted the script before the second, and the rebuilt jar was verified
> for the version string rather than for the behaviour. The caller tested for a refusal the
> function could never return, so the roof bug was still live in a release whose notes said it
> was fixed. Verify the logic, not the presence of a build.

## /help

`/help` walked the Brigadier root and printed every node in one flat alphabetical run. Fine for
a dozen vanilla commands, useless at sixty: nothing connected `/claim` to `/trust`, the stock
admin commands sat above every heading looking like anyone could run them, and the top of the
list scrolled out of chat before it could be read.

`HelpCategories` is the registry behind the grouping. Addons reach it the same way they reach
the rest of the game -- it is in the server jar, already on their classpath -- so grouping cost
no addon API change. Anything unclaimed stays in the first unheaded group, which keeps plain
vanilla `/help` looking as it did.

> [!IMPORTANT]
> The `//` region editor cannot be listed from the command tree. `handleSlashCommand` consumes
> those lines before Brigadier sees them, and a Brigadier literal cannot usefully be named
> `/set`, so they are registered as plain display lines instead. Without that they are invisible
> and you have to already know they exist.

Ranking within a category was not enough on its own. Sorting operator commands last is invisible
-- nothing on screen says where `/warp` stops and `/setblock` starts -- so the operator half
gets its own heading rather than just a lower sort position.

Page size is measured, not guessed: at 12 lines the previous page was still visible underneath,
so it is 18.

> [!NOTE]
> `/setspawn` used to write only the `/spawn` teleport point, so "set spawn" moved where `/spawn`
> sent you and nothing else -- new players still arrived, and everyone still respawned, wherever
> the world was generated. It now sets the dimension's real spawn as well, and `/spawnpoint`
> writes both too, because two records of "spawn" is two things that can disagree.

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
| `0004-rebuild-chunk-renderers-on-dimension-change` | the sky, fog and particles of the dimension you left persisted into the new one |
| `0005-world-height-before-the-renderer-is-built` | terrain above the client's *default* world height was solid but never drawn |

`0001-title-menu-mods-discord-and-scaling` — removes the Changelog button, adds the Mods and
Discord buttons, and centres the icon row under Options and Quit Game.

> [!IMPORTANT]
> `setMaxSections` must be applied to the world **before** `Minecraft.changeWorld`, because
> that call ends in `worldRenderer.changeWorld` -> `reloadChunks`, which sizes the chunk
> renderer array with `chunksHeight = world.getMaxSections()`. It used to be set on the line
> after, so the array was built from the client's own default (`WorldSettings.heightInt`, 12 or
> 16) rather than the server's. On a taller server every section above that default had no
> renderer at all: the blocks arrived, collided and pick-blocked normally, and were simply never
> drawn, leaving a hard horizontal cut with solid invisible terrain above it.

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
