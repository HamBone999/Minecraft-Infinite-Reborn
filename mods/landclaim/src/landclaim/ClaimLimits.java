package landclaim;

import java.io.*;
import java.util.List;
import java.util.Properties;

/**
 * How much land a non-op may claim.
 *
 * Budget = startBlocks + (blocksPerHour * hours played), capped at maxBlocks.
 * Ops are unlimited and never consume budget.
 *
 * Defaults are written to world/landclaim.properties on first run so they can be tuned
 * without a rebuild. startBlocks defaults to a 16-chunk radius: 16 chunks is 256 blocks,
 * so a 256-block radius is a 512 x 512 square = 262144 blocks.
 */
public final class ClaimLimits {

   private static long startBlocks   = 262144L;
   private static long blocksPerHour = 16384L;
   private static long maxBlocks     = 1048576L;

   private ClaimLimits() { }

   public static synchronized void load(File f) {
      Properties p = new Properties();
      if (f.exists()) {
         InputStream in = null;
         try { in = new FileInputStream(f); p.load(in); }
         catch (IOException e) { System.out.println("[landclaim] could not read " + f + ": " + e); }
         finally { if (in != null) try { in.close(); } catch (IOException ignored) { } }
         startBlocks   = parse(p, "start-blocks",    startBlocks);
         blocksPerHour = parse(p, "blocks-per-hour", blocksPerHour);
         maxBlocks     = parse(p, "max-blocks",      maxBlocks);
      } else {
         write(f);
      }
      System.out.println("[landclaim] limits: start=" + startBlocks
            + " perHour=" + blocksPerHour + " max=" + maxBlocks + " (ops unlimited)");
   }

   private static long parse(Properties p, String key, long def) {
      String v = p.getProperty(key);
      if (v == null) return def;
      try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
   }

   private static void write(File f) {
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(f));
         w.println("# Land claim limits. Ops are unlimited and ignore all of this.");
         w.println("#");
         w.println("# budget = start-blocks + (blocks-per-hour * hours played), capped at max-blocks");
         w.println("#");
         w.println("# 1 chunk = 16x16 = 256 blocks.");
         w.println("# 262144 = a 512x512 square, i.e. a 16-chunk (256 block) radius.");
         w.println("start-blocks=262144");
         w.println("#");
         w.println("# 16384 = 64 chunks per hour played.");
         w.println("blocks-per-hour=16384");
         w.println("#");
         w.println("# 1048576 = 1024x1024. Nobody accrues past this.");
         w.println("max-blocks=1048576");
      } catch (IOException e) {
         System.out.println("[landclaim] could not write " + f + ": " + e);
      } finally {
         if (w != null) w.close();
      }
   }

   public static synchronized long budgetFor(String player) {
      long hours = PlaytimeStore.minutes(player) / 60L;
      long b = startBlocks + blocksPerHour * hours;
      return b > maxBlocks ? maxBlocks : b;
   }

   public static long usedBy(String player) {
      List<Claim> mine = ClaimStore.ownedBy(player);
      long used = 0L;
      for (int i = 0; i < mine.size(); i++) used += mine.get(i).area();
      return used;
   }

   public static long remaining(String player) {
      long r = budgetFor(player) - usedBy(player);
      return r < 0 ? 0 : r;
   }

   public static synchronized long startBlocks()   { return startBlocks; }
   public static synchronized long blocksPerHour() { return blocksPerHour; }
}
