package worldprotect;

/**
 * The flags a region can carry, and how their values are read.
 *
 * Kept small on purpose: every flag here is enforced somewhere. A flag that is accepted by the
 * command but silently does nothing is worse than no flag at all, because the person who set it
 * believes the area is protected.
 */
public final class Flags {

   public static final String BUILD = "build";
   public static final String INTERACT = "interact";
   public static final String PVP = "pvp";
   public static final String MOBS = "mobs";
   public static final String EXPLOSIONS = "explosions";
   public static final String ENTRY = "entry";
   public static final String GREETING = "greeting";
   public static final String FAREWELL = "farewell";

   /** The allow/deny flags, in the order /rg info prints them. */
   public static final String[] TOGGLES = { BUILD, INTERACT, PVP, MOBS, EXPLOSIONS, ENTRY };

   /** The free-text flags. */
   public static final String[] MESSAGES = { GREETING, FAREWELL };

   private Flags() {
   }

   public static boolean isToggle(String flag) {
      for (int i = 0; i < TOGGLES.length; i++) {
         if (TOGGLES[i].equals(flag)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isMessage(String flag) {
      for (int i = 0; i < MESSAGES.length; i++) {
         if (MESSAGES[i].equals(flag)) {
            return true;
         }
      }

      return false;
   }

   public static boolean known(String flag) {
      return isToggle(flag) || isMessage(flag);
   }

   /** "deny"/"false"/"no"/"off" all mean deny. Anything else set means allow. */
   public static boolean isDeny(String value) {
      if (value == null) {
         return false;
      }

      String v = value.trim().toLowerCase();
      return v.equals("deny") || v.equals("false") || v.equals("no") || v.equals("off") || v.equals("0");
   }

   public static String describe() {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < TOGGLES.length; i++) {
         b.append(i > 0 ? ", " : "").append(TOGGLES[i]);
      }

      for (int i = 0; i < MESSAGES.length; i++) {
         b.append(", ").append(MESSAGES[i]);
      }

      return b.toString();
   }
}
