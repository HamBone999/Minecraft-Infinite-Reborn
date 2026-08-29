package landclaim.mixin;

import landclaim.PlaytimeStore;
import net.minecraft.server.ServerConfigurationManager;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Session bookkeeping for the claim-block allowance. */
@Mixin(ServerConfigurationManager.class)
public class PlaytimeMixin {

   @Inject(method = "playerLoggedIn", at = @At("TAIL"))
   private void landclaim$startSession(EntityPlayerMP p, CallbackInfo ci) {
      PlaytimeStore.onLogin(p.getName());
   }

   @Inject(method = "removePlayersFromList", at = @At("HEAD"))
   private void landclaim$endSession(EntityPlayerMP p, CallbackInfo ci) {
      PlaytimeStore.onLogout(p.getName());
   }
}
