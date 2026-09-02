package worldprotect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every region, the file behind them, and the per-player wand selections. */
public final class RegionStore {

   private static final Map<String, Region> REGIONS = new LinkedHashMap<String, Region>();
   private static final Map<String, int[]> CORNER1 = new HashMap<String, int[]>();
   private static final Map<String, int[]> CORNER2 = new HashMap<String, int[]>();

   /** Operators who have turned their own bypass off, so they can test what players see. */
   private static final Map<String, Boolean> BYPASS_OFF = new HashMap<String, Boolean>();

   private static File file;

   private RegionStore() {
   }

   // ---- lookup ---------------------------------------------------------------

   /**
    * The region governing a point, or null.
    *
    * Highest priority wins; on a tie the smaller region does, so a small carve-out inside a big
    * protected area behaves the way whoever drew it expected without having to think about
    * priorities at all.
    */
   public static synchronized Region at(int dim, int x, int y, int z) {
      Region best = null;
      for (Region r : REGIONS.values()) {
         if (!r.contains(dim, x, y, z)) {
            continue;
         }

         if (best == null || r.priority > best.priority
            || (r.priority == best.priority && r.volume() < best.volume())) {
            best = r;
         }
      }

      return best;
   }

   /** As {@link #at}, ignoring height, for the hooks that only know a column. */
   public static synchronized Region atColumn(int dim, int x, int z) {
      Region best = null;
      for (Region r : REGIONS.values()) {
         if (!r.containsColumn(dim, x, z)) {
            continue;
         }

         if (best == null || r.priority > best.priority
            || (r.priority == best.priority && r.volume() < best.volume())) {
            best = r;
         }
      }

      return best;
   }

   public static synchronized Region get(String name) {
      return REGIONS.get(name.toLowerCase());
   }

   public static synchronized List<Region> all() {
      return new ArrayList<Region>(REGIONS.values());
   }

   public static synchronized String add(Region r) {
      if (REGIONS.containsKey(r.name.toLowerCase())) {
         return "a region called " + r.name + " already exists";
      }

      REGIONS.put(r.name.toLowerCase(), r);
      save();
      return null;
   }

   public static synchronized boolean remove(String name) {
      boolean gone = REGIONS.remove(name.toLowerCase()) != null;
      if (gone) {
         save();
      }

      return gone;
   }

   public static synchronized void touch() {
      save();
   }

   // ---- selections -----------------------------------------------------------

   public static synchronized void setCorner(String player, boolean first, int x, int y, int z) {
      (first ? CORNER1 : CORNER2).put(player.toLowerCase(), new int[] { x, y, z });
   }

   public static synchronized int[] corner(String player, boolean first) {
      return (first ? CORNER1 : CORNER2).get(player.toLowerCase());
   }

   public static synchronized void clearSelection(String player) {
      CORNER1.remove(player.toLowerCase());
      CORNER2.remove(player.toLowerCase());
   }

   // ---- op bypass ------------------------------------------------------------

   public static synchronized boolean bypassing(String player) {
      return !Boolean.TRUE.equals(BYPASS_OFF.get(player.toLowerCase()));
   }

   /** Returns the new state. */
   public static synchronized boolean toggleBypass(String player) {
      String p = player.toLowerCase();
      boolean nowOff = !Boolean.TRUE.equals(BYPASS_OFF.get(p));
      BYPASS_OFF.put(p, Boolean.valueOf(nowOff));
      return !nowOff;
   }

   // ---- persistence ----------------------------------------------------------

   public static synchronized void load(File f) {
      file = f;
      REGIONS.clear();
      if (!f.exists()) {
         return;
      }

      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
               continue;
            }

            String[] p = line.split("\t", -1);
            if (p.length < 10) {
               continue;
            }

            try {
               Region reg = new Region(
                  p[0], Integer.parseInt(p[1]),
                  Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                  Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7])
               );
               reg.priority = Integer.parseInt(p[8]);

               if (p[9].length() > 0) {
                  String[] mem = p[9].split(",");
                  for (int i = 0; i < mem.length; i++) {
                     if (mem[i].trim().length() > 0) {
                        reg.members.add(mem[i].trim().toLowerCase());
                     }
                  }
               }

               if (p.length > 10 && p[10].length() > 0) {
                  String[] fl = p[10].split(";");
                  for (int i = 0; i < fl.length; i++) {
                     int eq = fl[i].indexOf('=');
                     if (eq > 0) {
                        reg.flags.put(fl[i].substring(0, eq), fl[i].substring(eq + 1));
                     }
                  }
               }

               REGIONS.put(reg.name.toLowerCase(), reg);
            } catch (NumberFormatException ignored) {
            }
         }

         System.out.println("[worldprotect] " + REGIONS.size() + " region(s) loaded");
      } catch (IOException e) {
         System.out.println("[worldprotect] could not read " + f + ": " + e);
      } finally {
         if (r != null) {
            try {
               r.close();
            } catch (IOException ignored) {
            }
         }
      }
   }

   private static void save() {
      if (file == null) {
         return;
      }

      PrintWriter w = null;
      try {
         File dir = file.getParentFile();
         if (dir != null) {
            dir.mkdirs();
         }

         w = new PrintWriter(new FileWriter(file));
         w.println("# name\tdim\tx1\ty1\tz1\tx2\ty2\tz2\tpriority\tmembers\tflags");
         for (Region r : REGIONS.values()) {
            StringBuilder mem = new StringBuilder();
            for (int i = 0; i < r.members.size(); i++) {
               mem.append(i > 0 ? "," : "").append(r.members.get(i));
            }

            StringBuilder fl = new StringBuilder();
            boolean firstFlag = true;
            for (Map.Entry<String, String> e : r.flags.entrySet()) {
               // ';' and '=' are the separators and tabs break the row; drop them rather than
               // writing a line that cannot be read back.
               String v = e.getValue().replace(";", " ").replace("=", " ").replace("\t", " ");
               fl.append(firstFlag ? "" : ";").append(e.getKey()).append('=').append(v);
               firstFlag = false;
            }

            w.println(r.name + "\t" + r.dimension + "\t"
               + r.x1 + "\t" + r.y1 + "\t" + r.z1 + "\t"
               + r.x2 + "\t" + r.y2 + "\t" + r.z2 + "\t"
               + r.priority + "\t" + mem + "\t" + fl);
         }
      } catch (IOException e) {
         System.out.println("[worldprotect] could not write " + file + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }
   }
}
