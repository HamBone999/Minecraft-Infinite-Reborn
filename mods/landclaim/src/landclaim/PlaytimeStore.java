package landclaim;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/** Accumulated playtime per player, in minutes. Persisted to world/playtime.tsv. */
public final class PlaytimeStore {

   private static final Map<String, Long> TOTAL = new HashMap<String, Long>();
   private static final Map<String, Long> SESSION_START = new HashMap<String, Long>();
   private static File file;

   private PlaytimeStore() { }

   public static synchronized void load(File f) {
      file = f;
      TOTAL.clear();
      if (!file.exists()) return;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(file));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            String[] p = line.split("\t", -1);
            if (p.length < 2) continue;
            try { TOTAL.put(p[0], Long.valueOf(Long.parseLong(p[1]))); }
            catch (NumberFormatException ignored) { }
         }
         System.out.println("[landclaim] playtime for " + TOTAL.size() + " players");
      } catch (IOException e) {
         System.out.println("[landclaim] could not read " + file + ": " + e);
      } finally {
         if (r != null) try { r.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void save() {
      if (file == null) return;
      BufferedWriter w = null;
      try {
         w = new BufferedWriter(new FileWriter(file));
         w.write("# player\tminutes"); w.newLine();
         for (Map.Entry<String, Long> e : TOTAL.entrySet()) {
            w.write(e.getKey() + "\t" + e.getValue()); w.newLine();
         }
      } catch (IOException e) {
         System.out.println("[landclaim] could not write " + file + ": " + e);
      } finally {
         if (w != null) try { w.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void onLogin(String name) {
      SESSION_START.put(name.toLowerCase(), Long.valueOf(System.currentTimeMillis()));
   }

   /** Banks the session and persists, so a crash costs at most the current session. */
   public static synchronized void onLogout(String name) {
      String k = name.toLowerCase();
      Long start = SESSION_START.remove(k);
      if (start == null) return;
      long mins = (System.currentTimeMillis() - start.longValue()) / 60000L;
      if (mins <= 0) return;
      Long prev = TOTAL.get(k);
      TOTAL.put(k, Long.valueOf((prev == null ? 0L : prev.longValue()) + mins));
      save();
   }

   /** Banked minutes plus whatever the current session has run. */
   public static synchronized long minutes(String name) {
      String k = name.toLowerCase();
      Long prev = TOTAL.get(k);
      long total = prev == null ? 0L : prev.longValue();
      Long start = SESSION_START.get(k);
      if (start != null) total += (System.currentTimeMillis() - start.longValue()) / 60000L;
      return total;
   }
}
