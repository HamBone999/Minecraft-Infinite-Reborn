package worldprotect;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/** /rg -- operator only, all of it. */
public final class RegionCommands {

   private RegionCommands() {
   }

   /** True when the command was ours and must not fall through to the vanilla dispatcher. */
   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String raw) {
      String line = raw.startsWith("/") ? raw.substring(1) : raw;
      String[] a = line.trim().split("\\s+");
      if (a.length == 0) {
         return false;
      }

      String c = a[0].toLowerCase();
      if (!c.equals("rg") && !c.equals("wp") && !c.equals("region") && !c.equals("worldprotect")) {
         return false;
      }

      if (!server.configManager.isOp(p.getName().toLowerCase())) {
         msg(p, "Regions are an operator tool.");
         return true;
      }

      String sub = a.length > 1 ? a[1].toLowerCase() : "help";

      if (sub.equals("help")) { help(p); return true; }
      if (sub.equals("wand")) { wand(p); return true; }
      if (sub.equals("pos1")) { pos(p, true); return true; }
      if (sub.equals("pos2")) { pos(p, false); return true; }
      if (sub.equals("define") || sub.equals("create")) { define(p, a, false); return true; }
      if (sub.equals("redefine")) { define(p, a, true); return true; }
      if (sub.equals("remove") || sub.equals("delete")) { remove(p, a); return true; }
      if (sub.equals("list")) { list(p); return true; }
      if (sub.equals("info") || sub.equals("i")) { info(p, a); return true; }
      if (sub.equals("flag") || sub.equals("f")) { flag(p, a); return true; }
      if (sub.equals("addmember") || sub.equals("addmem")) { member(p, a, true); return true; }
      if (sub.equals("removemember") || sub.equals("remmem")) { member(p, a, false); return true; }
      if (sub.equals("priority") || sub.equals("pri")) { priority(p, a); return true; }
      if (sub.equals("claim") || sub.equals("claims")) { claims(p, a); return true; }
      if (sub.equals("bypass")) {
         boolean on = RegionStore.toggleBypass(p.getName());
         msg(p, "Your region bypass is " + (on ? "on -- you ignore region rules." : "off -- regions apply to you."));
         return true;
      }

      msg(p, "Unknown: /rg " + sub + ". Try /rg help.");
      return true;
   }

   private static void help(EntityPlayerMP p) {
      msg(p, "Regions (operator only):");
      msg(p, "  /rg wand                    a " + WandItem.name() + " selects corners");
      msg(p, "  /rg pos1 /rg pos2           corners at your feet instead");
      msg(p, "  /rg define <name>           make a region from the selection");
      msg(p, "  /rg redefine <name>         move an existing one to the selection");
      msg(p, "  /rg remove <name>           delete it");
      msg(p, "  /rg list                    every region");
      msg(p, "  /rg info [name]             the region here, or a named one");
      msg(p, "  /rg flag <name> <flag> <value>");
      msg(p, "  /rg addmember <name> <player> | /rg removemember <name> <player>");
      msg(p, "  /rg priority <name> <n>     higher wins where regions overlap");
      msg(p, "  /rg bypass                  turn your own exemption off to test");
      msg(p, "Player land claims (the gold shovel ones):");
      msg(p, "  /rg claim                   whose claim you are standing in");
      msg(p, "  /rg claim delete            delete the claim you are standing in");
      msg(p, "  /rg claim of <player>       list a player's claims");
      msg(p, "  /rg claim deleteall <player>");
      msg(p, "  /rg claim trust|untrust <player>   on the claim you are standing in");
      msg(p, "Flags: " + Flags.describe());
      msg(p, "Values: allow or deny. greeting/farewell take a message.");
      msg(p, "Example: /rg flag spawn build deny");
   }

   private static void wand(EntityPlayerMP p) {
      if (!WandItem.give(p)) {
         msg(p, "No wand item available in this build. Use /rg pos1 and /rg pos2.");
         return;
      }

      msg(p, "Wand: " + WandItem.name() + ". Left-click a block for corner 1, right-click for corner 2.");
   }

   private static void pos(EntityPlayerMP p, boolean first) {
      int x = (int)Math.floor(p.posX), y = (int)Math.floor(p.posY), z = (int)Math.floor(p.posZ);
      RegionStore.setCorner(p.getName(), first, x, y, z);
      msg(p, "Corner " + (first ? "1" : "2") + " set to " + x + "," + y + "," + z + ".");
   }

   private static void define(EntityPlayerMP p, String[] a, boolean redefine) {
      if (a.length < 3) {
         msg(p, "Usage: /rg " + (redefine ? "redefine" : "define") + " <name>");
         return;
      }

      int[] c1 = RegionStore.corner(p.getName(), true);
      int[] c2 = RegionStore.corner(p.getName(), false);
      if (c1 == null || c2 == null) {
         msg(p, "Set both corners first -- /rg wand, or /rg pos1 and /rg pos2.");
         return;
      }

      String name = a[2];
      Region existing = RegionStore.get(name);
      if (redefine) {
         if (existing == null) {
            msg(p, "There is no region called " + name + ".");
            return;
         }

         existing.dimension = p.dimension;
         existing.setBounds(c1[0], c1[1], c1[2], c2[0], c2[1], c2[2]);
         RegionStore.touch();
         msg(p, "Redefined " + name + ", now " + existing.size() + ".");
         return;
      }

      Region r = new Region(name, p.dimension, c1[0], c1[1], c1[2], c2[0], c2[1], c2[2]);
      String err = RegionStore.add(r);
      if (err != null) {
         msg(p, "Cannot define: " + err + ".");
         return;
      }

      msg(p, "Defined " + name + " (" + r.size() + ", " + r.volume() + " blocks) in dimension " + p.dimension + ".");
      msg(p, "It does nothing yet. Add a rule, e.g. /rg flag " + name + " build deny");
      warnOverlappingClaims(p, r);
   }

   /**
    * Says so when a new region sits on top of player claims.
    *
    * Both systems then apply to that ground, and a region denying build overrides the claim
    * owner's permission -- which is correct, but it will look like their claim broke unless
    * somebody knew. Warning is deliberately all this does: silently deleting a player's claim
    * because an operator drew a box over it would be far worse.
    */
   private static void warnOverlappingClaims(EntityPlayerMP p, Region r) {
      if (!Claims.available()) {
         return;
      }

      List<Claims.Info> hit = Claims.overlapping(r.x1, r.z1, r.x2, r.z2);
      if (hit.isEmpty()) {
         return;
      }

      msg(p, "Note: this region covers " + hit.size() + " player claim(s):");
      for (int i = 0; i < hit.size() && i < 6; i++) {
         msg(p, "  " + hit.get(i).owner + "  " + hit.get(i).bounds());
      }

      msg(p, "Region rules win over claim permissions. /rg claim delete removes one you are standing in.");
   }

   private static void claims(EntityPlayerMP p, String[] a) {
      if (!Claims.available()) {
         msg(p, "The land claim addon is not loaded, so there are no claims to manage.");
         return;
      }

      String op = a.length > 2 ? a[2].toLowerCase() : "info";
      int x = (int)Math.floor(p.posX), z = (int)Math.floor(p.posZ);

      if (op.equals("info")) {
         Claims.Info c = Claims.at(x, z);
         if (c == null) {
            msg(p, "No claim here.");
            return;
         }

         msg(p, "Claim owned by " + c.owner);
         msg(p, "  " + c.bounds() + "  (" + c.area + " blocks)");
         msg(p, "  trusted: " + (c.trusted.isEmpty() ? "nobody" : String.join(", ", c.trusted)));
         return;
      }

      if (op.equals("delete")) {
         String owner = Claims.removeAt(x, z);
         msg(p, owner == null ? "No claim here." : "Deleted " + owner + "'s claim.");
         return;
      }

      if (op.equals("of")) {
         if (a.length < 4) {
            msg(p, "Usage: /rg claim of <player>");
            return;
         }

         List<Claims.Info> owned = Claims.ownedBy(a[3]);
         if (owned.isEmpty()) {
            msg(p, a[3] + " has no claims.");
            return;
         }

         msg(p, a[3] + " has " + owned.size() + " claim(s):");
         for (int i = 0; i < owned.size() && i < 10; i++) {
            msg(p, "  " + owned.get(i).bounds() + "  (" + owned.get(i).area + " blocks)");
         }

         return;
      }

      if (op.equals("deleteall")) {
         if (a.length < 4) {
            msg(p, "Usage: /rg claim deleteall <player>");
            return;
         }

         int n = Claims.removeAllOwnedBy(a[3]);
         msg(p, n == 0 ? a[3] + " has no claims." : "Deleted " + n + " claim(s) owned by " + a[3] + ".");
         return;
      }

      if (op.equals("trust") || op.equals("untrust")) {
         if (a.length < 4) {
            msg(p, "Usage: /rg claim " + op + " <player>");
            return;
         }

         boolean add = op.equals("trust");
         msg(p, Claims.setTrust(x, z, a[3], add)
            ? (add ? "Added " : "Removed ") + a[3] + (add ? " to" : " from") + " the claim here."
            : "No claim here.");
         return;
      }

      msg(p, "Unknown: /rg claim " + op + ". Try info, delete, of, deleteall, trust, untrust.");
   }

   private static void remove(EntityPlayerMP p, String[] a) {
      if (a.length < 3) {
         msg(p, "Usage: /rg remove <name>");
         return;
      }

      msg(p, RegionStore.remove(a[2]) ? "Removed " + a[2] + "." : "There is no region called " + a[2] + ".");
   }

   private static void list(EntityPlayerMP p) {
      List<Region> all = RegionStore.all();
      if (all.isEmpty()) {
         msg(p, "No regions yet. /rg wand, then /rg define <name>.");
         return;
      }

      msg(p, all.size() + " region(s):");
      for (int i = 0; i < all.size(); i++) {
         Region r = all.get(i);
         msg(p, "  " + r.name + "  dim " + r.dimension + "  " + r.size()
            + (r.priority != 0 ? "  priority " + r.priority : "")
            + (r.flags.isEmpty() ? "  (no flags)" : "  " + r.flags.size() + " flag(s)"));
      }
   }

   private static void info(EntityPlayerMP p, String[] a) {
      Region r;
      if (a.length > 2) {
         r = RegionStore.get(a[2]);
         if (r == null) {
            msg(p, "There is no region called " + a[2] + ".");
            return;
         }
      } else {
         r = RegionStore.at(p.dimension,
            (int)Math.floor(p.posX), (int)Math.floor(p.posY), (int)Math.floor(p.posZ));
         if (r == null) {
            msg(p, "You are not standing in a region.");
            return;
         }
      }

      msg(p, "Region " + r.name + " -- dimension " + r.dimension + ", priority " + r.priority);
      msg(p, "  " + r.x1 + "," + r.y1 + "," + r.z1 + " to " + r.x2 + "," + r.y2 + "," + r.z2
         + "  (" + r.size() + ", " + r.volume() + " blocks)");
      if (r.flags.isEmpty()) {
         msg(p, "  no flags set -- this region currently does nothing");
      } else {
         for (int i = 0; i < Flags.TOGGLES.length; i++) {
            String v = r.flags.get(Flags.TOGGLES[i]);
            if (v != null) {
               msg(p, "  " + Flags.TOGGLES[i] + " = " + (Flags.isDeny(v) ? "deny" : "allow"));
            }
         }

         for (int i = 0; i < Flags.MESSAGES.length; i++) {
            String v = r.flags.get(Flags.MESSAGES[i]);
            if (v != null) {
               msg(p, "  " + Flags.MESSAGES[i] + " = " + v);
            }
         }
      }

      msg(p, "  members: " + (r.members.isEmpty() ? "none" : String.join(", ", r.members)));
   }

   private static void flag(EntityPlayerMP p, String[] a) {
      if (a.length < 5) {
         msg(p, "Usage: /rg flag <name> <flag> <value>");
         msg(p, "Flags: " + Flags.describe());
         return;
      }

      Region r = RegionStore.get(a[2]);
      if (r == null) {
         msg(p, "There is no region called " + a[2] + ".");
         return;
      }

      String f = a[3].toLowerCase();
      if (!Flags.known(f)) {
         msg(p, "Unknown flag '" + a[3] + "'. Known: " + Flags.describe());
         return;
      }

      StringBuilder v = new StringBuilder();
      for (int i = 4; i < a.length; i++) {
         v.append(i > 4 ? " " : "").append(a[i]);
      }

      String value = v.toString();
      if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("unset")) {
         r.flags.remove(f);
         RegionStore.touch();
         msg(p, "Cleared " + f + " on " + r.name + ".");
         return;
      }

      if (Flags.isToggle(f)) {
         boolean deny = Flags.isDeny(value);
         r.flags.put(f, deny ? "deny" : "allow");
         RegionStore.touch();
         msg(p, r.name + ": " + f + " = " + (deny ? "deny" : "allow"));
         return;
      }

      r.flags.put(f, value);
      RegionStore.touch();
      msg(p, r.name + ": " + f + " = " + value);
   }

   private static void member(EntityPlayerMP p, String[] a, boolean add) {
      if (a.length < 4) {
         msg(p, "Usage: /rg " + (add ? "addmember" : "removemember") + " <name> <player>");
         return;
      }

      Region r = RegionStore.get(a[2]);
      if (r == null) {
         msg(p, "There is no region called " + a[2] + ".");
         return;
      }

      String who = a[3].toLowerCase();
      if (add) {
         if (!r.isMember(who)) {
            r.members.add(who);
         }
      } else {
         r.members.remove(who);
      }

      RegionStore.touch();
      msg(p, (add ? "Added " : "Removed ") + who + (add ? " to " : " from ") + r.name + ".");
   }

   private static void priority(EntityPlayerMP p, String[] a) {
      if (a.length < 4) {
         msg(p, "Usage: /rg priority <name> <number>");
         return;
      }

      Region r = RegionStore.get(a[2]);
      if (r == null) {
         msg(p, "There is no region called " + a[2] + ".");
         return;
      }

      try {
         r.priority = Integer.parseInt(a[3]);
      } catch (NumberFormatException e) {
         msg(p, "'" + a[3] + "' is not a number.");
         return;
      }

      RegionStore.touch();
      msg(p, r.name + " priority is now " + r.priority + ".");
   }

   private static void msg(EntityPlayerMP p, String s) {
      p.addChatMessage(s);
   }
}
