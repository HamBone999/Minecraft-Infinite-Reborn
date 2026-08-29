package perms;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Chat nicknames, persisted to world/nicknames.tsv. */
public final class NickStore {

   private static final Map<String, String> NICKS = new HashMap<String, String>();
   private static File file;

   private NickStore() { }

   public static synchronized void load(File f) {
      file = f; NICKS.clear();
      if (!f.exists()) return;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            String[] p = line.split("\t", -1);
            if (p.length >= 2) NICKS.put(p[0].toLowerCase(), p[1]);
         }
         System.out.println("[perms] " + NICKS.size() + " nicknames");
      } catch (IOException e) {
         System.out.println("[perms] could not read " + f + ": " + e);
      } finally { if (r != null) try { r.close(); } catch (IOException ignored) { } }
   }

   public static synchronized void save() {
      if (file == null) return;
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(file));
         w.println("# player\tnickname");
         for (Map.Entry<String, String> e : NICKS.entrySet()) w.println(e.getKey() + "\t" + e.getValue());
      } catch (IOException e) {
         System.out.println("[perms] could not write " + file + ": " + e);
      } finally { if (w != null) w.close(); }
   }

   public static synchronized String get(String player) { return NICKS.get(player.toLowerCase()); }
   public static synchronized void set(String player, String nick) { NICKS.put(player.toLowerCase(), nick); save(); }
   public static synchronized boolean clear(String player) {
      boolean b = NICKS.remove(player.toLowerCase()) != null; if (b) save(); return b;
   }

   /**
    * A nickname must not be another player's real name, and must not be a nickname already
    * taken by someone else. Without this, /nick is an impersonation tool.
    */
   public static synchronized String rejectReason(String player, String nick, Set<String> realNames) {
      if (nick.length() < 2 || nick.length() > 16) return "must be 2 to 16 characters";
      for (int i = 0; i < nick.length(); i++) {
         char c = nick.charAt(i);
         boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                   || c == '_' || c == '-';
         if (!ok) return "letters, digits, _ and - only";
      }
      for (String real : realNames) {
         if (real.equalsIgnoreCase(nick) && !real.equalsIgnoreCase(player)) {
            return "that is another player's real name";
         }
      }
      for (Map.Entry<String, String> e : NICKS.entrySet()) {
         if (e.getValue().equalsIgnoreCase(nick) && !e.getKey().equalsIgnoreCase(player)) {
            return "someone else already uses that nickname";
         }
      }
      return null;
   }
}
