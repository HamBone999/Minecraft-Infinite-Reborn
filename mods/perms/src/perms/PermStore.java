package perms;

import java.io.*;
import java.util.*;

/**
 * Group-based command permissions.
 *
 *   world/perm-groups.tsv   group   comma,separated,commands      ("*" = everything)
 *   world/perm-players.tsv  player  group
 *
 * Operators bypass all of this, always. That is deliberate: it means a broken permission
 * file can never lock you out of your own server.
 */
public final class PermStore {

   private static final Map<String, Set<String>> GROUPS = new LinkedHashMap<String, Set<String>>();
   private static final Map<String, String> PREFIX = new LinkedHashMap<String, String>();
   private static final Map<String, String> NAMECOLOR = new LinkedHashMap<String, String>();
   private static final Map<String, String> PLAYERS = new HashMap<String, String>();
   private static String defaultGroup = "player";
   private static File groupFile, playerFile;

   private PermStore() { }

   public static synchronized void load(File groups, File players) {
      groupFile = groups; playerFile = players;
      GROUPS.clear(); PLAYERS.clear(); PREFIX.clear(); NAMECOLOR.clear();
      if (!groups.exists()) seed(groups);
      readInto(groups, true);
      expandInheritance();
      readInto(players, false);
      System.out.println("[perms] " + GROUPS.size() + " groups, " + PLAYERS.size() + " assignments"
            + ", default group '" + defaultGroup + "'");
   }

