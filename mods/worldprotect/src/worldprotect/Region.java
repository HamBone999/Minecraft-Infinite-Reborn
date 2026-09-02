package worldprotect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One named, admin-defined area of a single dimension.
 *
 * Deliberately not the same thing as a land claim. Claims are owned by a player, bought with
 * playtime and cover a column of the world; a region is placed by an operator, has no owner, is
 * bounded in Y as well as X and Z, and carries flags. Spawn protection is the obvious one.
 */
public final class Region {

   public final String name;
   public int dimension;
   public int x1, y1, z1, x2, y2, z2;

   /** Higher wins where regions overlap. Equal priorities are resolved by the smaller area. */
   public int priority;

   /** Players exempt from this region's denials, besides operators. */
   public final List<String> members = new ArrayList<String>();

   /** flag name -> value, as typed. Absent means "not set here", which defers to the parent. */
   public final Map<String, String> flags = new LinkedHashMap<String, String>();

   public Region(String name, int dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
      this.name = name;
      this.dimension = dimension;
      this.setBounds(x1, y1, z1, x2, y2, z2);
   }

   public void setBounds(int ax, int ay, int az, int bx, int by, int bz) {
      this.x1 = Math.min(ax, bx); this.x2 = Math.max(ax, bx);
      this.y1 = Math.min(ay, by); this.y2 = Math.max(ay, by);
      this.z1 = Math.min(az, bz); this.z2 = Math.max(az, bz);
   }

   public boolean contains(int dim, int x, int y, int z) {
      return dim == this.dimension
         && x >= this.x1 && x <= this.x2
         && y >= this.y1 && y <= this.y2
         && z >= this.z1 && z <= this.z2;
   }

   /** Ignores Y. Used by the block hooks, which know a column but not always a height. */
   public boolean containsColumn(int dim, int x, int z) {
      return dim == this.dimension && x >= this.x1 && x <= this.x2 && z >= this.z1 && z <= this.z2;
   }

   public long volume() {
      return (long)(this.x2 - this.x1 + 1) * (this.y2 - this.y1 + 1) * (this.z2 - this.z1 + 1);
   }

   public boolean isMember(String player) {
      String p = player.toLowerCase();
      for (int i = 0; i < this.members.size(); i++) {
         if (this.members.get(i).equals(p)) {
            return true;
         }
      }

      return false;
   }

   public String size() {
      return (this.x2 - this.x1 + 1) + "x" + (this.y2 - this.y1 + 1) + "x" + (this.z2 - this.z1 + 1);
   }
}
