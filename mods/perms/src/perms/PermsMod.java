package perms;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/** Group permissions. Registers no blocks, items or entities -- server-side only. */
@Mod("perms")
public class PermsMod {
   public PermsMod(ModContext ctx) { ctx.onSetup(this::setup); }

   private void setup() {
      PermStore.load(new File("world", "perm-groups.tsv"), new File("world", "perm-players.tsv"));
      NickStore.load(new File("world", "nicknames.tsv"));
      System.out.println("[perms] ready -- /perms groups");
   }
}
