package landclaim;

import java.util.ArrayList;
import java.util.List;

/** One rectangular claim. Y is not bounded -- a claim is a column, like the golden shovel original. */
public class Claim {
   public final String owner;
   public int x1, z1, x2, z2;
   public final List<String> trusted = new ArrayList<String>();

   public Claim(String owner, int ax, int az, int bx, int bz) {
      this.owner = owner;
      this.x1 = Math.min(ax, bx);
      this.z1 = Math.min(az, bz);
      this.x2 = Math.max(ax, bx);
      this.z2 = Math.max(az, bz);
   }

   public boolean contains(int x, int z) {
      return x >= x1 && x <= x2 && z >= z1 && z <= z2;
   }

   public boolean overlaps(Claim o) {
      return x1 <= o.x2 && x2 >= o.x1 && z1 <= o.z2 && z2 >= o.z1;
   }

   public int area() {
      return (x2 - x1 + 1) * (z2 - z1 + 1);
   }

   /** Owner or anyone they trusted. Case-insensitive: names are stored lowercase everywhere. */
   public boolean mayBuild(String player) {
      String p = player.toLowerCase();
      return owner.equals(p) || trusted.contains(p);
   }

   public String serialize() {
      StringBuilder sb = new StringBuilder();
      sb.append(owner).append('\t').append(x1).append('\t').append(z1)
        .append('\t').append(x2).append('\t').append(z2).append('\t');
      for (int i = 0; i < trusted.size(); i++) {
         if (i > 0) sb.append(',');
         sb.append(trusted.get(i));
      }
      return sb.toString();
   }

   public static Claim deserialize(String line) {
      String[] p = line.split("\t", -1);
      if (p.length < 5) return null;
      try {
         Claim c = new Claim(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                                   Integer.parseInt(p[3]), Integer.parseInt(p[4]));
         if (p.length > 5 && p[5].length() > 0) {
            String[] t = p[5].split(",");
            for (int i = 0; i < t.length; i++) {
               if (t[i].length() > 0) c.trusted.add(t[i]);
            }
         }
         return c;
      } catch (NumberFormatException e) {
         return null;
      }
   }
}
