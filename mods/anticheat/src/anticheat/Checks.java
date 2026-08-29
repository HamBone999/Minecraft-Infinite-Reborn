package anticheat;

import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/** The actual detection logic. Returns true when the action should be blocked. */
public final class Checks {

   private static final Map<String, double[]> LAST_POS = new HashMap<String, double[]>();
   private static final Map<String, LinkedList<Long>> CHAT = new HashMap<String, LinkedList<Long>>();
   private static final Map<String, LinkedList<Long>> BREAKS = new HashMap<String, LinkedList<Long>>();
   private static final Map<String, LinkedList<Long>> PLACES = new HashMap<String, LinkedList<Long>>();
   private static final Map<String, LinkedList<Long>> ATTACKS = new HashMap<String, LinkedList<Long>>();
   private static final Map<String, LinkedList<Long>> MOVES = new HashMap<String, LinkedList<Long>>();
   private static final Map<String, int[]> MINED = new HashMap<String, int[]>();     // {total, valuable}
   private static final Map<String, int[]> LASTBLOCK = new HashMap<String, int[]>(); // dedupe {x,y,z}
   private static final Map<String, Long> LASTBLOCK_AT = new HashMap<String, Long>();
   private static final Map<String, int[]> AIR = new HashMap<String, int[]>();       // {consecutive}
   private static final Map<String, Double> AIR_START_Y = new HashMap<String, Double>();
   private static final Map<String, int[]> FAST = new HashMap<String, int[]>();      // {consecutive over-limit}

   private Checks() { }

   /** Admin, owner and op are skipped entirely -- no bookkeeping, no flags. */
   private static boolean exempt(EntityPlayerMP p) {
      return Config.exemptStaff && Alerts.isStaff(p.mcServer, p.getName());
   }

   private static boolean act(NetServerHandler net, EntityPlayerMP p, String check,
                              String detail, boolean kickEnabled) {
      int streak = Flags.raise(p.getName(), check, detail);
      Alerts.raise(p.mcServer, p.getName() + "  " + check + "  " + detail + "  (x" + streak + ")");
      if (kickEnabled && streak >= Config.flagsToKick) {
         Flags.clear(p.getName(), check);
         net.kickPlayer("Kicked by anticheat: " + check);
         return true;
      }
      return false;
   }

   /** Rolling count of events in the last second. Returns the count after adding this one. */
   private static int rate(Map<String, LinkedList<Long>> m, String key) {
      long now = System.currentTimeMillis();
      LinkedList<Long> q = m.get(key);
      if (q == null) { q = new LinkedList<Long>(); m.put(key, q); }
      q.addLast(Long.valueOf(now));
      while (!q.isEmpty() && now - q.getFirst().longValue() > 1000L) q.removeFirst();
      return q.size();
   }

   /** Horizontal speed. Vertical is ignored: falling and knockback are legitimately fast. */
   public static void movement(NetServerHandler net, EntityPlayerMP p, double x, double y, double z) {
      if (!Config.movementEnabled || exempt(p)) return;
      String k = p.getName().toLowerCase();
      double[] last = LAST_POS.get(k);
      LAST_POS.put(k, new double[] { x, y, z });
      if (last == null) return;
      double dx = x - last[0], dz = z - last[2];
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist > 64.0) { FAST.remove(k); return; }   // teleport or dimension change, not a cheat

      // A single fast packet means nothing on this build. Mob knockback, a teleporter pad,
      // standing up out of a crawl after a relog, and an ordinary lag spike all cover several
      // blocks between two packets -- those were most of what this check used to report. A real
      // speed hack sustains the pace, so require a run of over-limit packets before believing it.
      int[] streak = FAST.get(k);
      if (streak == null) { streak = new int[] { 0 }; FAST.put(k, streak); }
      if (dist > Config.maxSpeed) {
         streak[0]++;
         if (streak[0] >= Config.speedStreak) {
            act(net, p, "speed", String.format("%.2f blocks per packet for %d packets (limit %.2f)",
                dist, streak[0], Config.maxSpeed), Config.movementKick);
         }
      } else {
         streak[0] = 0;
         Flags.clear(p.getName(), "speed");
      }

      if (Config.rateEnabled) {
         int n = rate(MOVES, k);
         if (n > Config.maxPackets) {
            act(net, p, "timer", n + " move packets in 1s (limit " + Config.maxPackets + ")", Config.rateKick);
         }
      }

