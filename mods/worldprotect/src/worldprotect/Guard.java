package worldprotect;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The decisions. Mixins ask, this answers, so the enforcement rules live in one readable place
 * rather than being spread across five injection points.
 */
public final class Guard {

   /** Last region each player was inside, for greeting and farewell. */
   private static final Map<String, String> LAST_REGION = new HashMap<String, String>();

   private Guard() {
   }

   /** Operators pass everything unless they have turned their own bypass off with /rg bypass. */
   private static boolean exempt(EntityPlayerMP p, MinecraftServer server, Region r) {
      String name = p.getName();
      if (r.isMember(name)) {
         return true;
      }

      return server.configManager.isOp(name.toLowerCase()) && RegionStore.bypassing(name);
   }

   private static boolean denied(Region r, String flag) {
      return Flags.isDeny(r.flags.get(flag));
   }

   /**
    * Whether a player may change a block. The block hooks know a column but not reliably a
    * height, so this checks the column: a region that protects part of a column protects the
    * whole of it as far as building goes. Being too protective is the safe direction for spawn.
    */
   public static boolean mayBuild(EntityPlayerMP p, MinecraftServer server, int x, int z) {
      Region r = RegionStore.atColumn(p.dimension, x, z);
      if (r == null || !denied(r, Flags.BUILD)) {
         return true;
      }

      if (exempt(p, server, r)) {
         return true;
      }

      p.addChatMessage("You cannot build in " + r.name + ".");
      return false;
   }

   public static boolean mayInteract(EntityPlayerMP p, MinecraftServer server, int x, int z) {
      Region r = RegionStore.atColumn(p.dimension, x, z);
      if (r == null || !denied(r, Flags.INTERACT)) {
         return true;
      }

      if (exempt(p, server, r)) {
         return true;
      }

      p.addChatMessage("You cannot use that in " + r.name + ".");
      return false;
   }

   /** Checked against the victim's position, so a region protects the people standing in it. */
   public static boolean mayAttack(EntityPlayerMP attacker, MinecraftServer server, EntityPlayerMP victim) {
      Region r = RegionStore.at(
         victim.dimension,
         (int)Math.floor(victim.posX), (int)Math.floor(victim.posY), (int)Math.floor(victim.posZ)
      );
      if (r == null || !denied(r, Flags.PVP)) {
         return true;
      }

      if (server.configManager.isOp(attacker.getName().toLowerCase()) && RegionStore.bypassing(attacker.getName())) {
         return true;
      }

      attacker.addChatMessage("PvP is off in " + r.name + ".");
      return false;
   }

   /** True when a mob may spawn at this point. */
   public static boolean maySpawn(int dim, int x, int y, int z) {
      Region r = RegionStore.at(dim, x, y, z);
      return r == null || !denied(r, Flags.MOBS);
   }

   /** True when an explosion centred here may break blocks. */
   public static boolean mayExplode(int dim, int x, int y, int z) {
      Region r = RegionStore.at(dim, x, y, z);
      return r == null || !denied(r, Flags.EXPLOSIONS);
   }

   /**
    * Called as a player moves. Returns false when they should be pushed back out.
    *
    * Also emits greeting and farewell, because both need the same "which region are they in now
    * versus a moment ago" bookkeeping and doing it twice would let the two disagree.
    */
   public static boolean mayBeAt(EntityPlayerMP p, MinecraftServer server, int x, int y, int z) {
      Region r = RegionStore.at(p.dimension, x, y, z);
      String name = p.getName();
      String now = r == null ? "" : r.name.toLowerCase();
      String before = LAST_REGION.get(name.toLowerCase());
      if (before == null) {
         before = "";
      }

      if (r != null && denied(r, Flags.ENTRY) && !exempt(p, server, r)) {
         p.addChatMessage("You cannot enter " + r.name + ".");
         return false;
      }

      if (!now.equals(before)) {
         if (before.length() > 0) {
            Region old = RegionStore.get(before);
            if (old != null) {
               String bye = old.flags.get(Flags.FAREWELL);
               if (bye != null && bye.length() > 0) {
                  p.addChatMessage(bye);
               }
            }
         }

         if (r != null) {
            String hi = r.flags.get(Flags.GREETING);
            if (hi != null && hi.length() > 0) {
               p.addChatMessage(hi);
            }
         }

         LAST_REGION.put(name.toLowerCase(), now);
      }

      return true;
   }

   public static void forget(String player) {
      LAST_REGION.remove(player.toLowerCase());
   }
}
