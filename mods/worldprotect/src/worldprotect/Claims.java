package worldprotect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A one-way bridge to the golden-shovel land claim addon, so operators can manage player
 * claims from /rg without the two addons being coupled.
 *
 * Every call is reflective and every failure is answered with "not available" rather than an
 * exception. worldprotect must load and work whether landclaim is installed, disabled by
 * renaming its jar, or changed underneath us -- a protection addon that refuses to start
 * because an unrelated addon moved a method is worse than one that quietly does less.
 *
 * Nothing here writes claim geometry. Admins can inspect, untrust, transfer and delete; they
 * cannot silently redraw somebody's claim, which would be indistinguishable from a bug to the
 * player it happened to.
 */
public final class Claims {

   private static Boolean present;
   private static Class<?> storeClass;
   private static Class<?> claimClass;

   private Claims() {
   }

   public static synchronized boolean available() {
      if (present == null) {
         try {
            storeClass = Class.forName("landclaim.ClaimStore");
            claimClass = Class.forName("landclaim.Claim");
            present = Boolean.TRUE;
         } catch (Throwable t) {
            present = Boolean.FALSE;
         }
      }

      return present.booleanValue();
   }

   /** A claim flattened into plain data, so nothing else here handles landclaim types. */
   public static final class Info {
      public final String owner;
      public final int x1, z1, x2, z2;
      public final int area;
      public final List<String> trusted;

      Info(String owner, int x1, int z1, int x2, int z2, int area, List<String> trusted) {
         this.owner = owner;
         this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
         this.area = area;
         this.trusted = trusted;
      }

      public String bounds() {
         return this.x1 + "," + this.z1 + " to " + this.x2 + "," + this.z2;
      }
   }

   private static Info read(Object claim) throws Exception {
      List<String> trusted = new ArrayList<String>();
      Object t = claimClass.getField("trusted").get(claim);
      if (t instanceof List) {
         for (Object o : (List<?>)t) {
            trusted.add(String.valueOf(o));
         }
      }

      return new Info(
         String.valueOf(claimClass.getField("owner").get(claim)),
         claimClass.getField("x1").getInt(claim), claimClass.getField("z1").getInt(claim),
         claimClass.getField("x2").getInt(claim), claimClass.getField("z2").getInt(claim),
         ((Integer)claimClass.getMethod("area").invoke(claim)).intValue(),
         trusted
      );
   }

   private static Object rawAt(int x, int z) throws Exception {
      Method at = storeClass.getMethod("at", int.class, int.class);
      return at.invoke(null, Integer.valueOf(x), Integer.valueOf(z));
   }

   /** The claim covering this column, or null. */
   public static Info at(int x, int z) {
      if (!available()) {
         return null;
      }

      try {
         Object c = rawAt(x, z);
         return c == null ? null : read(c);
      } catch (Throwable t) {
         return null;
      }
   }

   public static List<Info> ownedBy(String player) {
      List<Info> out = new ArrayList<Info>();
      if (!available()) {
         return out;
      }

      try {
         Object list = storeClass.getMethod("ownedBy", String.class).invoke(null, player);
         if (list instanceof List) {
            for (Object c : (List<?>)list) {
               out.add(read(c));
            }
         }
      } catch (Throwable ignored) {
      }

      return out;
   }

   /** Deletes the claim covering this column. Returns its owner, or null if there was none. */
   public static String removeAt(int x, int z) {
      if (!available()) {
         return null;
      }

      try {
         Object c = rawAt(x, z);
         if (c == null) {
            return null;
         }

         String owner = String.valueOf(claimClass.getField("owner").get(c));
         Boolean ok = (Boolean)storeClass.getMethod("remove", claimClass).invoke(null, c);
         return Boolean.TRUE.equals(ok) ? owner : null;
      } catch (Throwable t) {
         return null;
      }
   }

   /** Deletes every claim owned by a player. Returns how many went. */
   public static int removeAllOwnedBy(String player) {
      if (!available()) {
         return 0;
      }

      int n = 0;
      try {
         Object list = storeClass.getMethod("ownedBy", String.class).invoke(null, player);
         if (list instanceof List) {
            // Copied first: remove() mutates the store's own collection.
            List<Object> copy = new ArrayList<Object>((List<?>)list);
            Method remove = storeClass.getMethod("remove", claimClass);
            for (int i = 0; i < copy.size(); i++) {
               if (Boolean.TRUE.equals(remove.invoke(null, copy.get(i)))) {
                  n++;
               }
            }
         }
      } catch (Throwable ignored) {
      }

      return n;
   }

   /** Adds or removes a trusted player on the claim here. Returns false if there is no claim. */
   public static boolean setTrust(int x, int z, String player, boolean add) {
      if (!available()) {
         return false;
      }

      try {
         Object c = rawAt(x, z);
         if (c == null) {
            return false;
         }

         Field f = claimClass.getField("trusted");
         Object t = f.get(c);
         if (!(t instanceof List)) {
            return false;
         }

         @SuppressWarnings("unchecked")
         List<String> trusted = (List<String>)t;
         String who = player.toLowerCase();
         if (add) {
            if (!trusted.contains(who)) {
               trusted.add(who);
            }
         } else {
            trusted.remove(who);
         }

         storeClass.getMethod("save").invoke(null);
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   /**
    * Claims overlapping a box, found by sampling. There is no "every claim" call to ask, and
    * claims are at least a few blocks across, so a grid of probes finds them without reading
    * landclaim's internals. The step is chosen so a large region stays a few hundred probes.
    */
   public static List<Info> overlapping(int x1, int z1, int x2, int z2) {
      List<Info> out = new ArrayList<Info>();
      if (!available()) {
         return out;
      }

      int w = Math.abs(x2 - x1) + 1;
      int d = Math.abs(z2 - z1) + 1;
      int step = Math.max(8, Math.max(w, d) / 24);
      List<String> seen = new ArrayList<String>();

      for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x += step) {
         for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z += step) {
            Info c = at(x, z);
            if (c == null) {
               continue;
            }

            String key = c.owner + "@" + c.bounds();
            if (!seen.contains(key)) {
               seen.add(key);
               out.add(c);
            }
         }
      }

      return out;
   }
}
