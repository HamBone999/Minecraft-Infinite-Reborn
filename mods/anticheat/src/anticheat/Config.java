package anticheat;

import java.io.*;
import java.util.Properties;

/**
 * Every check ships in LOG-ONLY mode. Nothing kicks anyone until you turn it on.
 *
 * That is deliberate. This build has documented desync bugs -- its own README lists the
 * anti-speed check as previously kicking legitimate players on a GC pause, and we see
 * "moved wrongly" warnings in normal play. A movement check tuned blind would kick real
 * players. Watch the log first, then enable enforcement one check at a time.
 */
public final class Config {

   public static boolean movementEnabled = true,  movementKick = false;
   public static boolean reachEnabled    = true,  reachKick    = false;
   public static boolean spamEnabled     = true,  spamKick     = false;
   public static boolean xrayEnabled     = true,  xrayKick     = false;
   public static boolean flyEnabled      = true,  flyKick      = false;
   public static boolean rateEnabled     = true,  rateKick     = false;

   public static double maxSpeed  = 1.2;   // blocks per movement packet, horizontal
   public static int    speedStreak = 3;   // consecutive over-limit packets before it counts
   public static double maxReach  = 7.0;   // blocks from eye to targeted block
   public static int    maxChat   = 8;     // messages per 10 seconds
   public static int    flagsToKick = 8;   // consecutive flags before a kick, when kicking is on
   public static boolean exemptStaff = true;  // admin/owner/op are not checked at all

   public static int    xraySample  = 300;  // blocks broken before the ratio means anything
   public static double xrayRatio   = 0.04; // valuable-ore share that trips a flag
   public static int    maxBreaks   = 16;   // blocks broken per second
   public static int    maxPlaces   = 10;   // blocks placed per second
   public static int    maxAttacks  = 12;   // attacks per second
   public static int    maxPackets  = 30;   // movement packets per second
   public static int    flyPackets  = 40;   // consecutive airborne packets with no net descent
   public static double minY = -64.0, maxY = 512.0;

   private Config() { }

   public static void load(File f) {
      if (!f.exists()) write(f);   // seed defaults, then read them back so the log is always accurate
      Properties p = new Properties();
      InputStream in = null;
      try { if (f.exists()) { in = new FileInputStream(f); p.load(in); } }
      catch (IOException e) { System.out.println("[anticheat] could not read " + f + ": " + e); }
      finally { if (in != null) try { in.close(); } catch (IOException ignored) { } }

      movementEnabled = bool(p, "movement.detect", movementEnabled);
      movementKick    = bool(p, "movement.kick",   movementKick);
      reachEnabled    = bool(p, "reach.detect",    reachEnabled);
      reachKick       = bool(p, "reach.kick",      reachKick);
      spamEnabled     = bool(p, "spam.detect",     spamEnabled);
      spamKick        = bool(p, "spam.kick",       spamKick);
      xrayEnabled     = bool(p, "xray.detect",     xrayEnabled);
      xrayKick        = bool(p, "xray.kick",       xrayKick);
      flyEnabled      = bool(p, "fly.detect",      flyEnabled);
      flyKick         = bool(p, "fly.kick",        flyKick);
      rateEnabled     = bool(p, "rate.detect",     rateEnabled);
      rateKick        = bool(p, "rate.kick",       rateKick);
      xraySample = (int) dbl(p, "xray.sample-size",  xraySample);
      xrayRatio  =       dbl(p, "xray.ore-ratio",    xrayRatio);
      maxBreaks  = (int) dbl(p, "rate.max-breaks-per-second",  maxBreaks);
      maxPlaces  = (int) dbl(p, "rate.max-places-per-second",  maxPlaces);
      maxAttacks = (int) dbl(p, "rate.max-attacks-per-second", maxAttacks);
      maxPackets = (int) dbl(p, "rate.max-move-packets-per-second", maxPackets);
      flyPackets = (int) dbl(p, "fly.airborne-packets", flyPackets);
      maxSpeed    = dbl(p, "movement.max-blocks-per-packet", maxSpeed);
      speedStreak = (int) dbl(p, "movement.consecutive-packets", speedStreak);
      maxReach    = dbl(p, "reach.max-blocks",     maxReach);
      maxChat     = (int) dbl(p, "spam.max-per-10s", maxChat);
      flagsToKick = (int) dbl(p, "flags-before-kick", flagsToKick);
      exemptStaff = bool(p, "exempt-staff", exemptStaff);

      System.out.println("[anticheat] movement=" + mode(movementEnabled, movementKick)
            + " reach=" + mode(reachEnabled, reachKick)
            + " spam=" + mode(spamEnabled, spamKick)
            + " xray=" + mode(xrayEnabled, xrayKick)
            + " fly=" + mode(flyEnabled, flyKick)
            + " rate=" + mode(rateEnabled, rateKick)
            + (exemptStaff ? "  (staff exempt)" : "  (staff ALSO checked)"));
   }

