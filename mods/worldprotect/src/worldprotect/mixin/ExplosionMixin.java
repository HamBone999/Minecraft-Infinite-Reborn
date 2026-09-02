package worldprotect.mixin;

import net.minecraft.game.world.World;
import net.minecraft.game.world.util.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import worldprotect.Guard;

/**
 * The explosions flag.
 *
 * Only the block damage is suppressed, not the explosion: it still goes off, still throws
 * players about and still hurts. A creeper that walks into spawn and silently does nothing
 * reads as a broken creeper; one that detonates without cratering the plaza reads as a
 * protected area, which is what was asked for.
 *
 * destroyBlocks is a field on Explosion and is read while it works out what to break, so
 * clearing it at the head of explode() is enough.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

   @Shadow public double x;
   @Shadow public double y;
   @Shadow public double z;
   @Shadow public boolean destroyBlocks;

   @Inject(method = "explode", at = @At("HEAD"))
   private void worldprotect$explode(CallbackInfo ci) {
      if (!this.destroyBlocks) {
         return;
      }

      World world = worldprotect$world();
      if (world == null || world.currDim == null) {
         return;
      }

      if (!Guard.mayExplode(world.currDim.dimension,
            (int)Math.floor(this.x), (int)Math.floor(this.y), (int)Math.floor(this.z))) {
         this.destroyBlocks = false;
      }
   }

   /**
    * Explosion keeps its world in a private field whose name is not part of any API, so it is
    * read reflectively rather than shadowed. A rename upstream then means explosions stop being
    * suppressed, not that the server stops exploding.
    */
   private World worldprotect$world() {
      try {
         for (java.lang.reflect.Field f : Explosion.class.getDeclaredFields()) {
            if (World.class.isAssignableFrom(f.getType())) {
               f.setAccessible(true);
               return (World)f.get(this);
            }
         }
      } catch (Throwable ignored) {
      }

      return null;
   }
}
