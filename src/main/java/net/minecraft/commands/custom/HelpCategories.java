package net.minecraft.commands.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups /help by where a command came from.
 *
 * /help walks the Brigadier root and prints every node it finds, in one flat alphabetical run.
 * That is fine for a dozen vanilla commands and useless once four addons and thirty added
 * commands are in there: nothing tells you that /claim and /trust are the same feature, or that
 * the region editor exists at all.
 *
 * Anything that adds commands can name a category for them here. Addons reach this class the
 * same way they reach the rest of the game -- it is in the server jar, which is already on their
 * classpath -- so no addon API change was needed for this.
 *
 * Commands nobody claims stay in the first, unheaded group, which keeps plain vanilla /help
 * looking exactly as it did.
 */
public final class HelpCategories {

   /** command name -> category label. */
   private static final Map<String, String> OWNER = new LinkedHashMap<String, String>();

   /**
    * command name -> sort rank within its category. Lower first, ties broken by name.
    *
    * Exists so a category can read top to bottom in the order someone actually needs it --
    * commands anyone can run, then the ones only operators can -- instead of alphabetically
    * interleaving /home with /killall.
    */
   private static final Map<String, Integer> RANK = new LinkedHashMap<String, Integer>();

   /** category label -> lines that are not Brigadier nodes but should still be listed. */
   private static final Map<String, List<String>> EXTRA = new LinkedHashMap<String, List<String>>();

   /** The order categories were first mentioned, so listings are stable between runs. */
   private static final List<String> ORDER = new ArrayList<String>();

   private HelpCategories() {
   }

   private static void note(String category) {
      if (!ORDER.contains(category)) {
         ORDER.add(category);
      }
   }

   /** Files commands under a category at the default rank. Safe to call more than once. */
   public static synchronized void register(String category, String... commands) {
      register(category, 0, commands);
   }

   /** Files commands under a category, ordered by rank within it. */
   public static synchronized void register(String category, int rank, String... commands) {
      note(category);
      for (int i = 0; i < commands.length; i++) {
         String name = commands[i].toLowerCase();
         OWNER.put(name, category);
         RANK.put(name, Integer.valueOf(rank));
      }
   }

   public static synchronized int rankOf(String command) {
      Integer r = RANK.get(command.toLowerCase());
      return r == null ? 0 : r.intValue();
   }

   /**
    * Adds a line that /help should print under a category even though it is not a registered
    * command node.
    *
    * The // region editor needs this. Its commands are dispatched before Brigadier ever sees
    * the line, and a Brigadier literal cannot sensibly be named "/set", so they are invisible
    * to a listing built from the command tree -- you had to already know they existed.
    */
   public static synchronized void line(String category, String usage) {
      note(category);
      List<String> lines = EXTRA.get(category);
      if (lines == null) {
         lines = new ArrayList<String>();
         EXTRA.put(category, lines);
      }

      if (!lines.contains(usage)) {
         lines.add(usage);
      }
   }

   public static synchronized String categoryOf(String command) {
      return OWNER.get(command.toLowerCase());
   }

   public static synchronized List<String> categories() {
      return new ArrayList<String>(ORDER);
   }

   public static synchronized List<String> extraLines(String category) {
      List<String> lines = EXTRA.get(category);
      return lines == null ? new ArrayList<String>() : new ArrayList<String>(lines);
   }
}