   private static String mode(boolean on, boolean kick) {
      return !on ? "off" : (kick ? "KICK" : "log-only");
   }

   private static boolean bool(Properties p, String k, boolean def) {
      String v = p.getProperty(k); return v == null ? def : Boolean.parseBoolean(v.trim());
   }
   private static double dbl(Properties p, String k, double def) {
      String v = p.getProperty(k);
      if (v == null) return def;
      try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return def; }
   }

   private static void write(File f) {
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(f));
         w.println("# Anticheat. Everything starts in log-only mode ON PURPOSE.");
         w.println("# Watch /ac and the console for a few days of normal play before enabling any kick.");
         w.println("# This build has known desync bugs, so movement is the most likely to false-positive.");
         w.println();
         w.println("movement.detect=true");
         w.println("movement.kick=false");
         w.println("movement.max-blocks-per-packet=1.2");
         w.println("# One fast packet is not a speed hack. Mob knockback, a teleporter, standing up");
         w.println("# out of a crawl and a plain lag spike all move you a long way in a single packet.");
         w.println("# A real speed hack sustains it, so require this many over-limit packets in a row.");
         w.println("movement.consecutive-packets=3");
         w.println();
         w.println("reach.detect=true");
         w.println("reach.kick=false");
         w.println("reach.max-blocks=7.0");
         w.println();
         w.println("spam.detect=true");
         w.println("spam.kick=false");
         w.println("spam.max-per-10s=8");
         w.println();
         w.println("# X-ray is STATISTICAL. It measures the share of valuable ore among the blocks");
         w.println("# a player breaks. A high ratio is suspicious, not proof -- a lucky vein or a");
         w.println("# careful branch-miner can trip it. Never auto-kick on this one.");
         w.println("xray.detect=true");
         w.println("xray.kick=false");
         w.println("xray.sample-size=300");
         w.println("xray.ore-ratio=0.04");
         w.println();
         w.println("# Hovering or rising while airborne. Boats, ladders, water and lag can all");
         w.println("# look like this on a build with known desync -- watch before enabling.");
         w.println("fly.detect=true");
         w.println("fly.kick=false");
         w.println("fly.airborne-packets=40");
         w.println();
         w.println("# Action rates: nuker, scaffold, killaura, timer.");
         w.println("rate.detect=true");
         w.println("rate.kick=false");
         w.println("# Sand, gravel and leaves break almost instantly, and mining them normally");
         w.println("# reached 11 per second in testing. A nuker breaks far more than this.");
         w.println("rate.max-breaks-per-second=16");
         w.println("rate.max-places-per-second=10");
         w.println("rate.max-attacks-per-second=12");
         w.println("rate.max-move-packets-per-second=30");
         w.println();
         w.println("# Admin, owner and op are not checked at all. Staff fly, teleport and build");
         w.println("# fast as a matter of course, so they would trip these constantly.");
         w.println("# Set false if you want to test the checks on yourself.");
         w.println("exempt-staff=true");
         w.println();
         w.println("# consecutive flags before a kick fires, for checks that have kick=true");
         w.println("flags-before-kick=8");
      } catch (IOException e) {
         System.out.println("[anticheat] could not write " + f + ": " + e);
      } finally { if (w != null) w.close(); }
   }
}
