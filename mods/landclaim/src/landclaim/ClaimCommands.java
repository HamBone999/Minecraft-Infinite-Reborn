package landclaim;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;
import java.util.List;

/** /claim and friends. Returns true when the command was handled and should not fall through. */
public final class ClaimCommands {

   private ClaimCommands() { }

   public static boolean handle(EntityPlayerMP player, MinecraftServer server, String raw) {
      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");
      String cmd = a[0].toLowerCase();

      if (cmd.equals("claim"))    { claim(player, server, a); return true; }
      if (cmd.equals("abandon"))  { abandon(player); return true; }
      if (cmd.equals("trust"))    { trust(player, a, true); return true; }
      if (cmd.equals("untrust"))  { trust(player, a, false); return true; }
      if (cmd.equals("claiminfo")){ info(player); return true; }
      return false;
   }

   private static void msg(EntityPlayerMP p, String s) {
      p.addChatMessage(s);
   }

   private static void claim(EntityPlayerMP p, MinecraftServer server, String[] a) {
      if (a.length > 1 && a[1].equalsIgnoreCase("list")) {
         List<Claim> mine = ClaimStore.ownedBy(p.getName());
         if (mine.isEmpty()) { msg(p, "You have no claims."); return; }
         msg(p, "Your claims:");
         for (int i = 0; i < mine.size(); i++) {
            Claim c = mine.get(i);
            msg(p, "  " + c.x1 + "," + c.z1 + " to " + c.x2 + "," + c.z2 + "  (" + c.area() + " blocks)");
         }
         return;
      }
      if (a.length > 1 && (a[1].equalsIgnoreCase("blocks") || a[1].equalsIgnoreCase("balance"))) {
         if (server.configManager.isOp(p.getName().toLowerCase())) {
            msg(p, "You are an operator -- your claims are unlimited.");
            return;
         }
         long mins = PlaytimeStore.minutes(p.getName());
         msg(p, "Claim blocks");
         msg(p, "  allowance  " + ClaimLimits.budgetFor(p.getName()));
         msg(p, "  used       " + ClaimLimits.usedBy(p.getName()));
         msg(p, "  remaining  " + ClaimLimits.remaining(p.getName()));
         msg(p, "  playtime   " + (mins / 60L) + "h " + (mins % 60L) + "m  (+"
                 + ClaimLimits.blocksPerHour() + " blocks per hour)");
         return;
      }
      if (a.length > 1 && a[1].equalsIgnoreCase("cancel")) {
         ClaimStore.clearCorner(p.getName());
         msg(p, "Selection cleared.");
         return;
      }
      msg(p, "Land claims");
      msg(p, "  Right-click two corners with a GOLD SHOVEL to claim.");
      msg(p, "  /claim list          your claims");
      msg(p, "  /claim blocks        how much land you may still claim");
      msg(p, "  /claim cancel        clear a half-made selection");
      msg(p, "  /claiminfo           who owns the ground you are standing on");
      msg(p, "  /trust <player>      let someone build in your claim");
      msg(p, "  /untrust <player>    take that back");
      msg(p, "  /abandon             delete the claim you are standing in");
   }

   private static void info(EntityPlayerMP p) {
      int x = (int) Math.floor(p.posX);
      int z = (int) Math.floor(p.posZ);
      Claim c = ClaimStore.at(x, z);
      if (c == null) { msg(p, "Unclaimed."); return; }
      msg(p, "Owned by " + c.owner + "  (" + c.x1 + "," + c.z1 + " to " + c.x2 + "," + c.z2 + ")");
      if (!c.trusted.isEmpty()) msg(p, "Trusted: " + String.join(", ", c.trusted));
   }

   private static void abandon(EntityPlayerMP p) {
      int x = (int) Math.floor(p.posX);
      int z = (int) Math.floor(p.posZ);
      Claim c = ClaimStore.at(x, z);
      if (c == null) { msg(p, "You are not standing in a claim."); return; }
      if (!c.owner.equals(p.getName().toLowerCase())) { msg(p, "That is not your claim."); return; }
      ClaimStore.remove(c);
      msg(p, "Claim abandoned.");
   }

   private static void trust(EntityPlayerMP p, String[] a, boolean add) {
      if (a.length < 2) { msg(p, "Usage: /" + (add ? "trust" : "untrust") + " <player>"); return; }
      int x = (int) Math.floor(p.posX);
      int z = (int) Math.floor(p.posZ);
      Claim c = ClaimStore.at(x, z);
      if (c == null) { msg(p, "Stand in the claim you want to change."); return; }
      if (!c.owner.equals(p.getName().toLowerCase())) { msg(p, "That is not your claim."); return; }
      String who = a[1].toLowerCase();
      if (add) {
         if (!c.trusted.contains(who)) c.trusted.add(who);
         msg(p, who + " can now build here.");
      } else {
         c.trusted.remove(who);
         msg(p, who + " can no longer build here.");
      }
      ClaimStore.save();
   }
}
