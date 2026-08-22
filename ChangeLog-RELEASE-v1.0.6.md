# Minecraft Infinite v1.0.6

A stability and multiplayer release. The headline items are a fix for the memory leak that
made long sessions end in an out-of-memory crash, a working dedicated server with real
Mojang account verification, and full creative mode support in multiplayer.

**Both the client jar and the server jar changed.** Every player needs the new client.

---

## Contents

- [Memory and stability](#memory-and-stability)
- [Client crashes](#client-crashes)
- [Multiplayer: the server](#multiplayer-the-server)
- [Multiplayer: account verification](#multiplayer-account-verification)
- [Creative mode in multiplayer](#creative-mode-in-multiplayer)
- [Sprinting](#sprinting)
- [Miscellaneous](#miscellaneous)
- [Upgrade notes](#upgrade-notes)
- [Verification](#verification)
- [Known issues and still open](#known-issues-and-still-open)
- [Full change footprint](#full-change-footprint)

---

## Memory and stability

### Chunk terrain cache was never freed

`ChunkProvider.terrainMap` was a write-only cache. A whole-tree search for it returned
exactly three references: the declaration, a `get`, and a `put`. There was no removal
path of any kind — `unloadOldChunks()` cleaned `chunkMap`, `chunkList` and
`droppedChunks` but never touched it.

Each entry is expensive. `justGenerateForHeight` builds a complete `Chunk`, and the
`short[]/byte[]` constructor allocates a `ChunkSection` for **every** y-slice, including
slices that are entirely air. At 20 sections that is roughly **246 KB retained per chunk
coordinate**, held for the life of the world.

It fills constantly during normal play. `Labyrinth.canSpawnHere` calls into it, Labyrinth
is the default fallback structure for most biomes, and `StructureList` probes an
84-coordinate window around every newly generated chunk. The same path is reached from
`World.getSurfaceHeightmap`, used by Pyramid, Tower, OceanMonument, Shipwreck, Fossil, and
by `Castle.generateSchematic` in a loop.

Touching 5,000–15,000 coordinates over half an hour of exploring retains 1.2–3.6 GB.

**Fixed:** the cache is now an access-ordered LRU capped at 256 entries — a fixed ceiling
of roughly 63 MB. The cache only exists to avoid regenerating terrain during a burst of
nearby structure probes, and 256 entries comfortably covers the 84-coordinate probe
window, so the speed benefit is retained in full.

Worldgen output is unaffected: all three generator entry points (`justGenerateForHeight`,
`generateChunk`, `populate`) reseed their RNG deterministically from chunk coordinates and
the world seed, so a chunk regenerated after eviction is identical to the one evicted.

### Chunk unloading could stop permanently

```java
Chunk chunk = (Chunk)this.chunkMap.get(hash);
if (chunk == null) {
   return false;          // hash left in droppedChunks forever
}
...
this.droppedChunks.remove(hash);
```

If a hash ever reached `droppedChunks` without a matching `chunkMap` entry, this returned
before the removal. The next call pulled the same stale hash off the iterator and bailed
at the same line. **Chunk unloading stopped for the rest of the session**, and
`chunkMap`/`chunkList` then grew without bound until the game ran out of memory.

**Fixed:** the stale entry is dropped and the loop continues.

The route in has also been closed: `loadChunk` no longer registers the shared `emptyChunk`
under a real coordinate. That instance carries coordinates `(0,0)`, so the unloader would
later call `dropChunk(0, 0)` for it and create exactly the stale hash above. Registering
one shared instance under many keys also desynced `chunkMap` from `chunkList`, because
`chunkList.remove(chunk)` only removes the first match.

### GL texture leaked on every screen close

`GuiScreen.setResolution` allocates a screen-sized `GL_RGB` texture — roughly 6 MB at
1080p — for the background blur. It was freed only in `GuiScreen.onClose()`, and twelve
subclasses overrode `onClose()` without calling `super`, so the delete never ran for them.
Since `Minecraft` opens a fresh `ChatScreen` on every press of the chat key, ordinary
play leaked driver memory continuously.

**Fixed:** the release moved into its own `GuiScreen.releaseViewportTexture()`, which
every screen calls directly. It is deliberately not done by chaining to `super.onClose()`
— `WorldOptionsScreen` and `BiomeToggleScreen` extend `SettingsScreen`, whose `onClose()`
dereferences an `OptionsList` those two never populate, so chaining there throws.

Two related fixes: `setResolution` no longer orphans the previous texture when a screen is
re-laid-out on window resize, and the release is idempotent so a double close cannot
delete a handle the driver has already recycled.

---

## Client crashes

### Disconnecting mid-tick crashed the game loop

```
java.lang.NullPointerException
  at net.minecraft.client.Minecraft.runTick(Minecraft.java:808)
```

The world-tick block was guarded by `this.world != null` and then read `this.player`
repeatedly. On a disconnect — a kick, a timeout, a dropped connection — the network read
thread tears the session down and nulls `this.player` while the game thread is midway
through that block.

**Fixed:** both fields are read once into locals. Another thread can still null the
fields, but it cannot null a local reference, so the tick completes against the world it
started on and the disconnect is handled normally on the next tick.

Two more unguarded dereferences of the same kind were fixed in the same method: closing
the sleep screen, and scrolling the hotbar.

### Closing the world-creation options screen crashed

```
java.lang.NullPointerException
  at ...options.SettingsScreen.onClose(SettingsScreen.java:95)
  at ...worldselect.WorldOptionsScreen.onClose(WorldOptionsScreen.java:223)
```

`SettingsScreen.onClose()` calls `this.options.saveOptions()`, but `WorldOptionsScreen`
and `BiomeToggleScreen` extend it for its list-widget layout and never populate that
field. Covered by the `releaseViewportTexture` change above, which avoids routing those
screens through their superclass's `onClose()`.

### Inventory updates could crash while the creative screen was open

```
java.lang.IndexOutOfBoundsException: Index: 47, Size: 46
  at Container.getSlot -> Container.putStackInSlot -> ClientHandler.handleSetSlot
```

See [Creative mode in multiplayer](#creative-mode-in-multiplayer) below — this was the
same defect that caused items to appear duplicated.

---

## Multiplayer: the server

The dedicated server now runs, accepts logins, and streams chunks cleanly. The client and
server jars were already protocol-compatible — both declare `PROTOCOL_VERSION = 20260630`
and register 68 packets at identical ids with byte-identical wire formats — so no protocol
work was needed.

### Chunk data could be silently truncated

```java
this.chunkData = new byte[i1];                        // buffer sized to the UNCOMPRESSED length
this.tempLength = deflater.deflate(this.chunkData);   // one call, no finished() check
```

Deflate output can exceed its input when data does not compress. `deflate()` then fills
the buffer, returns its length, and the remainder is discarded — the client receives a
truncated stream and throws `IOException: Bad compressed data format`, dropping the
connection.

**Fixed:** the output buffer allows for worst-case expansion and the deflater is driven
until it reports finished, growing the buffer if needed.

Measured against 106,752-byte payloads: realistic terrain compressed to 4,528 bytes and
was always fine, but **300 of 300 incompressible payloads truncated before the fix and 0
of 300 after**, with realistic terrain still compressing 23.8× and round-tripping
correctly.

### Chunk packing used a shared static buffer

`UpdateChunkPacket` used a plain `static byte[]` shared by both the packing constructor
and `readPacketData`, across every thread in the JVM. Now per-thread.

### The same two memory leaks

The server carried identical copies of the `terrainMap` and `unloadOldChunks` defects
described above. Both fixed identically. This matters more on a server, which runs for
days rather than a single play session.

### Players were kicked for moving normally

```java
if (dx*dx + dy*dy + dz*dz > 100.0) {     // 10 blocks
   kickPlayer("You moved too quickly :( (Hacking?)");
}
```

This is a distance-per-packet limit, not a speed limit — it has no notion of how much time
passed since the previous packet. The client only sends position from its tick loop, so
any stutter delays a packet and multiplies the distance in the next one. Terminal velocity
is about 3.9 blocks/tick, so three dropped ticks while falling already exceeds 10 blocks.
Creative flight approaches the limit on its own.

**Fixed:** the allowance now scales with elapsed time, with a larger budget for creative
and spectator because flight is much faster than walking. Elapsed time is clamped to one
second so a client that goes quiet cannot bank an unlimited teleport. The warning now logs
the numbers:

```
Smoke300 moved too quickly! (300.0 blocks in 94ms, limit 11.3)
```

### Missing server icon spammed the console

`NetLoginHandler.handleDisconnect` called `ImageIO.read` on `server-icon.png`
unconditionally. With no icon present — the normal case — that threw `IIOException` and
printed a full stack trace on **every server-list ping**.

**Fixed:** the file is checked first, and the result is null-checked. `ImageIO.read`
returns null rather than throwing when a file exists but is not a readable image, and the
old code's `icon.getWidth()` then threw an NPE that escaped the `IOException` catch
entirely.

### Health and food were never synced

`EntityPlayerMP` sent `new EntityHealthPacket(this.health)` — the one-argument
constructor, which leaves the packet's `food` and `foodSaturation` fields at zero. The
client writes those straight into its food stats, so any health change zeroed the client's
hunger, and food never synced on its own because the packet was only sent when health
changed.

**Fixed:** the packet carries the real values and is sent when either changes.

---

## Multiplayer: account verification

`online-mode=true` now performs genuine Mojang account verification.

### Why it never worked

Two independent problems. The server authenticated against
`https://session.minecraft.net/game/checkserver.jsp` — part of Mojang's legacy Yggdrasil
system, which has been shut down. And the check was inverted:

```java
if (!string4.equals("YES")) {
   NetLoginHandler.this.packet1login = packet1Login1;         // accept
} else {
   NetLoginHandler.this.kickUser("Failed to verify username!"); // reject
}
```

A player who **passed** verification was kicked; one who **failed** was let straight in.

### The port

Both halves were moved to the current endpoints:

- **Client** — POSTs to `https://sessionserver.mojang.com/session/minecraft/join` with
  `{accessToken, selectedProfile, serverId}`. HTTP 204 means success.
- **Server** — GETs `https://sessionserver.mojang.com/session/minecraft/hasJoined`, then
  **compares the profile name Mojang returns against the name the client claimed**.
  Without that check a player could authenticate as themselves and then log in under
  someone else's name.

The credentials were already available: the launcher passes them in the second
command-line argument as `token:<accessToken>:<profileUuid>`, which lands in
`Session.sessionId`. Offline accounts get a bare `-`, which the client detects to fail
with a readable message rather than a stack trace.

Vanilla derives `serverId` from the encryption handshake. This build has no encryption, so
both sides use the raw random nonce — Mojang treats `serverId` as an opaque correlation
string, so only agreement between the two sides matters.

Failure cases now produce plain-English disconnect messages: offline mode, expired session
(HTTP 403), rate limiting (HTTP 429), and auth servers unreachable are all distinguished.

`online-mode` still defaults to `false`, so existing setups are unaffected until it is
explicitly enabled.

### Security limitations

This verifies accounts. It does **not** encrypt the connection.

Nobody can log in under a username they do not own. However, there is no cryptographic
binding between the verified session and the TCP connection — vanilla achieves that by
folding the server's RSA public key into the `serverId` hash, which is not possible
without the full encryption handshake. A player tricked into connecting to a malicious
server could have their authentication relayed.

This is the security level vanilla had before 1.3, which is precisely why encryption was
added. **Running with `white-list=true` is recommended**, since a relayed session still
has to belong to an invited player.

---

## Creative mode in multiplayer

Creative mode previously did not exist on a dedicated server. There was no `/gamemode`
command, no way to tell a client what mode it was in, and no creative behaviour.

### `/gamemode`

`GamemodeCommand` existed only in `net.minecraft.client.commands.client` and declared
`EnvType.CLIENT`, so a dedicated server had no such command.

Two pieces already existed and were reused: `Player.properties.gamemode` lives on the
shared `Player` class and is already persisted to NBT as `"Gamemode"`, and the shared game
code checks `properties.gamemode == 1` in dozens of places — food, potions, bow, crossbow
and musket ammunition, dye, item frames, damage immunity, drowning immunity. That shared
code runs on the server, so setting the field correctly provides most of creative
behaviour.

What was missing was a way to inform the client, since `LoginPacket` has no gamemode
field. The value is now pushed over `CustomPayloadPacket` (packet id 250, already present
in both jars) on channel `IP|GM`, both on join and whenever `/gamemode` changes it. No new
packet type and no change to the packet table.

```
/gamemode creative              # yourself
/gamemode 1                     # equivalent
/gamemode survival PlayerName   # someone else
/gm c                           # alias
```

Accepts `survival|creative|adventure|spectator`, their initials, or `0`–`3`.
Operator-only.

### The object-type check pattern

A family of bugs shared one cause. Code throughout the client tested
`mc.gamemode instanceof CreativeMode`. In multiplayer that object is always
`MultiplayerMode` — never `CreativeMode` — because it is the class that sends dig and
place packets, and swapping it out would break block interaction entirely.

Every affected site now tests `player.properties.gamemode == 1` instead:

| Location | Symptom before |
|---|---|
| `PlayerController` double-tap-jump | creative flight could not be toggled |
| `PlayerController` flight key | same |
| `PlayerController.clickBlock` | creative used the survival break-timer path |
| `HeadsUpDisplay` | creative showed the survival HUD offset |
| `CreativeScreen.update()` | creative screen bounced back to the survival inventory |
| `CreativeScreen.initGui()` | creative screen UI was never built |

### Flight

Flight already existed as `player.flying`; only the toggles were gated by the check above.
Additionally, nothing in the codebase ever reset the flag, so leaving creative left the
player hovering — in singleplayer as well as multiplayer. It is now cleared when switching
to a mode that does not permit flight. Creative and spectator keep it.

The flag is persisted to NBT as `"Flying"`, so this also covers worlds saved mid-flight:
singleplayer re-applies the gamemode on world load, and multiplayer receives the gamemode
payload on join.

`allow-flight=true` is required in `server.properties`. The server kicks any player who
has not moved downward for 80 ticks with *"was kicked for floating too long!"*, which
makes flight impossible otherwise.

### The creative menu

The real singleplayer creative screen — tabs, search, scrolling — now opens in
multiplayer, and the items taken from it are real and server-authoritative.

The screen's 45 item slots are a client-side **view**: which item sits in which slot
depends on the selected tab and scroll offset. There is no matching window on the server,
so a window-click packet would be meaningless to it.

Rather than synchronising that view state — where any drift means clicking one item and
receiving another — the client resolves the click locally against the real
`CreativeContainer` and sends the resulting inventory to the server, which is the approach
vanilla uses for creative. The whole inventory is sent rather than the slot believed to
have changed, because shift-clicks and stack merges can touch several slots at once.

The server accepts this **only while it considers the player to be in creative**:

```java
if (this.playerEntity.properties.gamemode != 1) {
   logger.warning("Ignoring creative inventory payload from " + username + " (not in creative)");
   return;
}
```

Without that check it would be a free item spawner for anyone able to craft a packet. The
payload length is validated against the real inventory size as well.

### Inventory updates were routed to the wrong container

The defect behind both the duplication reports and the `IndexOutOfBoundsException` above.

`ClientHandler.handleSetSlot` routed window-0 slot updates to `player.container` **only
for slots 36–45**, a special case that adds the pickup animation. Every other window-0
slot fell through to a branch targeting `openContainer` when the window ids match — and
`CreativeContainer`'s id is also 0.

With the creative screen open, the server's inventory updates were therefore written into
the creative container, which has an entirely different slot layout. Creative container
slots 45–53 are backed by the player's hotbar, so a server update for container slot 47
landed in inventory slot 2 — an item appearing where it should not, which reads as
duplication. When the index ran past the end of the creative slot list, the game loop
crashed.

**Fixed:** all `windowId == 0` slot updates now go to `player.container`, since window 0
is by definition the player's own inventory and real containers receive non-zero ids from
the server. The 36–45 animation is preserved as an inner case. Bounds checks were added to
both branches so a stale or mismatched index drops the update instead of killing the game
loop.

This was latent rather than new — before this release no client-side container had ever
shadowed window 0 in multiplayer.

### Hotbar placement was dropping items

`CreativeContainer.windowClick` is a dispatcher: the inventory tab delegates to the
player's own container, hotbar slots (index ≥ 45) go through `Container.windowClick` which
actually places the item, and everything else goes to `clickSlot`, which is display-slot
logic that only moves items to the cursor. An early implementation called `clickSlot`
directly and bypassed that dispatch, so clicking a hotbar slot emptied the cursor without
placing anything.

**Fixed:** the dispatcher is used, which is the same call singleplayer makes.

---

## Sprinting

Sprinting did nothing on a dedicated server. Two independent defects.

### The client never entered the sprint state

```java
if (properties.getHunger() && foodStats.getHunger() >= 6.0F) {
   return true;
} else {
   return properties.gamemode == 1 ? true : getFlag(4);
}
```

The `else` branch conflates two different situations: *hunger is enabled but the player is
too hungry* (should block) and *the hunger system is disabled entirely* (should not). With
hunger off it required `getFlag(4)`, a flag bit nothing in the codebase ever sets, so
`canSprint()` returned false permanently.

That always applies on a server. `PlayerProperties.hunger` defaults to `false`, and the
only caller of `Player.setProperties(world)` is gated on `world.newlyGenerated`, which is
true only for a freshly created singleplayer world. Every player on a multiplayer world
therefore had hunger disabled and could not sprint.

Sprinting with hunger off is clearly intended — `HeadsUpDisplay` draws a stamina bar under
exactly the condition `getSprint() && !getHunger()`.

**Fixed:** with hunger enabled the food requirement is unchanged; with hunger disabled the
check uses stamina, matching what the HUD displays.

### The server ignored the sprint packets

`NetServerHandler.handleEntityAction` handled only action state 3 (waking from a bed). The
client sends 4 to start sprinting and 5 to stop, and both were discarded, so the
server-side player was never sprinting — which matters because the speed bonus applies to
the server entity, and other players never saw the state.

**Fixed:** states 4 and 5 call `setSprinting`, which writes flag 3 on the entity's
`DataSyncer` and therefore propagates to every other client through the normal metadata
sync.

---

## Miscellaneous

- Version strings updated to **v1.0.6** across the window title, title screen, in-game
  HUD overlay, changelog heading, crash-report header, world-save version tag, and the
  server startup log. `PROTOCOL_VERSION` is deliberately unchanged at `20260630`, since it
  governs client/server compatibility.
- `ChatOverlay.chatHistory` grows without bound. Its sibling `activeChatMessages` is
  correctly capped at 100. Only a few KB, listed for completeness.
- `Renderer.clearDisplayLists()` flips its buffer to limit 0 before the `put` loop and
  therefore always throws `BufferOverflowException` and deletes nothing. It is only called
  during shutdown inside a `try`/`catch` that swallows the exception.

---

## Upgrade notes

**Client** — import the new instance zip, or replace the jar via the instance's version
tab. Java 8 is still required; Java 21 and above break LWJGL 2.9.

**Server** — replace the jar. Java 8 works; the server does not use LWJGL and also runs on
modern Java.

Recommended `server.properties` for creative play with account verification:

```properties
online-mode=true       # requires the v1.0.6 client and a genuine Java account for everyone
white-list=true        # strongly recommended, see the security note above
allow-flight=true      # required, or flying players are kicked after 4 seconds
```

`allow-flight=true` disables the anti-cheat's flight check for everyone, so a modified
client could fly in survival. On a whitelisted server this is not a practical concern.

---

## Verification

- Client and server protocol compared packet by packet: 68 shared ids, byte-identical wire
  format on both sides.
- Chunk compression measured before and after: 300/300 incompressible payloads truncated
  before, 0/300 after.
- Auth endpoints probed directly: `hasJoined` with an unknown nonce returns 204, `join`
  with an invalid token returns 403.
- Auth enforcement tested live: an unauthenticated client is rejected with
  `Failed to verify <name>: not authenticated with Mojang`; the same build with
  `online-mode=false` still logs in.
- Sprint propagation tested with two clients, one observing the other's entity metadata:
  the sprint flag never appears on the old build and sets/clears correctly on the new one.
- `canSprint()` exercised directly across five scenarios: only the hunger-disabled case
  changes, from `false` to `true`. Starving still blocks sprinting; creative still works.
- Movement speed check tested with single jumps of a known distance: 12 blocks was kicked
  before and is accepted now; 25, 200 and 300 blocks are still kicked.
- `/gamemode` verified end to end: the gamemode is pushed on join, on switching to
  creative, and on switching back.
- Creative inventory verified end to end: a survival player's payload is rejected and
  logged, a non-operator cannot switch to creative, an operator in creative is accepted,
  and the item is still present after a full reconnect — proving it was applied
  server-side and written to NBT.
- Hotbar placement tested against the real container classes before and after the
  dispatcher fix.
- Four concurrent clients streamed chunks continuously with no errors, kicks or
  server-side exceptions.
- Every rebuilt class compared against the original with `javap`; only intended
  differences appear. Both jars were produced by updating a copy of the original archive,
  so manifests and untouched entries are preserved byte for byte.

---

## Known issues and still open

**Not empirically verified.** The two memory-leak fixes rest on static analysis. The
defect is mechanical — a cache with a `put`, no `remove`, and no other references — but a
live before/after measurement was not possible: a synthetic client cannot travel far
enough to generate chunks, because the server applies collision and a movement cap. The
same applies to the health/food packet change, whose code path requires a client to
complete a movement handshake.

**Still leaking:**

- `StructureList.featureList` grows without bound and performs a linear separation scan up
  to 84 times per generated chunk, so worldgen cost rises monotonically for the whole
  session. This is the most likely cause of progressive framerate decay, independent of
  memory. A spatial hash keyed on `centerX/centerZ >> separationBits` would fix it.
- `LegacyStructure.coordMap` retains mineshaft, stronghold and fortress starts
  permanently, iterating all of them on every chunk generation.

Both touch worldgen and were deliberately deferred.

**Unaudited.** The Starlight lighting engine (`ca.spottedleaf.starlight.StarlightEngine`)
ships as a class file with no source. It holds a `chunkCache` and a `destroyCaches()` that
must be called. If memory still climbs, this is the next place to look — run with
`-XX:+HeapDumpOnOutOfMemoryError`.

**Unresolved.** A crash was reported after travelling Aether → Void in creative, preceded
by heavy lag. The log ended without an exception and left a stale lock file, indicating an
out-of-memory condition or a native fault rather than a caught Java error. None of the
chunk changes are reachable on an infinite world, and `World.dropOldChunks()` was checked
for an infinite-loop condition and is safe. Not reproduced since.

**Pre-existing, not fixed.** `Entity.setCanSprint` writes flag 4 while `Entity.isEating`
reads flag 4 — the same bit with two meanings.

**Not implemented.** Protocol encryption. See the security note under account
verification.

---

## Full change footprint

| | classes changed | added | untouched |
|---|---|---|---|
| Client | 23 | 1 | 7,767 |
| Server | 9 | 3 | 4,935 |

<details>
<summary>Client classes modified</summary>

```
net/minecraft/SharedConstants
net/minecraft/game/entity/player/Player
net/minecraft/game/world/chunk/ChunkProvider
net/minecraft/client/Minecraft
net/minecraft/client/ErrorDialog
net/minecraft/client/network/ClientHandler
net/minecraft/client/network/player/MultiplayerMode
net/minecraft/client/player/PlayerController
net/minecraft/client/gui/overlay/HeadsUpDisplay
net/minecraft/client/gui/screens/GuiScreen
net/minecraft/client/gui/screens/container/inventory/CreativeScreen
net/minecraft/client/gui/screens/title/TitleScreen
net/minecraft/client/gui/screens/title/ChangelogScreen
net/minecraft/client/gui/screens/multiplayer/{ChatScreen, SleepScreen, AddServerScreen,
    ConnectingScreen, DirectConnectScreen, ServerSelectScreen}
net/minecraft/client/gui/screens/worldselect/{WorldSelectScreen, WorldOptionsScreen,
    WorldRenamingScreen, BiomeToggleScreen}
```

</details>

<details>
<summary>Server classes modified</summary>

```
net/minecraft/game/entity/player/Player
net/minecraft/game/world/chunk/ChunkProvider
net/minecraft/network/packet/UpdateChunkPacket
net/minecraft/server/MinecraftServer
net/minecraft/server/network/NetLoginHandler
net/minecraft/server/network/NetServerHandler
net/minecraft/server/player/EntityPlayerMP
net/minecraft/server/commands/ServerCommandFactory
net/minecraft/server/commands/CommandGamemode          (new)
```

</details>
