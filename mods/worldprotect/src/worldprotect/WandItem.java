package worldprotect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.game.item.Item;
import net.minecraft.game.item.ItemStack;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The region selection tool.
 *
 * Deliberately not the gold shovel, which land claims already use, and not the stone axe, which
 * the built-in // region editor uses. Sharing an item between two selection systems means every
 * click does two things and neither is predictable.
 */
public final class WandItem {

   private static final String[] CANDIDATES = { "debugStick", "hoeGold", "axeGold", "shovelDiamond", "stick" };

   private static int id = Integer.MIN_VALUE;
   private static String name = "?";

   private WandItem() {
   }

   public static synchronized int id() {
      if (id == Integer.MIN_VALUE) {
         id = -1;
         for (int i = 0; i < CANDIDATES.length; i++) {
            int found = lookup(CANDIDATES[i]);
            if (found >= 0) {
               id = found;
               name = CANDIDATES[i];
               break;
            }
         }
      }

      return id;
   }

   public static String name() {
      id();
      return name;
   }

   /** Reads ItemList by reflection so a rename upstream is a missing wand, not a crash. */
   private static int lookup(String field) {
      try {
         Class<?> list = Class.forName("net.minecraft.game.item.ItemList");
         Field f = list.getDeclaredField(field);
         if (!Modifier.isStatic(f.getModifiers())) {
            return -1;
         }

         f.setAccessible(true);
         Object v = f.get(null);
         return v instanceof Item ? ((Item)v).id : -1;
      } catch (ReflectiveOperationException e) {
         return -1;
      } catch (LinkageError e) {
         // A failed static initialiser is not a missing field, and swallowing it silently is
         // how a poisoned registry class turns into an unexplained crash somewhere else.
         System.out.println("[worldprotect] item registry not ready for '" + field + "': " + e);
         return -1;
      }
   }

   public static boolean give(EntityPlayerMP p) {
      int i = id();
      if (i < 0) {
         return false;
      }

      p.inventory.addItemToInventory(new ItemStack(i, 1, 0));
      return true;
   }

   public static boolean isHeld(EntityPlayerMP p) {
      int i = id();
      if (i < 0) {
         return false;
      }

      ItemStack held = p.inventory.getCurrentItem();
      return held != null && held.itemID == i;
   }
}
