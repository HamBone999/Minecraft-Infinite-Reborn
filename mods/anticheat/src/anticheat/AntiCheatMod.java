package anticheat;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/** Movement, reach and spam detection. Registers no blocks, items or entities. */
@Mod("anticheat")
public class AntiCheatMod {
   public AntiCheatMod(ModContext ctx) { ctx.onSetup(this::setup); }

   private void setup() {
      Config.load(new File("world", "anticheat.properties"));
      Alerts.load(new File("world", "ac-alerts.tsv"), new File("world", "ac-seen.tsv"));
      System.out.println("[anticheat] ready -- /ac status  (nothing kicks until you enable it)");
   }
}
