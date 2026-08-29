package net.minecraft.commands.custom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Named points: per-player homes, global warps, the server spawn, and /back.
 *
 * Every point records the dimension it belongs to, so returning to one always puts you in the
 * right world. Stored as tab-separated text in world/points.tsv; a file written before dimensions
 * were recorded still loads and is treated as dimension 0.
 */
public final class PointStore {

   public static final class Point {
      public final int dim;
      public final double x, y, z;
      public final float yaw, pitch;

      public Point(int dim, double x, double y, double z, float yaw, float pitch) {
         this.dim = dim;
         this.x = x;
         this.y = y;
         this.z = z;
         this.yaw = yaw;
         this.pitch = pitch;
      }
   }

   private static final Map<String, Point> POINTS = new HashMap<String, Point>();
   private static File file;

   private PointStore() {
   }

   public static synchronized void load(File f) {
      file = f;
      POINTS.clear();
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
            if (p.length < 6) {
               continue;
            }

            try {
               // older files have no dimension column: x y z yaw pitch
               int dim = p.length >= 7 ? Integer.parseInt(p[6]) : 0;
               POINTS.put(
                  p[0],
                  new Point(
                     dim,
                     Double.parseDouble(p[1]),
                     Double.parseDouble(p[2]),
                     Double.parseDouble(p[3]),
                     Float.parseFloat(p[4]),
                     Float.parseFloat(p[5])
                  )
               );
            } catch (NumberFormatException ignored) {
            }
         }

         System.out.println("[commands] loaded " + POINTS.size() + " points");
      } catch (IOException e) {
         System.out.println("[commands] could not read " + f + ": " + e);
      } finally {
         if (r != null) {
            try {
               r.close();
            } catch (IOException ignored) {
            }
         }
      }
   }

   public static synchronized void save() {
      if (file == null) {
         return;
      }

      PrintWriter w = null;
      try {
         w = new PrintWriter(new java.io.FileWriter(file));
         w.println("# key\tx\ty\tz\tyaw\tpitch\tdim");
         for (Map.Entry<String, Point> e : POINTS.entrySet()) {
            Point p = e.getValue();
            w.println(e.getKey() + "\t" + p.x + "\t" + p.y + "\t" + p.z + "\t" + p.yaw + "\t" + p.pitch + "\t" + p.dim);
         }
      } catch (IOException e) {
         System.out.println("[commands] could not write " + file + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }
   }

   public static synchronized void put(String key, Point p) {
      ensureLoaded();
      POINTS.put(key, p);
      save();
   }

   /**
    * Loads world/points.tsv on first use. Doing it lazily keeps MinecraftServer unpatched --
    * by the time any command runs, the working directory and world folder both exist.
    */
   private static void ensureLoaded() {
      if (file == null) {
         load(new File("world", "points.tsv"));
      }
   }

   public static synchronized Point get(String key) {
      ensureLoaded();
      return POINTS.get(key);
   }

   public static synchronized boolean remove(String key) {
      ensureLoaded();
      boolean removed = POINTS.remove(key) != null;
      if (removed) {
         save();
      }

      return removed;
   }

   public static synchronized List<String> warpNames() {
      ensureLoaded();
      List<String> out = new ArrayList<String>();
      for (String k : POINTS.keySet()) {
         if (k.startsWith("warp:")) {
            out.add(k.substring(5));
         }
      }

      return out;
   }
}
