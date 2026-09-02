package worldprotect;

import java.io.File;

import infinite.api.Mod;
import infinite.api.ModContext;
import infinite.api.event.EntitySpawnEvent;
import infinite.api.event.EventBus;

/**
 * Admin region protection: named areas with flags, for spawn protection and anywhere else that
 * should not be at the mercy of whoever wanders in.
 *
 * Registers no blocks, items or entities -- the selection wand is an existing item -- so the
 * registry table is unchanged and this is safe to run server-side only.
 */
@Mod("worldprotect")
public class WorldProtectMod {

   public WorldProtectMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      RegionStore.load(new File("world", "regions.tsv"));

      // The mobs flag. EntitySpawnEvent is the one event that genuinely prevents a spawn, which
      // is why this flag is enforced here and the rest live on NetServerHandler.
      EventBus.get().subscribe(EntitySpawnEvent.class, EventBus.Priority.HIGH, "worldprotect", event -> {
         if (event.isCancelled()) {
            return;
         }

         Object e = event.entity;
         if (!(e instanceof net.minecraft.game.entity.mob.Mob)
            || e instanceof net.minecraft.game.entity.player.Player) {
            return;
         }

         // Entity carries no dimension of its own -- the world it is spawning into does.
         if (!(event.world instanceof net.minecraft.game.world.World)) {
            return;
         }

         net.minecraft.game.world.World w = (net.minecraft.game.world.World)event.world;
         if (w.currDim == null) {
            return;
         }

         net.minecraft.game.entity.Entity ent = (net.minecraft.game.entity.Entity)e;
         if (!Guard.maySpawn(w.currDim.dimension,
               (int)Math.floor(ent.posX), (int)Math.floor(ent.posY), (int)Math.floor(ent.posZ))) {
            event.setCancelled(true);
         }
      });

      // Deliberately does NOT name the wand here. Doing so resolves it from ItemList, and
      // touching a game registry during mod setup runs its static initialiser far too early:
      // ItemList pulls in BlockList, BlockList fails at that point in the loader's lifecycle,
      // and a class whose clinit has failed once can never initialise again. The server then
      // dies later, in world load, pointing at BlockList and not at the mod that poisoned it.
      System.out.println("[worldprotect] ready -- /rg help");
      verifyMixins();
   }

   /**
    * Force-loads the mixin targets so a bad injector fails here rather than in front of a
    * player.
    *
    * The classes this mod injects into are not loaded during startup -- NetServerHandler waits
    * for the first connection, Explosion for the first creeper -- and mixins apply on class
    * load. A clean boot therefore says nothing at all about whether the injections took. Run
    * with -Dworldprotect.verify=true to find out before shipping.
    *
    * Off by default: force-loading game classes during setup is exactly what must not happen
    * on a live server.
    */
   private void verifyMixins() {
      if (System.getProperty("worldprotect.verify") == null) {
         return;
      }

      String[] targets = {
         "net.minecraft.server.network.NetServerHandler",
         "net.minecraft.game.world.util.Explosion"
      };

      // NOT the wand. Resolving it reads ItemList, and that is the very thing that must not
      // happen during setup -- proved by doing it here once and watching the server die in
      // world load. The wand resolves the first time an operator runs /rg wand, by which point
      // the registries are up.
      System.out.println("[worldprotect] verify: land claims " + (Claims.available() ? "detected" : "not installed"));

      for (int i = 0; i < targets.length; i++) {
         try {
            Class.forName(targets[i]);
            System.out.println("[worldprotect] verify: mixins applied to " + targets[i]);
         } catch (Throwable t) {
            System.out.println("[worldprotect] verify: FAILED for " + targets[i] + " -- " + t);
         }
      }
   }
}
