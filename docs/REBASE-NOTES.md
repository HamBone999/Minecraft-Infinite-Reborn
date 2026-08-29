# Dropping the fork

My fork is retired. This build is upstream `Minecraft Infinite 1.0-210826` with only the fixes
upstream is actually missing carried across.

Most of the fork's server side work turned out to be **already fixed upstream**, and in two
cases fixed better than mine. That's the good outcome. Less to carry.

## Already fixed upstream, dropped from the fork

| My fix | Upstream |
| --- | --- |
| Teleport packet field swap | Fixed the same way |
| Float kick ground test | Fixed, and **better**, see below |
| Creative instant block break | Fixed |
| GL viewport texture leak | Fixed, `onClose()` deletes it |
| `handleSetSlot` window routing | Fixed, with better animation handling |
| Modern Mojang auth | Already ported |
| Sprint on multiplayer | Already handled |
| `/gamemode` command | Real `GamemodeCommand` with a typed `GamemodeArgument` |
| Creative menu / inventory sync | Real `CreativeSlotPacket`, not a custom payload channel |
| Creative flight, gamemode plumbing | Whole gamemode system rewritten |

Two worth calling out as flat out better than what I had.

**The float check.** Upstream fixed the `(d15 > -0.5 || d15 < 0.5)` tautology *and* added the
ground bounding box test *and* used real vertical movement (`posY - d3`) instead of the zeroed
variable *and* reset the airborne counter on descent. I'd only added the ground test.

**Gamemode architecture.** The four subclass hierarchy (`CreativeMode`, `SurvivalMode`, …) is
gone, replaced by one `GameMode` class reading `player.properties.isCreative()`. That's server
aware by construction, which is exactly what I was hand plumbing with `IP|GM` and `IP|CI`
custom payloads. It also fixed the reach mismatch: `blockReach()` now returns 5.0 creative /
4.0 survival against the server's own limit, where my client asked for 10.0 and tripped the
server's check.

## Carried across, upstream still has these

### Shared, the class is byte identical in both jars

**1. `ChunkProvider.terrainMap` was unbounded.** Upstream made it a real cache (mine was write
only and never read) but nothing evicts, so it grows for every column ever touched. Now a 256
entry LRU using `Long2ObjectLinkedOpenHashMap`, which upstream already ships. A miss only costs
a regeneration.

**2. `unloadOldChunks()` wedged on an orphan.** When a queued hash had no chunk in the map it
did `return false`, leaving that hash in `droppedChunks` forever *and* abandoning the rest of
the pass. Now it drops the orphan and continues.

**3. Chunk compression could truncate.** The deflate output buffer was sized to the *input*
length. Deflate output is usually smaller but it isn't guaranteed to be. Incompressible chunk
data expands slightly, `deflate()` filled the buffer, stopped, and the packet shipped truncated
data the client couldn't inflate. That's your "Bad compressed data format". Now the buffer grows
until the deflater says it's finished.

### Server only

**4. The speed check was a flat constant.** `d19 > 100.0`. Ten blocks between packets with no
reference to elapsed time, so a lag spike, GC pause or slow chunk load batches up movement and
kicks a legitimate player. Now proportional to real elapsed time, clamped at both ends: at
least one tick so a packet burst can't buy distance, at most a second so a client going quiet
can't bank budget and teleport on resume. Creative and spectator get a higher limit. The kick
message names the actual numbers now.

**5. Break confirmation wasn't sent in creative.** The confirming `UpdateBlockPacket` only went
out when the block also dropped something, and in creative it never drops. The client's pending
update then sat there until its 80 tick revert timer fired and flickered the block back. Now
it's sent whenever the block actually came out.

### Client only

**6. SRV record support.** Upstream resolves A records only, so a domain pointing at a tunnel
gets ignored. Added `net.minecraft.network.SrvResolver`, wired into both connect paths
(`ClientHandler`, `PingHandler`), using the JRE's own DNS provider so there's no new dependency.
Only attempted when no explicit port was typed. IP literals, `localhost` and single label names
skip it without a DNS round trip. Every failure falls back to plain resolution.

**7. Multiplayer splash text.** Yellow **"Multiplayer is WIP"**, centred. Upstream already said
"Multiplayer is a heavy work-in-progress" in yellow so this is wording only, but it was pinned
at `width/2 - 100` which was tuned for the longer string, so a shorter one sat left of centre.

## Desync fixes

I re-audited all twelve findings from the desync audit against upstream first, because several
were fork specific or killed by the rewrite. Two were already fixed:

- Number keys 1-9 in a container now route through `gamemode.containerSwapWithHotbar(...)`,
  which sends a packet. That was the duplication bug. Gone.
- `NetworkWorld` ghost entities. The retry loop checks `isAlive()` now and removal clears
  `entityMemory`.

Five were still there. Fixed here.

**8. The entity tracker compared absolute coordinates instead of deltas.**

```java
// was: i2/i3/i4 are ABSOLUTE scaled positions
boolean z11 = Math.abs(i2) >= 8 || Math.abs(i3) >= 8 || Math.abs(i4) >= 8;
// now: i7/i8/i9 are the deltas
boolean z11 = Math.abs(i7) >= 8 || Math.abs(i8) >= 8 || Math.abs(i9) >= 8;
```

