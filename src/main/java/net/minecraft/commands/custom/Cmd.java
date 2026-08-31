package net.minecraft.commands.custom;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/** Argument parsing and chat plumbing shared by every command class in this package. */
public final class Cmd {

   /** Returned by {@link #coord} for anything that is not a number. */
   public static final int BAD = Integer.MIN_VALUE;

   private Cmd() {
   }

   public static void msg(EntityPlayerMP p, String s) {
      p.addChatMessage(s);
   }

   public static boolean needOp(EntityPlayerMP p, boolean op) {
      if (op) {
         return false;
      }

      msg(p, "You do not have permission for that.");
      return true;
   }

   public static EntityPlayerMP find(MinecraftServer s, String name) {
      List<EntityPlayerMP> all = s.configManager.playerEntities;
      for (int i = 0; i < all.size(); i++) {
         if (all.get(i).getName().equalsIgnoreCase(name)) {
            return all.get(i);
         }
      }

      return null;
   }

   // ---- arguments ------------------------------------------------------------

   public static int intOr(String s, int fallback) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   /**
    * A coordinate, where {@code ~} is the caller's own position and {@code ~5} is five past
    * it. Returns {@link #BAD} rather than throwing, so a typo is a message and not a stack
    * trace in the server log.
    */
   public static int coord(String tok, double self) {
      if (tok.startsWith("~")) {
         String rest = tok.substring(1);
         int base = (int)Math.floor(self);
         if (rest.length() == 0) {
            return base;
         }

         try {
            return base + Integer.parseInt(rest);
         } catch (NumberFormatException e) {
            return BAD;
         }
      }

      try {
         return Integer.parseInt(tok);
      } catch (NumberFormatException e) {
         return BAD;
      }
   }

   /**
    * Six coordinates starting at {@code from}, returned sorted as
    * {@code {minX, minY, minZ, maxX, maxY, maxZ}} so callers never have to care which corner
    * was typed first. Y is clamped to the world rather than rejected, because asking for
    * "everything down to bedrock" by typing 0 and 127 is the normal way to use these.
    */
   public static int[] region(EntityPlayerMP p, String[] a, int from) {
      if (a.length < from + 6) {
         return null;
      }

      int x1 = coord(a[from], p.posX),     y1 = coord(a[from + 1], p.posY), z1 = coord(a[from + 2], p.posZ);
      int x2 = coord(a[from + 3], p.posX), y2 = coord(a[from + 4], p.posY), z2 = coord(a[from + 5], p.posZ);
      if (x1 == BAD || y1 == BAD || z1 == BAD || x2 == BAD || y2 == BAD || z2 == BAD) {
         return null;
      }

      return box(x1, y1, z1, x2, y2, z2);
   }

   public static int[] box(int x1, int y1, int z1, int x2, int y2, int z2) {
      return new int[] {
         Math.min(x1, x2), Math.max(0, Math.min(y1, y2)), Math.min(z1, z2),
         Math.max(x1, x2), Math.min(127, Math.max(y1, y2)), Math.max(z1, z2)
      };
   }

   public static long volume(int[] r) {
      return (long)(r[3] - r[0] + 1) * (r[4] - r[1] + 1) * (r[5] - r[2] + 1);
   }

   public static String skipNote(Edits.Edit e) {
      return e.skipped() > 0
         ? " (" + e.skipped() + " skipped -- those chunks are not loaded)"
         : "";
   }

   /** Everything from index {@code from} onward, re-joined with single spaces. */
   public static String rest(String[] a, int from) {
      StringBuilder b = new StringBuilder();
      for (int i = from; i < a.length; i++) {
         if (b.length() > 0) {
            b.append(' ');
         }

         b.append(a[i]);
      }

      return b.toString();
   }

   /** Up to {@code limit} names, comma separated, with a count of whatever did not fit. */
   public static String join(List<String> names, int limit) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < names.size() && i < limit; i++) {
         if (i > 0) {
            b.append(", ");
         }

         b.append(names.get(i));
      }

      if (names.size() > limit) {
         b.append(" (+").append(names.size() - limit).append(" more)");
      }

      return b.toString();
   }
}