   private static void seed(File f) {
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(f));
         w.println("# group\tcomma,separated,commands\tchat-tag");
         w.println("#   \"*\" in the command column means every command.");
         w.println("#   The chat tag is shown to the left of the name: [Owner]HamBone667: hello");
         w.println("#   Colours use &-codes: &b light blue, &c red, &a green, &f white, &e yellow.");
         w.println("#   Column 4 is the colour of the player's own name.");
         w.println("#   Leave the tag empty for no prefix. Operators bypass the command column entirely.");
         w.println("#   \"+group\" inherits every command from that group.");
         w.println("#");
         w.println("# owner gets everything, including the commands that could end the server");
         w.println("# or hand out power: stop, op, deop, perms.");
         w.println("owner\t*\t&b[Owner]\t&b");
         w.println("#");
         w.println("# admin gets moderation, and everything a player has, but NOT");
         w.println("# stop / op / deop / perms.");
         w.println("#   \"whitelist.add\" grants just that subcommand; a bare \"whitelist\" grants all of them.");
         w.println("admin\t+player,kick,ban,unban,whitelist.add,whitelist.list,whitelist.reload,gamemode,give,tp,time,weather,heal,setspawn,setwarp,delwarp,ac,nick\t&c[Admin]\t&c");
         w.println("#");
         w.println("player\thelp,commands,list,seed,home,sethome,delhome,spawn,back,warp,warps,tpa,tpaccept,tpdeny,msg,r,claim,claiminfo,abandon,trust,untrust\t&a[Player]\t&f");
      } catch (IOException e) {
         System.out.println("[perms] could not seed " + f + ": " + e);
      } finally { if (w != null) w.close(); }
   }

   private static void readInto(File f, boolean isGroups) {
      if (!f.exists()) return;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            String[] p = line.split("\t", -1);
            if (p.length < 2) continue;
            if (isGroups) {
               Set<String> cmds = new HashSet<String>();
               String[] c = p[1].split(",");
               for (int i = 0; i < c.length; i++) {
                  String s = c[i].trim().toLowerCase();
                  if (s.length() > 0) cmds.add(s);
               }
               String g = p[0].trim().toLowerCase();
               GROUPS.put(g, cmds);
               // col 3 = chat tag, col 4 = name colour. Both optional.
               PREFIX.put(g, colour(p.length > 2 ? p[2].trim() : ""));
               NAMECOLOR.put(g, colour(p.length > 3 ? p[3].trim() : ""));
            } else {
               PLAYERS.put(p[0].trim().toLowerCase(), p[1].trim().toLowerCase());
            }
         }
      } catch (IOException e) {
         System.out.println("[perms] could not read " + f + ": " + e);
      } finally { if (r != null) try { r.close(); } catch (IOException ignored) { } }
   }

   /** Expands "+group" references. Depth-limited, so a cycle degrades instead of hanging. */
   private static void expandInheritance() {
      for (String g : new ArrayList<String>(GROUPS.keySet())) {
         Set<String> out = new HashSet<String>();
         collect(g, out, 0);
         GROUPS.put(g, out);
      }
   }

   private static void collect(String group, Set<String> out, int depth) {
      if (depth > 8) return;
      Set<String> src = GROUPS.get(group);
      if (src == null) return;
      for (String c : src) {
         if (c.length() > 1 && c.charAt(0) == '+') collect(c.substring(1), out, depth + 1);
         else out.add(c);
      }
   }

   public static synchronized void savePlayers() {
      if (playerFile == null) return;
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(playerFile));
         w.println("# player\tgroup");
         for (Map.Entry<String, String> e : PLAYERS.entrySet()) w.println(e.getKey() + "\t" + e.getValue());
      } catch (IOException e) {
         System.out.println("[perms] could not write " + playerFile + ": " + e);
      } finally { if (w != null) w.close(); }
   }

   public static synchronized String groupOf(String player) {
      String g = PLAYERS.get(player.toLowerCase());
      return g == null ? defaultGroup : g;
   }

   public static synchronized boolean setGroup(String player, String group) {
      if (!GROUPS.containsKey(group.toLowerCase())) return false;
      PLAYERS.put(player.toLowerCase(), group.toLowerCase());
      savePlayers();
      return true;
   }

   /**
    * Colour codes are written as &b in the file and translated to the section sign here.
    * Writing the section sign directly would depend on the file encoding matching, and
    * FileWriter uses the platform default -- this sidesteps that entirely.
    */
   private static String colour(String s) {
      if (s == null || s.length() == 0) return "";
      StringBuilder sb = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '&' && i + 1 < s.length()) {
            char n = Character.toLowerCase(s.charAt(i + 1));
            if ((n >= '0' && n <= '9') || (n >= 'a' && n <= 'f')) { sb.append((char) 167).append(n); i++; continue; }
         }
         sb.append(c);
      }
      return sb.toString();
   }

   /** Colour code applied to the player's own name in chat. Never null. */
   public static synchronized String nameColorOf(String player) {
      String s = NAMECOLOR.get(groupOf(player));
      return s == null ? "" : s;
   }

   /** Chat tag for whichever group the player is in. Never null. */
   public static synchronized String prefixOf(String player) {
      String s = PREFIX.get(groupOf(player));
      return s == null ? "" : s;
   }

   public static synchronized Set<String> groupNames() { return new LinkedHashSet<String>(GROUPS.keySet()); }

   public static synchronized Set<String> commandsOf(String group) {
      Set<String> s = GROUPS.get(group.toLowerCase());
      return s == null ? Collections.<String>emptySet() : s;
   }

   /**
    * Operators bypass everything and effectively hold "*".
    *
    * Consequence worth knowing: vanilla itself requires op for /kick, /ban and /whitelist.
    * So an admin who is opped in order to moderate also clears this gate for /stop, /op,
    * /deop and /perms. The admin group's restrictions only bite for admins who are NOT ops.
    *
    * Failsafe: a missing or unreadable groups file also falls back to plain op rules.
    */
   public static synchronized boolean mayUse(String player, String command, String sub, boolean isOp) {
      if (isOp) return true;
      if (GROUPS.isEmpty()) return isOp;
      String g = groupOf(player);
      if (g.equals("owner")) return true;
      Set<String> allowed = GROUPS.get(g);
      if (allowed == null) return false;
      if (allowed.contains("*")) return true;

      String cmd = command.toLowerCase();
      // a bare "whitelist" grants every subcommand
      if (allowed.contains(cmd)) return true;
      // otherwise a specific "whitelist.add" is required
      if (sub != null && sub.length() > 0 && allowed.contains(cmd + "." + sub.toLowerCase())) return true;
      return false;
   }

   /** True when the group only holds scoped entries for this command, so denial is about the subcommand. */
   public static synchronized boolean hasAnySub(String player, String command) {
      Set<String> allowed = GROUPS.get(groupOf(player));
      if (allowed == null) return false;
      String prefix = command.toLowerCase() + ".";
      for (String a : allowed) if (a.startsWith(prefix)) return true;
      return false;
   }
}
