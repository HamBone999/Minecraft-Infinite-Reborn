package anticheat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;
import java.io.*;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Staff alerting, live and by mail.
 *
 * Online staff get a detection the moment it happens. Staff who are offline get it when they
 * next log in, so nothing is missed overnight.
 *
 * Rather than enumerating offline staff, every alert gets a sequence number and each player
 * records the last one they saw. On login you receive everything newer than your mark. That
 * copes with rank changes for free: promote someone and they simply start receiving alerts,
 * with no backfill of things they were never staff for.
 *
 * Staff is resolved through perms by REFLECTION, not a direct import. The two are separate
 * jars with no declared dependency, so a hard reference would turn "perms removed" into a
 * NoClassDefFoundError at the worst possible moment. Without perms, ops are staff.
 */
public final class Alerts {

   private static final List<String[]> ALERTS = new ArrayList<String[]>();   // {seq, millis, text}
   private static final Map<String, Long> SEEN = new HashMap<String, Long>();
   private static long nextSeq = 1L;
   private static File alertFile, seenFile;
   private static final int KEEP = 200;
   private static final int MAX_ON_LOGIN = 15;

   private static Method groupOf;
   private static boolean permsChecked = false;

   private Alerts() { }

   // ---- staff resolution -------------------------------------------------

   /** Ops, plus the perms owner/admin groups when perms is installed. */
   public static boolean isStaff(MinecraftServer s, String name) {
      if (s.configManager.isOp(name.toLowerCase())) return true;
      if (!permsChecked) {
         permsChecked = true;
         try {
            Class<?> c = Class.forName("perms.PermStore");
            groupOf = c.getMethod("groupOf", String.class);
            System.out.println("[anticheat] alerts will use perms groups for staff");
         } catch (Throwable t) {
            System.out.println("[anticheat] perms not present, alerts go to ops only");
         }
      }
      if (groupOf == null) return false;
      try {
         Object g = groupOf.invoke(null, name);
         if (g == null) return false;
         String grp = g.toString();
         return grp.equals("owner") || grp.equals("admin");
      } catch (Throwable t) {
         return false;
      }
   }

   // ---- persistence ------------------------------------------------------

   public static synchronized void load(File alerts, File seen) {
      alertFile = alerts; seenFile = seen;
      ALERTS.clear(); SEEN.clear();
      for (String line : readLines(alerts)) {
         String[] p = line.split("\t", 3);
         if (p.length < 3) continue;
         ALERTS.add(p);
         try { nextSeq = Math.max(nextSeq, Long.parseLong(p[0]) + 1L); } catch (NumberFormatException ignored) { }
      }
      for (String line : readLines(seen)) {
         String[] p = line.split("\t", 2);
         if (p.length < 2) continue;
         try { SEEN.put(p[0].toLowerCase(), Long.valueOf(Long.parseLong(p[1]))); }
         catch (NumberFormatException ignored) { }
      }
      System.out.println("[anticheat] " + ALERTS.size() + " stored alerts, " + SEEN.size() + " read marks");
   }

   private static List<String> readLines(File f) {
      List<String> out = new ArrayList<String>();
      if (f == null || !f.exists()) return out;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0 && line.charAt(0) != '#') out.add(line);
         }
      } catch (IOException e) {
         System.out.println("[anticheat] could not read " + f + ": " + e);
      } finally { if (r != null) try { r.close(); } catch (IOException ignored) { } }
      return out;
   }

   private static void save() {
      write(alertFile, "# seq\tmillis\ttext", ALERTS, true);
      List<String[]> marks = new ArrayList<String[]>();
      for (Map.Entry<String, Long> e : SEEN.entrySet()) marks.add(new String[] { e.getKey(), String.valueOf(e.getValue()) });
      write(seenFile, "# player\tlast-seen-seq", marks, false);
   }

   private static void write(File f, String header, List<String[]> rows, boolean three) {
      if (f == null) return;
      PrintWriter w = null;
      try {
         w = new PrintWriter(new FileWriter(f));
         w.println(header);
         for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            w.println(three ? (r[0] + "\t" + r[1] + "\t" + r[2]) : (r[0] + "\t" + r[1]));
         }
      } catch (IOException e) {
         System.out.println("[anticheat] could not write " + f + ": " + e);
      } finally { if (w != null) w.close(); }
   }

   // ---- raising and delivery ---------------------------------------------

   /** Records a detection, shows it to online staff now, and leaves it as mail for the rest. */
   public static synchronized void raise(MinecraftServer s, String text) {
      long seq = nextSeq++;
      ALERTS.add(new String[] { String.valueOf(seq), String.valueOf(System.currentTimeMillis()), text });
      while (ALERTS.size() > KEEP) ALERTS.remove(0);

      List<?> online = s.configManager.playerEntities;
      for (int i = 0; i < online.size(); i++) {
         EntityPlayerMP q = (EntityPlayerMP) online.get(i);
         if (!isStaff(s, q.getName())) continue;
         q.addChatMessage(colour("&c[AC]&f ") + text);
         SEEN.put(q.getName().toLowerCase(), Long.valueOf(seq));   // they saw it live
      }
      save();
   }

   /** Delivers anything a staff member missed while they were away. */
   public static synchronized void onLogin(MinecraftServer s, EntityPlayerMP p) {
      if (!isStaff(s, p.getName())) return;
      String k = p.getName().toLowerCase();
      Long mark = SEEN.get(k);
      long last = mark == null ? 0L : mark.longValue();

      List<String[]> missed = new ArrayList<String[]>();
      for (int i = 0; i < ALERTS.size(); i++) {
         try { if (Long.parseLong(ALERTS.get(i)[0]) > last) missed.add(ALERTS.get(i)); }
         catch (NumberFormatException ignored) { }
      }
      if (missed.isEmpty()) {
         SEEN.put(k, Long.valueOf(nextSeq - 1L)); save();
         return;
      }

      SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
      p.addChatMessage(colour("&c[AC]&f ") + missed.size() + " detection"
            + (missed.size() == 1 ? "" : "s") + " while you were away:");
      int from = Math.max(0, missed.size() - MAX_ON_LOGIN);
      for (int i = from; i < missed.size(); i++) {
         String[] a = missed.get(i);
         String when = fmt.format(new Date(Long.parseLong(a[1])));
         p.addChatMessage(colour("&7  " + when + " &f") + a[2]);
      }
      if (from > 0) p.addChatMessage(colour("&7  ...and " + from + " older. /ac for the full list."));

      SEEN.put(k, Long.valueOf(nextSeq - 1L));
      save();
   }

   private static String colour(String s) {
      StringBuilder sb = new StringBuilder();
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

   public static synchronized List<String> recentText(int n) {
      SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
      List<String> out = new ArrayList<String>();
      int from = Math.max(0, ALERTS.size() - n);
      for (int i = from; i < ALERTS.size(); i++) {
         String[] a = ALERTS.get(i);
         out.add(fmt.format(new Date(Long.parseLong(a[1]))) + "  " + a[2]);
      }
      return out;
   }
}
