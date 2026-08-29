package anticheat;

import java.util.*;

/** Recent detections, per player, in memory. Viewable with /ac. */
public final class Flags {

   public static final class Flag {
      public final long when; public final String player, check, detail;
      Flag(String p, String c, String d) { this.when = System.currentTimeMillis(); this.player = p; this.check = c; this.detail = d; }
   }

   private static final LinkedList<Flag> RECENT = new LinkedList<Flag>();
   private static final Map<String, Integer> STREAK = new HashMap<String, Integer>();
   private static final int KEEP = 200;

   private Flags() { }

   /** Records a detection. Returns the player's consecutive-flag count for this check. */
   public static synchronized int raise(String player, String check, String detail) {
      Flag f = new Flag(player.toLowerCase(), check, detail);
      RECENT.addLast(f);
      while (RECENT.size() > KEEP) RECENT.removeFirst();
      String k = player.toLowerCase() + "/" + check;
      Integer n = STREAK.get(k);
      int c = (n == null ? 0 : n.intValue()) + 1;
      STREAK.put(k, Integer.valueOf(c));
      System.out.println("[anticheat] " + check + " " + player + " -- " + detail + " (x" + c + ")");
      return c;
   }

   public static synchronized void clear(String player, String check) {
      STREAK.remove(player.toLowerCase() + "/" + check);
   }

   public static synchronized List<Flag> recent(int n) {
      List<Flag> out = new ArrayList<Flag>(RECENT);
      Collections.reverse(out);
      return out.size() > n ? out.subList(0, n) : out;
   }

   public static synchronized int total() { return RECENT.size(); }
}
