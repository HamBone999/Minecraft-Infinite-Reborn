package net.minecraft.game.world.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.game.world.World;

/**
 * Teleporter endpoints, remembered per world.
 *
 * Stock behaviour keeps teleporter destinations on the Player, per player and per dimension, so
 * in multiplayer a gate placed by one person simply does not exist for anyone else -- they get
 * "missing teleport output" until they break and replace it, which then steals it from the
 * original owner. This registry is the world-level copy: placement records here as well, and
 * activation falls back to here when the player has no endpoint of their own.
 *
 * It is also the only durable copy. Player.teleporterPositionsIn/Out are never written to NBT,
 * so the stock endpoints do not survive quitting the game at all.
 *
 * The file lives inside the world it belongs to, asked for through the save handler. It used to
 * be a hardcoded "world/teleporters.tsv", which is the world folder only on a dedicated server;
 * a singleplayer install keeps its worlds under saves/, so the file was never found and never
 * written. Combined with the NBT gap above, that meant every singleplayer teleporter forgot its
 * destination on quit and then correctly reported that its output was missing.
 *
 * Deliberately free of any net.minecraft.server dependency -- the teleporter block is shared
 * client and server code, so anything it touches has to load on both.
 */
public final class TeleporterRegistry {

   /** key is dimension << 8 | slot, with the input/output halves kept apart by an offset. */
   private static final Map<Integer, Position> INPUTS = new HashMap<Integer, Position>();
   private static final Map<Integer, Position> OUTPUTS = new HashMap<Integer, Position>();

   /** Where the world in use keeps its endpoints. Null until the first lookup. */
   private static File file;

   private TeleporterRegistry() {
   }

   /** This world's endpoint file, falling back to the old fixed path if it cannot be asked. */
   private static File fileFor(World world) {
      if (world != null) {
         try {
            File f = world.getSaveHandler().getMapFileFromName("teleporters");
            if (f != null) {
               return f;
            }
         } catch (Throwable ignored) {
         }
      }

      return new File("world", "teleporters.tsv");
   }

   /**
    * Loads the endpoints for {@code world}, if they are not already loaded.
    *
    * Keyed on the resolved path rather than a boolean, so opening a different save reloads
    * instead of quietly answering with the previous world's teleporters.
    */
   private static synchronized void ensureLoaded(World world) {
      File want = fileFor(world);
      if (want.equals(file)) {
         return;
      }

      file = want;
      INPUTS.clear();
      OUTPUTS.clear();

      File read = want;
      if (!read.exists()) {
         // Servers built before this moved kept it here. Read it once so their teleporters
         // survive the change; the next save writes to the new location.
         File legacy = new File("world", "teleporters.tsv");
         if (!legacy.exists()) {
            return;
         }

         read = legacy;
      }

      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(read));
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

         System.out.println("[teleporters] " + (INPUTS.size() + OUTPUTS.size()) + " endpoints from " + read);
      } catch (IOException e) {
         System.out.println("[teleporters] could not read " + read + ": " + e);
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
         File dir = file.getParentFile();
         if (dir != null) {
            dir.mkdirs();
         }

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

   public static synchronized void setInput(World world, int dim, int slot, Position pos) {
      ensureLoaded(world);
      INPUTS.put(key(dim, slot), pos);
      save();
   }

   public static synchronized void setOutput(World world, int dim, int slot, Position pos) {
      ensureLoaded(world);
      OUTPUTS.put(key(dim, slot), pos);
      save();
   }

   public static synchronized Position getInput(World world, int dim, int slot) {
      ensureLoaded(world);
      return INPUTS.get(key(dim, slot));
   }

   public static synchronized Position getOutput(World world, int dim, int slot) {
      ensureLoaded(world);
      return OUTPUTS.get(key(dim, slot));
   }

   /** Forget an endpoint when its block is broken, so a stale destination is not left behind. */
   public static synchronized void clearAt(World world, int dim, int slot, boolean input, Position pos) {
      ensureLoaded(world);
      Map<Integer, Position> m = input ? INPUTS : OUTPUTS;
      Position cur = m.get(key(dim, slot));
      if (cur != null && cur.x == pos.x && cur.y == pos.y && cur.z == pos.z) {
         m.remove(key(dim, slot));
         save();
      }
   }
}
