package net.minecraft.commands.custom;

import java.util.List;

import net.minecraft.game.entity.Entity;
import net.minecraft.game.entity.mob.DamageSource;
import net.minecraft.game.entity.mob.Mob;
import net.minecraft.game.entity.player.Player;
import net.minecraft.game.item.potion.EffectData;
import net.minecraft.game.item.potion.EffectList;
import net.minecraft.game.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The SinglePlayerCommands staples that work without touching the client.
 *
 * Movement in this engine is simulated by the client and merely checked by the server, so the
 * commands that change how the player *moves* -- fly, noclip, instant mining -- cannot be done
 * from here at all. They need client code and a packet to grant them, which is a feature
 * rather than a command, so they are deliberately absent instead of present and inert.
 *
 * What is here is everything that is genuinely the server's decision: damage immunity, killing
 * mobs, moving the player to a different height, and seeing in the dark.
 */
public final class PlayerCommands {

   /** Night vision long enough that it never quietly lapses mid-session. */
   private static final int LIGHT_TICKS = 1000000;

   private PlayerCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String c, String[] a, boolean op) {
      if (c.equals("god"))     { if (Cmd.needOp(p, op)) return true; god(p, server, a); return true; }
      if (c.equals("killall")) { if (Cmd.needOp(p, op)) return true; killall(p, a); return true; }
      if (c.equals("up"))      { if (Cmd.needOp(p, op)) return true; up(p, a); return true; }
      if (c.equals("ascend"))  { if (Cmd.needOp(p, op)) return true; ascend(p); return true; }
      if (c.equals("descend")) { if (Cmd.needOp(p, op)) return true; descend(p); return true; }
      if (c.equals("light"))   { if (Cmd.needOp(p, op)) return true; light(p); return true; }
      return false;
   }

   // ---- god ------------------------------------------------------------------

   private static void god(EntityPlayerMP p, MinecraftServer s, String[] a) {
      EntityPlayerMP t = a.length > 1 ? Cmd.find(s, a[1]) : p;
      if (t == null) {
         Cmd.msg(p, "'" + a[1] + "' is not online.");
         return;
      }

      t.godMode = !t.godMode;
      Cmd.msg(p, (t == p ? "God mode" : t.getName() + "'s god mode") + " is now "
         + (t.godMode ? "on" : "off") + ".");
      if (t != p) {
         Cmd.msg(t, "God mode " + (t.godMode ? "enabled" : "disabled") + " by " + p.getName() + ".");
      } else if (t.godMode) {
         Cmd.msg(p, "/kill still works on you -- that is deliberate, so you cannot get stuck.");
      }
   }

   // ---- mobs -----------------------------------------------------------------

   private static void killall(EntityPlayerMP p, String[] a) {
      int radius = a.length > 1 ? Cmd.intOr(a[1], 0) : 0;
      World w = p.world;
      List<Entity> all = w.getEntityList();
      int n = 0;

      // Copied out first: killing an entity mutates the list we would otherwise be walking.
      Entity[] snapshot = all.toArray(new Entity[all.size()]);
      for (int i = 0; i < snapshot.length; i++) {
         Entity e = snapshot[i];
         if (!(e instanceof Mob) || e instanceof Player) {
            continue;
         }

         if (radius > 0) {
            double dx = e.posX - p.posX, dy = e.posY - p.posY, dz = e.posZ - p.posZ;
            if (dx * dx + dy * dy + dz * dz > (double)radius * radius) {
               continue;
            }
         }

         ((Mob)e).damageEntity(DamageSource.outOfWorld, 1000);
         n++;
      }

      Cmd.msg(p, "Killed " + n + " mob" + (n == 1 ? "" : "s")
         + (radius > 0 ? " within " + radius + " blocks." : " in this dimension."));
   }

   // ---- vertical movement ----------------------------------------------------

   private static void up(EntityPlayerMP p, String[] a) {
      int by = a.length > 1 ? Cmd.intOr(a[1], 1) : 1;
      int x = (int)Math.floor(p.posX), z = (int)Math.floor(p.posZ);
      int y = (int)Math.floor(p.posY) + by;
      World w = p.world;
      if (y < 1 || y > w.getWorldHeight() - 2) {
         Cmd.msg(p, "That would put you outside the world.");
         return;
      }

      if (!w.blockExists(x, y, z)) {
         Cmd.msg(p, "That position is not loaded yet.");
         return;
      }

      // Stand on something, or /up is just a fall with extra steps.
      if (w.getBlockId(x, y - 1, z) == 0) {
         Edits.Edit e = Edits.begin(w);
         e.set(x, y - 1, z, net.minecraft.game.block.BlockList.glass.id, 0);
         e.commit(p.getName().toLowerCase());
      }

      ServerCommands.teleport(p, p.dimension, x + 0.5, y, z + 0.5);
      Cmd.msg(p, "Moved up " + by + " block" + (by == 1 ? "" : "s") + ".");
   }

   private static void ascend(EntityPlayerMP p) {
      World w = p.world;
      int x = (int)Math.floor(p.posX), z = (int)Math.floor(p.posZ);
      int from = (int)Math.floor(p.posY);

      for (int y = from + 1; y < w.getWorldHeight() - 1; y++) {
         if (!w.blockExists(x, y, z)) {
            break;
         }

         // Floor with two blocks of headroom above it.
         if (w.getBlockId(x, y, z) != 0 && w.getBlockId(x, y + 1, z) == 0
               && (y + 2 >= w.getWorldHeight() || w.getBlockId(x, y + 2, z) == 0)) {
            ServerCommands.teleport(p, p.dimension, x + 0.5, y + 1, z + 0.5);
            Cmd.msg(p, "Ascended to y=" + (y + 1) + ".");
            return;
         }
      }

      Cmd.msg(p, "Nothing to stand on above you.");
   }

   private static void descend(EntityPlayerMP p) {
      World w = p.world;
      int x = (int)Math.floor(p.posX), z = (int)Math.floor(p.posZ);
      int from = (int)Math.floor(p.posY);

      for (int y = from - 2; y > 0; y--) {
         if (!w.blockExists(x, y, z)) {
            break;
         }

         if (w.getBlockId(x, y, z) != 0 && w.getBlockId(x, y + 1, z) == 0
               && w.getBlockId(x, y + 2, z) == 0) {
            ServerCommands.teleport(p, p.dimension, x + 0.5, y + 1, z + 0.5);
            Cmd.msg(p, "Descended to y=" + (y + 1) + ".");
            return;
         }
      }

      Cmd.msg(p, "Nothing to stand on below you.");
   }

   // ---- light ----------------------------------------------------------------

   private static void light(EntityPlayerMP p) {
      if (p.hasEffect(EffectList.nightVision)) {
         p.removeEffect(EffectList.nightVision.getId());
         Cmd.msg(p, "Light off.");
      } else {
         p.addEffect(new EffectData(EffectList.nightVision.getId(), LIGHT_TICKS, 0));
         Cmd.msg(p, "Light on.");
      }
   }
}
