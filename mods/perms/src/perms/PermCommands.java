package perms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;
import java.io.File;
import java.util.Set;

/** /perms — op only. Returns true when handled. */
public final class PermCommands {

   private PermCommands() { }

   /** /nick — op only. Changes the name shown in chat, nothing else. */
   private static void nick(EntityPlayerMP p, MinecraftServer s, String[] a) {
      if (a.length < 2) {
         String cur = NickStore.get(p.getName());
         p.addChatMessage(cur == null ? "No nickname set. /nick <name> or /nick off"
                                      : "Your nickname is '" + cur + "'. /nick off to clear.");
         return;
      }
      if (a[1].equalsIgnoreCase("off") || a[1].equalsIgnoreCase("clear")) {
         p.addChatMessage(NickStore.clear(p.getName()) ? "Nickname cleared." : "You had no nickname.");
         return;
      }
      String want = a[1];
      String bad = NickStore.rejectReason(p.getName(), want, s.configManager.getPlayerNames());
      if (bad != null) { p.addChatMessage("Cannot use that nickname: " + bad + "."); return; }
      NickStore.set(p.getName(), want);
      p.addChatMessage("You now appear as '" + want + "' in chat.");
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer s, String raw) {
      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");

      if (a[0].equalsIgnoreCase("nick")) { nick(p, s, a); return true; }

      if (!a[0].equalsIgnoreCase("perms") && !a[0].equalsIgnoreCase("perm")) return false;

      String sub = a.length > 1 ? a[1].toLowerCase() : "help";

      if (sub.equals("groups")) {
         p.addChatMessage("Groups: " + String.join(", ", PermStore.groupNames()));
         return true;
      }
      if (sub.equals("show")) {
         String who = a.length > 2 ? a[2] : p.getName();
         String g = PermStore.groupOf(who);
         p.addChatMessage(who + " is in group '" + g + "'");
         Set<String> c = PermStore.commandsOf(g);
         p.addChatMessage("  may use: " + (c.contains("*") ? "everything" : String.join(", ", c)));
         return true;
      }
      if (sub.equals("set")) {
         if (a.length < 4) { p.addChatMessage("Usage: /perms set <player> <group>"); return true; }
         if (PermStore.setGroup(a[2], a[3])) {
            p.addChatMessage(a[2] + " is now in group '" + a[3].toLowerCase() + "'.");
         } else {
            p.addChatMessage("No such group '" + a[3] + "'. Try /perms groups");
         }
         return true;
      }
      if (sub.equals("reload")) {
         PermStore.load(new File("world", "perm-groups.tsv"), new File("world", "perm-players.tsv"));
         p.addChatMessage("Permissions reloaded from disk.");
         return true;
      }
      p.addChatMessage("Permissions (op only):");
      p.addChatMessage("  /perms groups              list groups");
      p.addChatMessage("  /perms show [player]       what someone may run");
      p.addChatMessage("  /perms set <player> <grp>  move someone between groups");
      p.addChatMessage("  /perms reload              re-read the tsv files");
      p.addChatMessage("Edit world/perm-groups.tsv to change what a group may do.");
      p.addChatMessage("  /nick <name> | off         change your chat name (op only)");
      return true;
   }
}
