package net.minecraft.commands.custom;

import java.util.List;

import net.minecraft.game.entity.Entity;
import net.minecraft.game.entity.EntityManager;
import net.minecraft.game.entity.mob.DamageSource;
import net.minecraft.game.item.ItemStack;
import net.minecraft.game.item.enchantment.Enchantment;
import net.minecraft.game.item.potion.Effect;
import net.minecraft.game.item.potion.EffectData;
import net.minecraft.game.world.World;
import net.minecraft.game.world.util.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The vanilla staples this build never had: setblock, fill, clone, summon, kill, clear,
 * effect, enchant, xp, say and spawnpoint.
 *
 * Everything that writes blocks goes through {@link Edits}, so /fill and /clone are undoable
 * with //undo and cannot generate chunks or stall the tick. Everything that names a block,
 * item, effect or enchantment goes through {@link Names}, so spelling works the same way in
 * every command and a wrong name suggests the right one.
 *
 * All of these are op-only. They are the commands that can flatten a base.
 */
public final class WorldCommands {

   private WorldCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String c, String[] a, boolean op) {
      if (c.equals("setblock")) { if (Cmd.needOp(p, op)) return true; setblock(p, a); return true; }
      if (c.equals("fill"))     { if (Cmd.needOp(p, op)) return true; fill(p, a); return true; }
      if (c.equals("clone"))    { if (Cmd.needOp(p, op)) return true; clone(p, a); return true; }
      if (c.equals("summon"))   { if (Cmd.needOp(p, op)) return true; summon(p, a); return true; }
      if (c.equals("kill"))     { if (Cmd.needOp(p, op)) return true; kill(p, server, a); return true; }
      if (c.equals("clear"))    { if (Cmd.needOp(p, op)) return true; clear(p, server, a); return true; }
      if (c.equals("effect"))   { if (Cmd.needOp(p, op)) return true; effect(p, server, a); return true; }
      if (c.equals("enchant"))  { if (Cmd.needOp(p, op)) return true; enchant(p, a); return true; }
      if (c.equals("xp"))       { if (Cmd.needOp(p, op)) return true; xp(p, server, a); return true; }
      if (c.equals("say"))      { if (Cmd.needOp(p, op)) return true; say(p, server, a); return true; }
      if (c.equals("spawnpoint")) { if (Cmd.needOp(p, op)) return true; spawnpoint(p, a); return true; }
      return false;
   }

   // ---- blocks ---------------------------------------------------------------

   private static void setblock(EntityPlayerMP p, String[] a) {
      if (a.length < 5) {
         Cmd.msg(p, "Usage: /setblock <x> <y> <z> <block> [data]   (~ means \"here\")");
         return;
      }

      int x = Cmd.coord(a[1], p.posX), y = Cmd.coord(a[2], p.posY), z = Cmd.coord(a[3], p.posZ);
      if (x == Cmd.BAD || y == Cmd.BAD || z == Cmd.BAD) {
         Cmd.msg(p, "Those coordinates do not read as numbers.");
         return;
      }

      int id = Names.block(a[4]);
      if (id < 0) {
         Cmd.msg(p, "No block called '" + a[4] + "'." + Names.suggestBlock(a[4]));
         return;
      }

      int meta = a.length > 5 ? Cmd.intOr(a[5], 0) : 0;
      World w = p.world;
      if (!w.blockExists(x, y, z)) {
         Cmd.msg(p, "That position is not loaded. Go closer to it first.");
         return;
      }

      Edits.Edit e = Edits.begin(w);
      boolean did = e.set(x, y, z, id, meta);
      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, did ? "Block placed at " + x + " " + y + " " + z + "."
                     : "That block was already there.");
   }

   private static void fill(EntityPlayerMP p, String[] a) {
      if (a.length < 8) {
         Cmd.msg(p, "Usage: /fill <x1> <y1> <z1> <x2> <y2> <z2> <block> [data]");
         return;
      }

      int[] r = Cmd.region(p, a, 1);
      if (r == null) {
         Cmd.msg(p, "Those coordinates do not read as numbers.");
         return;
      }

      int id = Names.block(a[7]);
      if (id < 0) {
         Cmd.msg(p, "No block called '" + a[7] + "'." + Names.suggestBlock(a[7]));
         return;
      }

      int meta = a.length > 8 ? Cmd.intOr(a[8], 0) : 0;
      long volume = Cmd.volume(r);
      if (volume > Edits.MAX_BLOCKS) {
         Cmd.msg(p, "That is " + volume + " blocks; the limit is " + Edits.MAX_BLOCKS + ".");
         return;
      }

      Edits.Edit e = Edits.begin(p.world);
      for (int x = r[0]; x <= r[3]; x++) {
         for (int y = r[1]; y <= r[4]; y++) {
            for (int z = r[2]; z <= r[5]; z++) {
               e.set(x, y, z, id, meta);
            }
         }
      }

      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, "Filled " + e.changed() + " blocks." + Cmd.skipNote(e));
   }

   private static void clone(EntityPlayerMP p, String[] a) {
      if (a.length < 10) {
         Cmd.msg(p, "Usage: /clone <x1> <y1> <z1> <x2> <y2> <z2> <toX> <toY> <toZ>");
         return;
      }

      int[] r = Cmd.region(p, a, 1);
      int tx = Cmd.coord(a[7], p.posX), ty = Cmd.coord(a[8], p.posY), tz = Cmd.coord(a[9], p.posZ);
      if (r == null || tx == Cmd.BAD || ty == Cmd.BAD || tz == Cmd.BAD) {
         Cmd.msg(p, "Those coordinates do not read as numbers.");
         return;
      }

      long volume = Cmd.volume(r);
      if (volume > Edits.MAX_BLOCKS) {
         Cmd.msg(p, "That is " + volume + " blocks; the limit is " + Edits.MAX_BLOCKS + ".");
         return;
      }

      World w = p.world;
      int sx = r[3] - r[0] + 1, sy = r[4] - r[1] + 1, sz = r[5] - r[2] + 1;

      // Read the whole source before writing any of it, so a region that overlaps its own
      // destination copies what was there rather than what it has just written.
      int[] ids = new int[(int)volume];
      int[] metas = new int[(int)volume];
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

      Edits.Edit e = Edits.begin(w);
      i = 0;
      for (int x = 0; x < sx; x++) {
         for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
               if (ids[i] >= 0) {
                  e.set(tx + x, ty + y, tz + z, ids[i], metas[i]);
               }

               i++;
            }
         }
      }

      e.commit(p.getName().toLowerCase());
      Cmd.msg(p, "Cloned " + e.changed() + " blocks." + Cmd.skipNote(e));
   }

   // ---- entities -------------------------------------------------------------

   private static void summon(EntityPlayerMP p, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: /summon <entity> [x] [y] [z]");
         Cmd.msg(p, "Known: " + Cmd.join(EntityManager.validEntityIDs(), 18));
         return;
      }

      double x = p.posX, y = p.posY, z = p.posZ;
      if (a.length >= 5) {
         int cx = Cmd.coord(a[2], p.posX), cy = Cmd.coord(a[3], p.posY), cz = Cmd.coord(a[4], p.posZ);
         if (cx == Cmd.BAD || cy == Cmd.BAD || cz == Cmd.BAD) {
            Cmd.msg(p, "Those coordinates do not read as numbers.");
            return;
         }

         x = cx + 0.5;
         y = cy;
         z = cz + 0.5;
      }

      // createEntity matches the registry's own spelling, so try what was typed and then the
      // canonical capitalisation of every known id before giving up.
      Entity ent = EntityManager.createEntity(a[1], p.world);
      if (ent == null) {
         List<String> known = EntityManager.validEntityIDs();
         for (int i = 0; i < known.size(); i++) {
            if (Names.key(known.get(i)).equals(Names.key(a[1]))) {
               ent = EntityManager.createEntity(known.get(i), p.world);
               break;
            }
         }
      }

      if (ent == null) {
         Cmd.msg(p, "No entity called '" + a[1] + "'.");
         Cmd.msg(p, "Known: " + Cmd.join(EntityManager.validEntityIDs(), 18));
         return;
      }

      ent.setPosition(x, y, z);
      p.world.spawnEntity(ent);
      Cmd.msg(p, "Summoned " + a[1] + ".");
   }

   private static void kill(EntityPlayerMP p, MinecraftServer s, String[] a) {
      EntityPlayerMP target = p;
      if (a.length > 1) {
         target = Cmd.find(s, a[1]);
         if (target == null) {
            Cmd.msg(p, "'" + a[1] + "' is not online.");
            return;
         }
      }

      // outOfWorld, so armour and god mode do not stop it -- /kill has to be the thing that
      // always works, or an op can strand themselves.
      target.damageEntity(DamageSource.outOfWorld, 1000);
      if (target != p) {
         Cmd.msg(p, "Killed " + target.getName() + ".");
         Cmd.msg(target, "You were killed by " + p.getName() + ".");
      }
   }

   private static void clear(EntityPlayerMP p, MinecraftServer s, String[] a) {
      EntityPlayerMP t = p;
      if (a.length > 1) {
         t = Cmd.find(s, a[1]);
         if (t == null) {
            Cmd.msg(p, "'" + a[1] + "' is not online.");
            return;
         }
      }

      int n = 0;
      n += wipe(t.inventory.mainInventory);
      n += wipe(t.inventory.armorSlots);
      n += wipe(t.inventory.accessorySlots);
      n += wipe(t.inventory.offhandSlot);
      Cmd.msg(p, "Cleared " + n + " stacks from " + (t == p ? "your inventory" : t.getName()) + ".");
      if (t != p) {
         Cmd.msg(t, "Your inventory was cleared by " + p.getName() + ".");
      }
   }

   private static int wipe(ItemStack[] slots) {
      int n = 0;
      if (slots != null) {
         for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
               slots[i] = null;
               n++;
            }
         }
      }

      return n;
   }

   // ---- player state ---------------------------------------------------------

   private static void effect(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 3) {
         Cmd.msg(p, "Usage: /effect <player> <effect> [seconds] [level]  |  /effect <player> clear");
         return;
      }

      EntityPlayerMP t = Cmd.find(s, a[1]);
      if (t == null) {
         Cmd.msg(p, "'" + a[1] + "' is not online.");
         return;
      }

      if (a[2].equalsIgnoreCase("clear")) {
         t.clearEffects();
         Cmd.msg(p, "Cleared effects on " + t.getName() + ".");
         return;
      }

      Effect eff = Names.effect(a[2]);
      if (eff == null) {
         Cmd.msg(p, "No effect called '" + a[2] + "'." + Names.suggestEffect(a[2]));
         return;
      }

      int secs = a.length > 3 ? Cmd.intOr(a[3], 30) : 30;
      int amp = a.length > 4 ? Cmd.intOr(a[4], 0) : 0;
      t.addEffect(new EffectData(eff.getId(), secs * 20, amp));
      Cmd.msg(p, "Gave " + t.getName() + " " + a[2] + " for " + secs + "s.");
   }

   private static void enchant(EntityPlayerMP p, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: /enchant <enchantment> [level]   (applies to the item you are holding)");
         return;
      }

      ItemStack held = p.inventory.getCurrentItem();
      if (held == null) {
         Cmd.msg(p, "You are not holding anything.");
         return;
      }

      Enchantment en = Names.enchantment(a[1]);
      if (en == null) {
         Cmd.msg(p, "No enchantment called '" + a[1] + "'." + Names.suggestEnchant(a[1]));
         return;
      }

      int lvl = a.length > 2 ? Cmd.intOr(a[2], 1) : 1;
      held.addEnchantment(en, lvl);
      Cmd.msg(p, "Applied " + en.name + " " + lvl + ".");
   }

   private static void xp(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: /xp <amount> [player]      (score; negative takes it away)");
         return;
      }

      EntityPlayerMP t = a.length > 2 ? Cmd.find(s, a[2]) : p;
      if (t == null) {
         Cmd.msg(p, "'" + a[2] + "' is not online.");
         return;
      }

      int amount = Cmd.intOr(a[1], 0);
      t.score += amount;
      if (t.score < 0) {
         t.score = 0;
      }

      Cmd.msg(p, t.getName() + " score is now " + t.score + ".");
   }

   private static void say(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) {
         Cmd.msg(p, "Usage: /say <message>");
         return;
      }

      String text = "§d[Server] §f" + Cmd.rest(a, 1);
      List<EntityPlayerMP> all = s.configManager.playerEntities;
      for (int i = 0; i < all.size(); i++) {
         all.get(i).addChatMessage(text);
      }

      MinecraftServer.logger.info("[say] " + p.getName() + ": " + Cmd.rest(a, 1));
   }

   private static void spawnpoint(EntityPlayerMP p, String[] a) {
      int x = (int)Math.floor(p.posX), y = (int)Math.floor(p.posY), z = (int)Math.floor(p.posZ);
      if (a.length >= 4) {
         x = Cmd.coord(a[1], p.posX);
         y = Cmd.coord(a[2], p.posY);
         z = Cmd.coord(a[3], p.posZ);
         if (x == Cmd.BAD || y == Cmd.BAD || z == Cmd.BAD) {
            Cmd.msg(p, "Those coordinates do not read as numbers.");
            return;
         }
      }

      p.world.setSpawnPoint(new Position(x, y, z));

      // Keep /spawn pointing at the same place. /setspawn and /spawnpoint are two names for
      // one idea, and letting them write different records is how "spawn" ends up meaning two
      // different spots depending on which command somebody happened to use.
      PointStore.put("spawn", new PointStore.Point(p.dimension, x + 0.5, y, z + 0.5, p.yaw, p.pitch));

      Cmd.msg(p, "World spawn for this dimension set to " + x + " " + y + " " + z + ".");
      Cmd.msg(p, "New players arrive and respawns land here, and /spawn brings you here.");
   }
}
