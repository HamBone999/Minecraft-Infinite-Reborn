package landclaim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All claims, and the pending golden-shovel corner selections.
 *
 * Deliberately dependency-free: one tab-separated line per claim. No JSON library,
 * because the only one in the jar (org.json) has an unresolved licence question --
 * see NOTICE.md. The file is human-editable, which also makes it easy to fix by hand.
 */
public final class ClaimStore {

   private static final List<Claim> CLAIMS = new ArrayList<Claim>();
   private static final Map<String, int[]> PENDING = new HashMap<String, int[]>();
   private static File file;

   private ClaimStore() { }

   public static synchronized void load(File dataFile) {
      file = dataFile;
      CLAIMS.clear();
      if (!file.exists()) return;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(file));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            Claim c = Claim.deserialize(line);
            if (c != null) CLAIMS.add(c);
         }
         System.out.println("[landclaim] loaded " + CLAIMS.size() + " claims");
      } catch (IOException e) {
         System.out.println("[landclaim] could not read " + file + ": " + e);
      } finally {
         if (r != null) try { r.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void save() {
      if (file == null) return;
      BufferedWriter w = null;
      try {
         w = new BufferedWriter(new FileWriter(file));
         w.write("# owner\tx1\tz1\tx2\tz2\ttrusted,comma,separated");
         w.newLine();
         for (int i = 0; i < CLAIMS.size(); i++) {
            w.write(CLAIMS.get(i).serialize());
            w.newLine();
         }
      } catch (IOException e) {
         System.out.println("[landclaim] could not write " + file + ": " + e);
      } finally {
         if (w != null) try { w.close(); } catch (IOException ignored) { }
      }
   }

   /** The claim covering this column, or null. */
   public static synchronized Claim at(int x, int z) {
      for (int i = 0; i < CLAIMS.size(); i++) {
         Claim c = CLAIMS.get(i);
         if (c.contains(x, z)) return c;
      }
      return null;
   }

   public static synchronized List<Claim> ownedBy(String player) {
      String p = player.toLowerCase();
      List<Claim> out = new ArrayList<Claim>();
      for (int i = 0; i < CLAIMS.size(); i++) {
         if (CLAIMS.get(i).owner.equals(p)) out.add(CLAIMS.get(i));
      }
      return out;
   }

   /** Returns null on success, or a reason string. */
   public static synchronized String add(Claim c) {
      for (int i = 0; i < CLAIMS.size(); i++) {
         if (CLAIMS.get(i).overlaps(c)) {
            Claim o = CLAIMS.get(i);
            return "that overlaps " + o.owner + "'s claim at " + o.x1 + "," + o.z1;
         }
      }
      CLAIMS.add(c);
      save();
      return null;
   }

   public static synchronized boolean remove(Claim c) {
      boolean removed = CLAIMS.remove(c);
      if (removed) save();
      return removed;
   }

   // ---- pending golden-shovel selection ----

   public static synchronized void setCorner1(String player, int x, int z) {
      PENDING.put(player.toLowerCase(), new int[] { x, z });
   }

   public static synchronized int[] getCorner1(String player) {
      return PENDING.get(player.toLowerCase());
   }

   public static synchronized void clearCorner(String player) {
      PENDING.remove(player.toLowerCase());
   }
}