      if (y < Config.minY || y > Config.maxY) {
         act(net, p, "position", String.format("y=%.1f is outside the world", y), Config.movementKick);
      }
   }

   /**
    * Hovering or rising while airborne.
    *
    * Deliberately generous: only fires after a long run of airborne packets with no net
    * descent. Ladders, water, boats and plain lag all look like short bursts of this.
    */
   public static void airborne(NetServerHandler net, EntityPlayerMP p, double y, boolean onGround) {
      if (!Config.flyEnabled || exempt(p)) return;
      String k = p.getName().toLowerCase();
      if (onGround) {
         AIR.remove(k); AIR_START_Y.remove(k);
         Flags.clear(p.getName(), "fly");
         return;
      }
      int[] c = AIR.get(k);
      if (c == null) { c = new int[] { 0 }; AIR.put(k, c); AIR_START_Y.put(k, Double.valueOf(y)); }
      c[0]++;
      Double startY = AIR_START_Y.get(k);
      if (c[0] < Config.flyPackets || startY == null) return;
      // a real fall loses height; hovering or rising does not
      if (y >= startY.doubleValue() - 1.0) {
         act(net, p, "fly", c[0] + " airborne packets with no descent (y " + String.format("%.1f", y) + ")",
             Config.flyKick);
         c[0] = 0; AIR_START_Y.put(k, Double.valueOf(y));
      } else {
         c[0] = 0; AIR_START_Y.put(k, Double.valueOf(y));
      }
   }

   /** Blocks broken: rate (nuker) and the x-ray ore ratio. */
   public static void broke(NetServerHandler net, EntityPlayerMP p, int bx, int by, int bz, int blockId) {
      if (exempt(p)) return;
      String k = p.getName().toLowerCase();
      long now = System.currentTimeMillis();

      // one dig packet per block: the client sends several per break, so dedupe by position
      int[] lb = LASTBLOCK.get(k);
      Long lat = LASTBLOCK_AT.get(k);
      if (lb != null && lat != null && lb[0] == bx && lb[1] == by && lb[2] == bz
            && now - lat.longValue() < 2000L) return;
      LASTBLOCK.put(k, new int[] { bx, by, bz });
      LASTBLOCK_AT.put(k, Long.valueOf(now));
      if (blockId == 0) return;   // air, nothing was broken

      if (Config.rateEnabled) {
         int n = rate(BREAKS, k);
         if (n > Config.maxBreaks) {
            act(net, p, "nuker", n + " blocks broken in 1s (limit " + Config.maxBreaks + ")", Config.rateKick);
         }
      }

      if (!Config.xrayEnabled) return;
      int[] m = MINED.get(k);
      if (m == null) { m = new int[] { 0, 0 }; MINED.put(k, m); }
      m[0]++;
      if (Ores.isValuable(blockId)) m[1]++;
      if (m[0] < Config.xraySample) return;
      double ratio = (double) m[1] / (double) m[0];
      if (ratio > Config.xrayRatio) {
         act(net, p, "xray", String.format("%d valuable ore in %d blocks (%.1f%%, limit %.1f%%)",
             m[1], m[0], ratio * 100.0, Config.xrayRatio * 100.0), Config.xrayKick);
      }
      m[0] = 0; m[1] = 0;   // start a fresh window either way
   }

   /** Blocks placed: rate (scaffold / fast-place). */
   public static void placed(NetServerHandler net, EntityPlayerMP p) {
      if (!Config.rateEnabled || exempt(p)) return;
      int n = rate(PLACES, p.getName().toLowerCase());
      if (n > Config.maxPlaces) {
         act(net, p, "fastplace", n + " blocks placed in 1s (limit " + Config.maxPlaces + ")", Config.rateKick);
      }
   }

   /** Attack rate (killaura / autoclicker). */
   public static void attacked(NetServerHandler net, EntityPlayerMP p) {
      if (!Config.rateEnabled || exempt(p)) return;
      int n = rate(ATTACKS, p.getName().toLowerCase());
      if (n > Config.maxAttacks) {
         act(net, p, "killaura", n + " attacks in 1s (limit " + Config.maxAttacks + ")", Config.rateKick);
      }
   }

   /** Distance from the player to the block they are breaking or placing. */
   public static boolean reach(NetServerHandler net, EntityPlayerMP p, int bx, int by, int bz) {
      if (!Config.reachEnabled || exempt(p)) return false;
      if (bx == -1 && by == -1 && bz == -1) return false;   // "no block" sentinel
      double dx = (bx + 0.5) - p.posX, dy = (by + 0.5) - (p.posY + 1.6), dz = (bz + 0.5) - p.posZ;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (dist <= Config.maxReach) { Flags.clear(p.getName(), "reach"); return false; }
      return act(net, p, "reach", String.format("%.1f blocks (limit %.1f)", dist, Config.maxReach),
                 Config.reachKick);
   }

   /** Chat and command rate. */
   public static boolean spam(NetServerHandler net, EntityPlayerMP p) {
      if (!Config.spamEnabled || exempt(p)) return false;
      String k = p.getName().toLowerCase();
      long now = System.currentTimeMillis();
      LinkedList<Long> q = CHAT.get(k);
      if (q == null) { q = new LinkedList<Long>(); CHAT.put(k, q); }
      q.addLast(Long.valueOf(now));
      while (!q.isEmpty() && now - q.getFirst().longValue() > 10000L) q.removeFirst();
      if (q.size() <= Config.maxChat) return false;
      return act(net, p, "spam", q.size() + " messages in 10s (limit " + Config.maxChat + ")",
                 Config.spamKick);
   }

   public static void forget(String player) {
      String k = player.toLowerCase();
      LAST_POS.remove(k); CHAT.remove(k); BREAKS.remove(k); PLACES.remove(k);
      ATTACKS.remove(k); MOVES.remove(k); MINED.remove(k); LASTBLOCK.remove(k);
      LASTBLOCK_AT.remove(k); AIR.remove(k); AIR_START_Y.remove(k); FAST.remove(k);
   }
}
