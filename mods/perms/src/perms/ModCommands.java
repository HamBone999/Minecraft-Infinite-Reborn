package perms;

import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;
import java.util.List;

/**
 * Plugin-side /kick, /ban and /unban.
 *
 * Vanilla gates these on op, and that check runs before any of our code, so a non-op admin
 * could never use them no matter what the permission file said. Handling them here — after
 * the perms gate has approved, but before vanilla sees the command — is what makes the
 * admin tier mean something without handing out op.
 *
 * Ops are deliberately NOT intercepted: they keep the stock behaviour untouched.
 */
public final class ModCommands {

   private ModCommands() { }

   public static boolean handle(EntityPlayerMP p, MinecraftServer s, String raw) {
      String me = p.getName().toLowerCase();
      if (s.configManager.isOp(me)) return false;   // vanilla path, unchanged

      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");
      String c = a[0].toLowerCase();

      if (c.equals("kick"))  { kick(p, s, a);  return true; }
      if (c.equals("ban"))   { ban(p, s, a);   return true; }
      if (c.equals("unban")) { unban(p, s, a); return true; }
      return false;
   }

   private static EntityPlayerMP find(MinecraftServer s, String name) {
      List<?> all = s.configManager.playerEntities;
      for (int i = 0; i < all.size(); i++) {
         EntityPlayerMP q = (EntityPlayerMP) all.get(i);
         if (q.getName().equalsIgnoreCase(name)) return q;
      }
      return null;
   }

   private static String tail(String[] a, int from, String def) {
      if (a.length <= from) return def;
      StringBuilder sb = new StringBuilder();
      for (int i = from; i < a.length; i++) { if (i > from) sb.append(' '); sb.append(a[i]); }
      return sb.toString();
   }

   /** An admin must not be able to kick or ban someone ranked at or above them. */
   private static boolean outranks(MinecraftServer s, String actor, String target) {
      if (s.configManager.isOp(target.toLowerCase())) return false;
      String tg = PermStore.groupOf(target);
      return !(tg.equals("owner") || tg.equals("admin"));
   }

   private static void kick(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) { p.addChatMessage("Usage: /kick <player> [reason]"); return; }
      EntityPlayerMP t = find(s, a[1]);
      if (t == null) { p.addChatMessage("No such player online."); return; }
      if (!outranks(s, p.getName(), t.getName())) {
         p.addChatMessage("You cannot kick " + t.getName() + "."); return;
      }
      String reason = tail(a, 2, "Kicked by an admin");
      t.playerNetServerHandler.kickPlayer(reason);
      s.configManager.sendPacketToAll(new ChatPacket(t.getName() + " was kicked by " + p.getName()));
      System.out.println("[perms] " + p.getName() + " kicked " + t.getName() + ": " + reason);
   }

   private static void ban(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) { p.addChatMessage("Usage: /ban <player> [reason]"); return; }
      String target = a[1];
      if (!outranks(s, p.getName(), target)) {
         p.addChatMessage("You cannot ban " + target + "."); return;
      }
      String reason = tail(a, 2, "Banned by an admin");
      s.configManager.banPlayer(p.getName(), target, reason);   // (bannedBy, target, reason)
      EntityPlayerMP t = find(s, target);
      if (t != null) t.playerNetServerHandler.kickPlayer(reason);
      s.configManager.sendPacketToAll(new ChatPacket(target + " was banned by " + p.getName()));
      System.out.println("[perms] " + p.getName() + " banned " + target + ": " + reason);
   }

   private static void unban(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) { p.addChatMessage("Usage: /unban <player>"); return; }
      s.configManager.getBannedPlayers().remove(a[1]);
      p.addChatMessage(a[1] + " is no longer banned.");
      System.out.println("[perms] " + p.getName() + " unbanned " + a[1]);
   }
}
