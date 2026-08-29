package net.minecraft.game.world.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Teleporter endpoints shared by everyone on the server.
 *
 * Stock behaviour keeps teleporter destinations on the Player, per player and per dimension, so
 * in multiplayer a gate placed by one person simply does not exist for anyone else -- they get
 * "missing teleport output" until they break and replace it, which then steals it from the
 * original owner. This registry is the world-level copy: placement records here as well, and
 * activation falls back to here when the player has no endpoint of their own.
 *
 * Deliberately free of any net.minecraft.server dependency -- the teleporter block is shared
 * client and server code, so anything it touches has to load on both.
 */
public final class TeleporterRegistry {

   /** key is dimension << 8 | slot, with the input/output halves kept apart by an offset. */
   private static final Map<Integer, Position> INPUTS = new HashMap<Integer, Position>();
   private static final Map<Integer, Position> OUTPUTS = new HashMap<Integer, Position>();
   private static File file;
   private static boolean loaded;

   private TeleporterRegistry() {
   }

   private static synchronized void ensureLoaded() {
      if (loaded) {
         return;
      }

      loaded = true;
      file = new File("world", "teleporters.tsv");
      if (!file.exists()) {
         return;
      }

      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(file));
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
               boolean input = "in".equals(p[0]);
               int dim = Integer.parseInt(p[1]);
               int slot = Integer.parseInt(p[2]);
               Position pos = new Position(Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
               (input ? INPUTS : OUTPUTS).put(key(dim, slot), pos);
            } catch (NumberFormatException ignored) {
            }
         }

         System.out.println("[teleporters] " + (INPUTS.size() + OUTPUTS.size()) + " shared endpoints");
      } catch (IOException e) {
         System.out.println("[teleporters] could not read " + file + ": " + e);
      } finally {
         if (r != null) {
            try {
               r.close();
            } catch (IOException ignored) {
            }
         }
      }
   }

   private static Integer key(int dim, int slot) {
      return Integer.valueOf(dim << 8 | slot & 255);
   }

   private static synchronized void save() {
      if (file == null) {
         return;
      }

      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(file));
         w.println("# kind\tdim\tslot\tx\ty\tz");
         write(w, "in", INPUTS);
         write(w, "out", OUTPUTS);
      } catch (IOException e) {
         System.out.println("[teleporters] could not write " + file + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }
   }

   private static void write(PrintWriter w, String kind, Map<Integer, Position> m) {
      for (Map.Entry<Integer, Position> e : m.entrySet()) {
         int k = e.getKey().intValue();
         Position p = e.getValue();
         w.println(kind + "\t" + (k >> 8) + "\t" + (k & 255) + "\t" + p.x + "\t" + p.y + "\t" + p.z);
      }
   }

   public static synchronized void setInput(int dim, int slot, Position pos) {
      ensureLoaded();
      INPUTS.put(key(dim, slot), pos);
      save();
   }

   public static synchronized void setOutput(int dim, int slot, Position pos) {
      ensureLoaded();
      OUTPUTS.put(key(dim, slot), pos);
      save();
   }

   public static synchronized Position getInput(int dim, int slot) {
      ensureLoaded();
      return INPUTS.get(key(dim, slot));
   }

   public static synchronized Position getOutput(int dim, int slot) {
      ensureLoaded();
      return OUTPUTS.get(key(dim, slot));
   }

   /** Forget an endpoint when its block is broken, so a stale destination is not left behind. */
   public static synchronized void clearAt(int dim, int slot, boolean input, Position pos) {
      ensureLoaded();
      Map<Integer, Position> m = input ? INPUTS : OUTPUTS;
      Position cur = m.get(key(dim, slot));
      if (cur != null && cur.x == pos.x && cur.y == pos.y && cur.z == pos.z) {
         m.remove(key(dim, slot));
         save();
      }
   }
}
