package net.minecraft.commands.custom;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's two selection corners, and the clipboard they last copied.
 *
 * A selection remembers which dimension it was made in. Without that, walking through a portal
 * and running //set would edit the matching coordinates in a world you cannot see, which is the
 * kind of mistake that has no undo the player can find.
 */
public final class Selection {

   private static final Map<String, Selection> ALL = new HashMap<String, Selection>();

   public boolean has1, has2;
   public int x1, y1, z1, x2, y2, z2;
   public int dimension;

   /** Last //copy: block ids and metadata, plus the offset back to where the player stood. */
   public int[] clipIds;
   public int[] clipMetas;
   public int clipSizeX, clipSizeY, clipSizeZ;
   public int clipOffX, clipOffY, clipOffZ;

   public static Selection of(String owner) {
      Selection s = ALL.get(owner);
      if (s == null) {
         s = new Selection();
         ALL.put(owner, s);
      }

      return s;
   }

   public static void forget(String owner) {
      ALL.remove(owner);
   }

   public boolean complete() {
      return this.has1 && this.has2;
   }

   public void set1(int x, int y, int z, int dim) {
      if (this.dimension != dim) {
         this.has2 = false;
      }

      this.x1 = x; this.y1 = y; this.z1 = z;
      this.has1 = true;
      this.dimension = dim;
   }

   public void set2(int x, int y, int z, int dim) {
      if (this.dimension != dim) {
         this.has1 = false;
      }

      this.x2 = x; this.y2 = y; this.z2 = z;
      this.has2 = true;
      this.dimension = dim;
   }

   /** {@code {minX, minY, minZ, maxX, maxY, maxZ}}, or null if both corners are not set. */
   public int[] box() {
      return this.complete()
         ? Cmd.box(this.x1, this.y1, this.z1, this.x2, this.y2, this.z2)
         : null;
   }
}
