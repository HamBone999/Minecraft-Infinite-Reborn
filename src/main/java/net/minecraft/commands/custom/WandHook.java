package net.minecraft.commands.custom;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.game.item.ItemStack;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The selection wand: left-click a block for corner 1, right-click for corner 2.
 *
 * Wand mode is opt-in per player and off by default. WorldEdit proper treats any wooden axe as
 * a wand for anyone with permission, which on a server where the ops also play means an op
 * cannot chop a tree without moving their selection. Here //wand toggles it, so the axe is an
 * axe until you say otherwise.
 *
 * {@link #onDig} and {@link #onPlace} return true when they have consumed the click, and the
 * network handler returns immediately in that case -- otherwise the block would be selected
 * and then broken.
 */
public final class WandHook {

   /** Tried in order; the first one this build actually has becomes the wand. */
   private static final String[] CANDIDATES = { "axeWood", "axeStone", "axeCopper", "stick" };

   private static final Set<String> ENABLED = new HashSet<String>();

   private static int wandId = Integer.MIN_VALUE;
   private static String wandName = "?";

   private WandHook() {
   }

   /** The item id used as a wand, resolved once, or -1 if this build has none of them. */
   public static int wandId() {
      if (wandId == Integer.MIN_VALUE) {
         wandId = -1;
         for (int i = 0; i < CANDIDATES.length; i++) {
            int id = Names.item(CANDIDATES[i]);
            if (id >= 0) {
               wandId = id;
               wandName = CANDIDATES[i];
               break;
            }
         }
      }

      return wandId;
   }

   public static String wandName() {
      wandId();
      return wandName;
   }

   public static boolean enabled(EntityPlayerMP p) {
      return ENABLED.contains(p.getName().toLowerCase());
   }

   /** Returns the new state. */
   public static boolean toggle(EntityPlayerMP p) {
      String me = p.getName().toLowerCase();
      if (ENABLED.remove(me)) {
         return false;
      }

      ENABLED.add(me);
      return true;
   }

   public static void forget(String owner) {
      ENABLED.remove(owner);
   }

   private static boolean holdingWand(EntityPlayerMP p) {
      if (wandId() < 0 || !enabled(p)) {
         return false;
      }

      ItemStack held = p.inventory.getCurrentItem();
      return held != null && held.itemID == wandId;
   }

   /** Left click. Status 0 is the start of a dig; the later stages are ignored. */
   public static boolean onDig(EntityPlayerMP p, boolean op, int status, int x, int y, int z) {
      if (!op || status != 0 || !holdingWand(p)) {
         return false;
      }

      Selection.of(p.getName().toLowerCase()).set1(x, y, z, p.dimension);
      Cmd.msg(p, "Corner 1: " + x + " " + y + " " + z + report(p));
      return true;
   }

   /** Right click on a block face. */
   public static boolean onPlace(EntityPlayerMP p, boolean op, int x, int y, int z) {
      if (!op || !holdingWand(p)) {
         return false;
      }

      Selection.of(p.getName().toLowerCase()).set2(x, y, z, p.dimension);
      Cmd.msg(p, "Corner 2: " + x + " " + y + " " + z + report(p));
      return true;
   }

   private static String report(EntityPlayerMP p) {
      Selection s = Selection.of(p.getName().toLowerCase());
      if (!s.complete()) {
         return "";
      }

      int[] r = s.box();
      return "  (" + (r[3] - r[0] + 1) + "x" + (r[4] - r[1] + 1) + "x" + (r[5] - r[2] + 1)
         + " = " + Cmd.volume(r) + ")";
   }
}
