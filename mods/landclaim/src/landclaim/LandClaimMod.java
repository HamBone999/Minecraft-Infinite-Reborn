package landclaim;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Golden-shovel land claims. Registers no blocks, items or entities -- it reuses the
 * existing gold shovel -- so the infinite|registry table is unchanged and this is
 * safe to run server-side only.
 */
@Mod("landclaim")
public class LandClaimMod {
   public LandClaimMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      ClaimStore.load(new File("world", "landclaims.tsv"));
      PlaytimeStore.load(new File("world", "playtime.tsv"));
      ClaimLimits.load(new File("world", "landclaim.properties"));
      System.out.println("[landclaim] ready -- right-click two corners with a gold shovel");
   }
}
