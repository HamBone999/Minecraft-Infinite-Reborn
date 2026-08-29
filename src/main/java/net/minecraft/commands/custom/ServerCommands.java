package net.minecraft.commands.custom;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.custom.PointStore.Point;
import net.minecraft.game.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The commands this build was missing: homes, warps, spawn, back, tpa, msg and friends.
 *
 * Every one that moves a player goes through {@link #teleport}, so all of them are
 * dimension-aware in exactly the same way. Nothing here calls setPosition directly.
 */
public final class ServerCommands {

   private static final Map<String, String> TPA = new HashMap<String, String>();
   private static final Map<String, String> LAST_MSG = new HashMap<String, String>();

   private ServerCommands() {
   }

   public static void load(File pointsFile) {
      PointStore.load(pointsFile);
   }

   /** Returns true when the command was handled and must not fall through. */
   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String raw) {
      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");
      if (a.length == 0 || a[0].length() == 0) {
         return false;
      }

      String c = a[0].toLowerCase();
      String me = p.getName().toLowerCase();
      boolean op = server.configManager.isOp(me);

      if (c.equals("commands") || c.equals("cmds")) { help(p, op); return true; }

      if (c.equals("sethome"))  { PointStore.put("home:" + me, here(p)); msg(p, "Home set in this dimension."); return true; }
      if (c.equals("home"))     { go(p, PointStore.get("home:" + me), "No home set. Use /sethome."); return true; }
      if (c.equals("delhome"))  { msg(p, PointStore.remove("home:" + me) ? "Home deleted." : "No home set."); return true; }
      if (c.equals("spawn"))    { go(p, PointStore.get("spawn"), "No spawn set."); return true; }
      if (c.equals("back"))     { go(p, PointStore.get("back:" + me), "Nowhere to go back to."); return true; }
      if (c.equals("seed"))     { msg(p, "Seed: " + world(server, p.dimension).getSeed()); return true; }
      if (c.equals("list") || c.equals("who")) { list(p, server); return true; }

      if (c.equals("warp"))  { warp(p, a); return true; }
      if (c.equals("warps")) {
         List<String> w = PointStore.warpNames();
         msg(p, w.isEmpty() ? "No warps." : "Warps: " + String.join(", ", w));
         return true;
      }

      if (c.equals("tpa"))      { tpa(p, server, a); return true; }
      if (c.equals("tpaccept")) { tpaccept(p, server); return true; }
      if (c.equals("tpdeny"))   { TPA.remove(me); msg(p, "Request denied."); return true; }

      if (c.equals("msg") || c.equals("w") || c.equals("tell")) { whisper(p, server, a); return true; }
      if (c.equals("r") || c.equals("reply")) {
         String to = LAST_MSG.get(me);
         if (to == null) { msg(p, "Nobody to reply to."); return true; }
         String[] b = new String[a.length + 1];
         b[0] = "msg";
         b[1] = to;
         System.arraycopy(a, 1, b, 2, a.length - 1);
         whisper(p, server, b);
         return true;
      }

      if (c.equals("setspawn")) { if (needOp(p, op)) return true; PointStore.put("spawn", here(p)); msg(p, "Spawn set."); return true; }
      if (c.equals("setwarp"))  { if (needOp(p, op)) return true;
                                  if (a.length < 2) { msg(p, "Usage: /setwarp <name>"); return true; }
                                  PointStore.put("warp:" + a[1].toLowerCase(), here(p));
                                  msg(p, "Warp '" + a[1] + "' set."); return true; }
      if (c.equals("delwarp"))  { if (needOp(p, op)) return true;
                                  if (a.length < 2) { msg(p, "Usage: /delwarp <name>"); return true; }
                                  msg(p, PointStore.remove("warp:" + a[1].toLowerCase()) ? "Warp deleted." : "No such warp.");
                                  return true; }
      if (c.equals("heal"))     { if (needOp(p, op)) return true; p.heal(20); msg(p, "Healed."); return true; }
      if (c.equals("time"))     { if (needOp(p, op)) return true; time(p, server, a); return true; }
      if (c.equals("weather"))  { if (needOp(p, op)) return true; weather(p, server, a); return true; }
      if (c.equals("whitelist") || c.equals("wl")) { if (needOp(p, op)) return true; whitelist(p, server, a); return true; }

      return false;
   }

   // ---- movement -------------------------------------------------------------

   /**
    * The single teleport path. If the destination is in another dimension the move is queued as
    * a transfer request and completed between ticks; otherwise it goes straight through the
    * network handler. Either way the client is told, so the movement check never sees a jump it
    * would treat as cheating.
    */
   public static void teleport(EntityPlayerMP p, int dim, double x, double y, double z) {
      PointStore.put("back:" + p.getName().toLowerCase(), here(p));
      if (dim != p.dimension) {
         p.requestDimension(dim, x, y, z);
      } else {
         p.playerNetServerHandler.teleportTo(x, y, z, p.yaw, p.pitch);
      }
   }

   private static void go(EntityPlayerMP p, Point dest, String ifMissing) {
      if (dest == null) {
         msg(p, ifMissing);
         return;
      }

      boolean crossing = dest.dim != p.dimension;
      teleport(p, dest.dim, dest.x, dest.y, dest.z);
      if (crossing) {
         msg(p, "Crossing to dimension " + dest.dim + "...");
      }
   }

   private static Point here(EntityPlayerMP p) {
      return new Point(p.dimension, p.posX, p.posY, p.posZ, p.yaw, p.pitch);
   }

   private static void warp(EntityPlayerMP p, String[] a) {
      if (a.length < 2) {
         msg(p, "Usage: /warp <name>   (see /warps)");
         return;
      }

      go(p, PointStore.get("warp:" + a[1].toLowerCase()), "No warp called '" + a[1] + "'.");
   }

   private static void tpa(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) { msg(p, "Usage: /tpa <player>"); return; }
      EntityPlayerMP target = find(s, a[1]);
      if (target == null) { msg(p, "No such player online."); return; }
      TPA.put(target.getName().toLowerCase(), p.getName());
      msg(p, "Request sent to " + target.getName() + ".");
      target.addChatMessage(p.getName() + " wants to teleport to you. /tpaccept or /tpdeny");
   }

   private static void tpaccept(EntityPlayerMP p, MinecraftServer s) {
      String requester = TPA.remove(p.getName().toLowerCase());
      if (requester == null) { msg(p, "No pending request."); return; }
      EntityPlayerMP q = find(s, requester);
      if (q == null) { msg(p, requester + " is no longer online."); return; }
      teleport(q, p.dimension, p.posX, p.posY, p.posZ);
      msg(p, requester + " teleported to you.");
      q.addChatMessage("Teleported to " + p.getName() + ".");
   }

   // ---- everything else ------------------------------------------------------

   private static void msg(EntityPlayerMP p, String s) {
      p.addChatMessage(s);
   }

   private static boolean needOp(EntityPlayerMP p, boolean op) {
      if (op) return false;
      msg(p, "You do not have permission for that.");
      return true;
   }

   private static World world(MinecraftServer s, int dim) {
      return s.worlds[dim];
   }

   private static EntityPlayerMP find(MinecraftServer s, String name) {
      List<?> all = s.configManager.playerEntities;
      for (int i = 0; i < all.size(); i++) {
         EntityPlayerMP q = (EntityPlayerMP)all.get(i);
         if (q.getName().equalsIgnoreCase(name)) {
            return q;
         }
      }

      return null;
   }

   private static void list(EntityPlayerMP p, MinecraftServer s) {
      List<?> all = s.configManager.playerEntities;
      List<String> names = new ArrayList<String>();
      for (int i = 0; i < all.size(); i++) {
         EntityPlayerMP q = (EntityPlayerMP)all.get(i);
         names.add(q.getName() + (q.dimension != 0 ? " (dim " + q.dimension + ")" : ""));
      }

      msg(p, "Online (" + names.size() + "): " + String.join(", ", names));
   }

   private static void whisper(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 3) { msg(p, "Usage: /msg <player> <message>"); return; }
      EntityPlayerMP target = find(s, a[1]);
      if (target == null) { msg(p, "No such player online."); return; }
      StringBuilder sb = new StringBuilder();
      for (int i = 2; i < a.length; i++) {
         if (i > 2) sb.append(' ');
         sb.append(a[i]);
      }

      String text = sb.toString();
      target.addChatMessage("[" + p.getName() + " -> you] " + text);
      msg(p, "[you -> " + target.getName() + "] " + text);
      LAST_MSG.put(target.getName().toLowerCase(), p.getName());
      LAST_MSG.put(p.getName().toLowerCase(), target.getName());
   }

   private static void time(EntityPlayerMP p, MinecraftServer s, String[] a) {
      World w = world(s, p.dimension);
      if (a.length < 2) { msg(p, "Time is " + w.getWorldTime()); return; }
      String v = a[1].toLowerCase();
      long day = w.getWorldTime() / 24000L * 24000L;
      if (v.equals("day")) { w.setTime(day + 1000L); msg(p, "Set to day."); }
      else if (v.equals("night")) { w.setTime(day + 13000L); msg(p, "Set to night."); }
      else {
         try { w.setTime(Long.parseLong(v)); msg(p, "Time set to " + v + "."); }
         catch (NumberFormatException e) { msg(p, "Usage: /time day|night|<ticks>"); }
      }
   }

   private static void weather(EntityPlayerMP p, MinecraftServer s, String[] a) {
      World w = world(s, p.dimension);
      if (a.length < 2) { msg(p, "Raining: " + w.isRaining()); return; }
      String v = a[1].toLowerCase();
      if (v.equals("clear")) { w.setRainStrength(0.0F); msg(p, "Weather cleared."); }
      else if (v.equals("rain") || v.equals("storm")) { w.setRainStrength(1.0F); msg(p, "Let it rain."); }
      else msg(p, "Usage: /weather clear|rain");
   }

   private static void whitelist(EntityPlayerMP p, MinecraftServer s, String[] a) {
      String sub = a.length > 1 ? a[1].toLowerCase() : "help";
      if (sub.equals("list")) {
         java.util.Set<String> w = s.configManager.getWhiteListedIPs();
         if (w.isEmpty()) { msg(p, "Whitelist is empty."); return; }
         List<String> names = new ArrayList<String>(w);
         java.util.Collections.sort(names);
         msg(p, "Whitelisted (" + names.size() + "): " + String.join(", ", names));
         return;
      }
      if (sub.equals("add")) {
         if (a.length < 3) { msg(p, "Usage: /whitelist add <player>"); return; }
         String who = a[2].toLowerCase();
         if (s.configManager.getWhiteListedIPs().contains(who)) { msg(p, who + " is already whitelisted."); return; }
         s.configManager.whiteList(who);
         msg(p, "Added " + who + ". They can join right now -- no restart.");
         return;
      }
      if (sub.equals("remove") || sub.equals("rm")) {
         if (a.length < 3) { msg(p, "Usage: /whitelist remove <player>"); return; }
         String who = a[2].toLowerCase();
         if (!s.configManager.getWhiteListedIPs().contains(who)) { msg(p, who + " is not on the whitelist."); return; }
         s.configManager.unWhiteList(who);
         msg(p, "Removed " + who + ".");
         return;
      }
      if (sub.equals("reload") || sub.equals("refresh")) {
         int before = s.configManager.getWhiteListedIPs().size();
         s.configManager.reloadWhiteList();
         msg(p, "Whitelist reloaded: " + before + " -> " + s.configManager.getWhiteListedIPs().size() + " entries.");
         return;
      }

      msg(p, "Whitelist (takes effect immediately, no restart):");
      msg(p, "  /whitelist list | add <player> | remove <player> | reload");
   }

   private static void help(EntityPlayerMP p, boolean op) {
      msg(p, "Added commands (all teleports work across dimensions):");
      msg(p, "  /home /sethome /delhome    /spawn    /back    /list    /seed");
      msg(p, "  /warp <n> /warps           /tpa <player> /tpaccept /tpdeny");
      msg(p, "  /msg <player> <text> /r");
      if (op) {
         msg(p, "  op: /setspawn /setwarp /delwarp /time /weather /heal");
         msg(p, "  op: /whitelist list|add|remove|reload");
      }
   }
}
