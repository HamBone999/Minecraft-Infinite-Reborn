package anticheat;

import net.minecraft.game.block.Block;
import net.minecraft.game.block.BlockList;
import java.util.HashSet;
import java.util.Set;

/**
 * Which block ids count as "valuable" for the x-ray heuristic.
 *
 * Coal, iron and copper are deliberately excluded: they are common enough that mining them
 * heavily is normal behaviour, and including them would drown the signal.
 *
 * Built lazily -- BlockList's static initialiser must have run, which it has by the time
 * anyone breaks a block.
 */
public final class Ores {

   private static Set<Integer> ids;

   private Ores() { }

   public static synchronized boolean isValuable(int id) {
      if (ids == null) ids = build();
      return ids.contains(Integer.valueOf(id));
   }

   private static Set<Integer> build() {
      Set<Integer> s = new HashSet<Integer>();
      add(s, BlockList.oreDiamond);
      add(s, BlockList.oreEmerald);
      add(s, BlockList.oreRuby);
      add(s, BlockList.oreSapphire);
      add(s, BlockList.oreAmethyst);
      add(s, BlockList.oreTopaz);
      add(s, BlockList.oreDamascus);
      add(s, BlockList.oreAdamantine);
      add(s, BlockList.oreGold);
      System.out.println("[anticheat] x-ray watches " + s.size() + " ore types");
      return s;
   }

   private static void add(Set<Integer> s, Block b) {
      if (b != null) s.add(Integer.valueOf(b.id));
   }
}
