package net.minecraft.commands.custom;

import net.minecraft.game.item.ItemStack;
import net.minecraft.game.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * A small WorldEdit: select two corners, then set, replace, copy, paste or undo the box
 * between them.
 *
 * These are the {@code //} commands, spelled with two slashes exactly as WorldEdit spells
 * them, so muscle memory carries over. The leading slash is still on the command word by the
 * time it reaches {@link #handle}, which is how they stay distinct from {@code /set}.
 *
 * Bulk writes go through {@link Edits}, which is what makes //undo work and what stops a large
 * selection from generating chunks or stalling the tick. Undo history is per player and eight
 * edits deep, and /fill and /clone push onto the same stack, so //undo takes back the last
 * thing you did regardless of which command did it.
 */
public final class WorldEditCommands {

   private WorldEditCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String c, String[] a, boolean op) {
      if (!c.startsWith("/")) {
         return false;
      }

      String w = c.substring(1);
      if (Cmd.needOp(p, op)) {
         return true;
      }

      String me = p.getName().toLowerCase();
      Selection sel = Selection.of(me);

      if (w.equals("wand"))    { wand(p); return true; }
      if (w.equals("pos1"))    { corner(p, sel, true); return true; }
      if (w.equals("pos2"))    { corner(p, sel, false); return true; }
      if (w.equals("desel") || w.equals("deselect")) {
         sel.has1 = sel.has2 = false;
         Cmd.msg(p, "Selection cleared.");
         return true;
      }

      if (w.equals("size") || w.equals("sel")) { size(p, sel); return true; }
      if (w.equals("set"))     { set(p, sel, a); return true; }
      if (w.equals("replace")) { replace(p, sel, a); return true; }
      if (w.equals("count"))   { count(p, sel, a); return true; }
      if (w.equals("copy"))    { copy(p, sel); return true; }
      if (w.equals("paste"))   { paste(p, sel); return true; }
      if (w.equals("undo"))    { undo(p, me); return true; }

      Cmd.msg(p, "Unknown // command. Try //wand, //pos1, //pos2, //size, //set, //replace, //count, //copy, //paste, //undo.");
      return true;
   }

   // ---- selection ------------------------------------------------------------

   private static void wand(EntityPlayerMP p) {
      int id = WandHook.wandId();
      if (id < 0) {
         Cmd.msg(p, "No wand item available in this build. Use //pos1 and //pos2 instead.");
         return;
      }

      if (!WandHook.toggle(p)) {
         Cmd.msg(p, "Wand off. Your " + WandHook.wandName() + " is an ordinary tool again.");
         return;
      }

      p.inventory.addItemToInventory(new ItemStack(id, 1, 0));
      Cmd.msg(p, "Wand on (" + WandHook.wandName() + "). Left-click a block for corner 1, right-click for corner 2.");
      Cmd.msg(p, "Run //wand again to turn it back into a normal tool.");
   }

   private static void corner(EntityPlayerMP p, Selection sel, boolean first) {
      int x = (int)Math.floor(p.posX), y = (int)Math.floor(p.posY), z = (int)Math.floor(p.posZ);
      if (first) {
         sel.set1(x, y, z, p.dimension);
      } else {
         sel.set2(x, y, z, p.dimension);
      }

      Cmd.msg(p, "Corner " + (first ? "1" : "2") + " set to " + x + " " + y + " " + z + ".");
      if (sel.complete()) {
         Cmd.msg(p, describe(sel));
      }
   }

   private static String describe(Selection sel) {
      int[] r = sel.box();
      int sx = r[3] - r[0] + 1, sy = r[4] - r[1] + 1, sz = r[5] - r[2] + 1;
      return "Selection " + sx + "x" + sy + "x" + sz + " = " + Cmd.volume(r) + " blocks.";
   }

   private static void size(EntityPlayerMP p, Selection sel) {
      if (!sel.complete()) {
         Cmd.msg(p, "Set both corners first -- //wand, or //pos1 and //pos2.");
         return;
      }

      Cmd.msg(p, describe(sel));
   }

   /**
    * Returns the selection box, having checked it exists, is in the dimension the player is
    * standing in, and is small enough to edit. Null means a message has already been sent.
    */
   private static int[] usable(EntityPlayerMP p, Selection sel) {
      if (!sel.complete()) {
         Cmd.msg(p, "Set both corners first -- //wand, or //pos1 and //pos2.");
         return null;
      }

      if (sel.dimension != p.dimension) {
         Cmd.msg(p, "That selection is in another dimension. Go back to it, or select again here.");
         return null;
      }

      int[] r = sel.box();
      long volume = Cmd.volume(r);
      if (volume > Edits.MAX_BLOCKS) {
         Cmd.msg(p, "That selection is " + volume + " blocks; the limit is " + Edits.MAX_BLOCKS + ".");
         return null;
      }

      return r;
   }

   // ---- editing --------------------------------------------------------------

   private static void set(EntityPlayerMP p, Selection sel, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: //set <block> [data]");
         return;
      }

      int[] r = usable(p, sel);
      if (r == null) {
         return;
      }

      int id = Names.block(a[1]);
      if (id < 0) {
         Cmd.msg(p, "No block called '" + a[1] + "'." + Names.suggestBlock(a[1]));
         return;
      }

      int meta = a.length > 2 ? Cmd.intOr(a[2], 0) : 0;
      Edits.Edit e = Edits.begin(p.world);
      for (int x = r[0]; x <= r[3]; x++) {
         for (int y = r[1]; y <= r[4]; y++) {
            for (int z = r[2]; z <= r[5]; z++) {
               e.set(x, y, z, id, meta);
            }
         }
      }

      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, "Set " + e.changed() + " blocks." + Cmd.skipNote(e));
   }

   private static void replace(EntityPlayerMP p, Selection sel, String[] a) {
      if (a.length < 3) {
         Cmd.msg(p, "Usage: //replace <from> <to>");
         return;
      }

      int[] r = usable(p, sel);
      if (r == null) {
         return;
      }

      int from = Names.block(a[1]);
      int to = Names.block(a[2]);
      if (from < 0) {
         Cmd.msg(p, "No block called '" + a[1] + "'." + Names.suggestBlock(a[1]));
         return;
      }

      if (to < 0) {
         Cmd.msg(p, "No block called '" + a[2] + "'." + Names.suggestBlock(a[2]));
         return;
      }

      World w = p.world;
      Edits.Edit e = Edits.begin(w);
      for (int x = r[0]; x <= r[3]; x++) {
         for (int y = r[1]; y <= r[4]; y++) {
            for (int z = r[2]; z <= r[5]; z++) {
               if (w.blockExists(x, y, z) && w.getBlockId(x, y, z) == from) {
                  e.set(x, y, z, to, 0);
               }
            }
         }
      }

      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, "Replaced " + e.changed() + " blocks." + Cmd.skipNote(e));
   }

   private static void count(EntityPlayerMP p, Selection sel, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: //count <block>");
         return;
      }

      int[] r = usable(p, sel);
      if (r == null) {
         return;
      }

      int id = Names.block(a[1]);
      if (id < 0) {
         Cmd.msg(p, "No block called '" + a[1] + "'." + Names.suggestBlock(a[1]));
         return;
      }

      World w = p.world;
      int n = 0;
      for (int x = r[0]; x <= r[3]; x++) {
         for (int y = r[1]; y <= r[4]; y++) {
            for (int z = r[2]; z <= r[5]; z++) {
               if (w.blockExists(x, y, z) && w.getBlockId(x, y, z) == id) {
                  n++;
               }
            }
         }
      }

      Cmd.msg(p, n + " x " + a[1] + " in the selection.");
   }

   // ---- clipboard ------------------------------------------------------------

   private static void copy(EntityPlayerMP p, Selection sel) {
      int[] r = usable(p, sel);
      if (r == null) {
         return;
      }

      World w = p.world;
      int sx = r[3] - r[0] + 1, sy = r[4] - r[1] + 1, sz = r[5] - r[2] + 1;
      int n = sx * sy * sz;

      int[] ids = new int[n];
      int[] metas = new int[n];
      int i = 0;
      for (int x = 0; x < sx; x++) {
         for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
               int wx = r[0] + x, wy = r[1] + y, wz = r[2] + z;
               if (w.blockExists(wx, wy, wz)) {
                  ids[i] = w.getBlockId(wx, wy, wz);
                  metas[i] = w.getBlockMetadata(wx, wy, wz);
               } else {
                  ids[i] = -1;
               }

               i++;
            }
         }
      }

      sel.clipIds = ids;
      sel.clipMetas = metas;
      sel.clipSizeX = sx;
      sel.clipSizeY = sy;
      sel.clipSizeZ = sz;

      // Remembered relative to where the player stood, so //paste puts the build back in the
      // same place relative to them rather than at some absolute corner.
      sel.clipOffX = r[0] - (int)Math.floor(p.posX);
      sel.clipOffY = r[1] - (int)Math.floor(p.posY);
      sel.clipOffZ = r[2] - (int)Math.floor(p.posZ);

      Cmd.msg(p, "Copied " + n + " blocks (" + sx + "x" + sy + "x" + sz + ").");
   }

   private static void paste(EntityPlayerMP p, Selection sel) {
      if (sel.clipIds == null) {
         Cmd.msg(p, "Nothing copied yet. Select a region and run //copy.");
         return;
      }

      int bx = (int)Math.floor(p.posX) + sel.clipOffX;
      int by = (int)Math.floor(p.posY) + sel.clipOffY;
      int bz = (int)Math.floor(p.posZ) + sel.clipOffZ;

      Edits.Edit e = Edits.begin(p.world);
      int i = 0;
      for (int x = 0; x < sel.clipSizeX; x++) {
         for (int y = 0; y < sel.clipSizeY; y++) {
            for (int z = 0; z < sel.clipSizeZ; z++) {
               if (sel.clipIds[i] >= 0) {
                  e.set(bx + x, by + y, bz + z, sel.clipIds[i], sel.clipMetas[i]);
               }

               i++;
            }
         }
      }

      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, "Pasted " + e.changed() + " blocks." + Cmd.skipNote(e));
   }

   private static void undo(EntityPlayerMP p, String me) {
      int n = Edits.undo(me);
      if (n < 0) {
         Cmd.msg(p, "Nothing to undo.");
         return;
      }

      Cmd.msg(p, "Undid " + n + " blocks. " + Edits.depth(me) + " edit(s) still in history.");
   }
}
