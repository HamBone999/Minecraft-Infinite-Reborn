package anticheat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;
import java.io.File;
import java.util.List;

/** /ac — op only. */
public final class AcCommands {

   private AcCommands() { }

   public static boolean handle(EntityPlayerMP p, MinecraftServer s, String raw) {
      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");
      if (!a[0].equalsIgnoreCase("ac") && !a[0].equalsIgnoreCase("anticheat")) return false;
      if (!s.configManager.isOp(p.getName().toLowerCase())) {
         p.addChatMessage("You do not have permission for that.");
         return true;
      }
      String sub = a.length > 1 ? a[1].toLowerCase() : "recent";

      if (sub.equals("reload")) {
         Config.load(new File("world", "anticheat.properties"));
         p.addChatMessage("Anticheat config reloaded.");
         return true;
      }
      if (sub.equals("status")) {
         p.addChatMessage("Anticheat  (" + Flags.total() + " flags this session)");
         p.addChatMessage("  movement " + mode(Config.movementEnabled, Config.movementKick)
               + "   max " + Config.maxSpeed + " blocks/packet");
         p.addChatMessage("  reach    " + mode(Config.reachEnabled, Config.reachKick)
               + "   max " + Config.maxReach + " blocks");
         p.addChatMessage("  spam     " + mode(Config.spamEnabled, Config.spamKick)
               + "   max " + Config.maxChat + " per 10s");
         p.addChatMessage("  xray     " + mode(Config.xrayEnabled, Config.xrayKick)
               + "   >" + (Config.xrayRatio * 100.0) + "% ore over " + Config.xraySample + " blocks");
         p.addChatMessage("  fly      " + mode(Config.flyEnabled, Config.flyKick)
               + "   " + Config.flyPackets + " airborne packets, no descent");
         p.addChatMessage("  rate     " + mode(Config.rateEnabled, Config.rateKick)
               + "   break " + Config.maxBreaks + "/s place " + Config.maxPlaces
               + "/s hit " + Config.maxAttacks + "/s move " + Config.maxPackets + "/s");
         p.addChatMessage("  staff    " + (Config.exemptStaff ? "EXEMPT (admin/owner/op not checked)"
                                                              : "also checked"));
         return true;
      }
      if (sub.equals("mail")) {
         List<String> m = Alerts.recentText(15);
         if (m.isEmpty()) { p.addChatMessage("No stored alerts."); return true; }
         p.addChatMessage("Stored alerts (newest last):");
         for (int i = 0; i < m.size(); i++) p.addChatMessage("  " + m.get(i));
         return true;
      }
      List<Flags.Flag> r = Flags.recent(10);
      if (r.isEmpty()) { p.addChatMessage("No detections."); return true; }
      p.addChatMessage("Recent detections:");
      for (int i = 0; i < r.size(); i++) {
         Flags.Flag f = r.get(i);
         p.addChatMessage("  " + f.player + "  " + f.check + "  " + f.detail);
      }
      p.addChatMessage("/ac status   /ac mail   /ac reload");
      return true;
   }

   private static String mode(boolean on, boolean kick) {
      return !on ? "off" : (kick ? "KICKING" : "log-only");
   }
}