"Is |posY| more than 0.25 blocks from zero" is true for every living entity, so this was
permanently true. The look-only branch was dead code and every tracked entity emitted a relative
move packet on its interval forever, even standing perfectly still.

> [!WARNING]
> Fixing the comparison on its own would have introduced a worse bug. The encoded position
> commit was gated on the same flag, and the forced teleport branch never updated it. With
> `z11` always true that never mattered, but once it can be false a teleport advances the
> client while leaving `encodedPos` stale, and every later delta gets measured from the wrong
> base and drifts without bound. So the commit now follows whether a position packet actually
> went out, teleports included.

**9. Riding sent rotation in the wrong branch.** `PlayerPositionPacket` carries no rotation and
`PlayerTeleportPacket` does, and the two were swapped. Turning in a boat or minecart sent the
packet *without* yaw and pitch, so the server's copy of your heading froze until you stopped
turning. The un-mounted path directly below it picks correctly.

**10. `handleRideEntity` dismounted riders whose vehicle hadn't spawned yet.** `mountEntity(null)`
is the dismount signal and spawn packets arrive in `HashSet` order, so a mounted rider could
arrive before its boat. `getEntityByID` returned null and observers saw the rider standing next
to the vehicle until the tracker's 60 tick resend fixed it. The server encodes a real dismount
as `vehicleEntityId == -1`, so that's what it tests now.

**11. The client threw away every head rotation packet.** `handleEntityRotation` had no override
so it hit the base no-op. Mob and remote player heads stayed pointed wherever the last move-look
packet left them while the server spent bandwidth on the channel. Implemented against
`Mob.headFacing`.

**12. Dig rejected by the reach check answered with nothing.** Every other rejection in that
method sends a block update, this one returned bare, so the client kept its predicted change
until `NetworkWorld`'s 80 tick revert timer put the block back four seconds later.

### Measured

Two protocol clients against a server with mob spawning off, so the only tracked entity is one
other player.

| | before | after |
| --- | ---: | ---: |
| Move packets in 14s, player **motionless** | **1034** | **7** |
| Move packets in 14s, player **walking** | — | **160** (~11/s, matching the tracker interval) |

First row is the bug. Second row is the check that movement still actually propagates.

## Not carried across, and why

**The two null player crash guards in `Minecraft.java`.** Both bugs are still live upstream:

- `Minecraft.java:671`, `this.currentScreen instanceof SleepScreen && !this.player.isPlayerSleeping()`
  with no null check on `player`, reachable while the world is being torn down.
- The hotbar scroll handler dereferences `this.player.inventory` guarded only on
  `currentScreen == null`.

They're not in this build because **`Minecraft.java` doesn't survive a decompile/recompile round
trip**. The test that settled it: compiling the *unmodified* decompiled source drops
`Minecraft$2`, an anonymous class the real `Minecraft.class` genuinely references. Shipping that
breaks the client far worse than the crashes it fixes.

Every other patched class was round trip tested the same way and reproduces its original inner
class set exactly.

Fixing these two properly means editing bytecode instead of going through source. Worth doing,
as its own piece of work with its own verification.

## Verification

- **Round trip test on every patched class.** Compile the unmodified decompiled source, confirm
  it reproduces the original set of class files. All pass except `Minecraft.java`, which is why
  that one got dropped.
- **`ChunkProvider` and `UpdateChunkPacket` compiled against each jar separately produce byte
  identical output**, confirming they really are the same shared class.
- **`javap` signature comparison.** No signature changed except the intended additions:
  `SrvResolver`, `lastMoveTime`, `TERRAIN_CACHE_SIZE`, and the `terrainMap` type change.
- **Server boots clean.** `Starting minecraft server version Minecraft Infinite 1.0-210826` →
  `Done (15361251116ns)!`
- **SRV resolver** exercised against the patched client jar, and verified live against a real
  record earlier.

## Change footprint

| | added | changed | untouched |
| --- | ---: | ---: | ---: |
| Client | 1 | 13 | 7,785 |
| Server | 0 | 5 | 4,944 |

## Still open

- **The two `Minecraft.java` crashes above.** Blocked on the round trip failure, not on
  difficulty.
- **`Container.rollbackChanges` and `deleteBackup` are empty method bodies.** The server does
  send `TransactionPacket(..., false)` on a rejected click and the client does call
  `rollbackChanges`, but the method does nothing, so a mispredicted inventory state stands until
  the next full window resync. Nothing takes a snapshot anywhere, so this is a feature to build,
  not a line to fix.
- **`handleTransaction` looks up `idMap` by the player's *current* container id** instead of the
  id in the packet, so an acknowledgement arriving after the player opened a different container
  is dropped and `canUseMP` can stay false, which silently discards every later inventory click.
  Left alone on purpose: **vanilla has the identical structure**, so changing it is a behaviour
  change against a reference implementation rather than a fix to a bug I introduced. Wants its
  own testing.
- **Minecarts are simulated on both sides, boats aren't**, and the minecart interpolation budget
  expires on exactly the tick the next packet is due, so jitter drops the client into local
  physics that then gets snapped back. Real, but it's a physics ownership change and too big to
  bundle in here.
