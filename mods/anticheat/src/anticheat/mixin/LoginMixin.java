package anticheat.mixin;

import anticheat.Alerts;
import anticheat.Checks;
import net.minecraft.server.ServerConfigurationManager;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Delivers missed detections on login, and clears per-player state on logout. */
@Mixin(ServerConfigurationManager.class)
public abstract class LoginMixin {

   @Shadow public net.minecraft.server.MinecraftServer mcServer;

   @Inject(method = "playerLoggedIn", at = @At("TAIL"))
   private void anticheat$deliverMail(EntityPlayerMP p, CallbackInfo ci) {
      Alerts.onLogin(this.mcServer, p);
   }

   @Inject(method = "removePlayersFromList", at = @At("HEAD"))
   private void anticheat$forget(EntityPlayerMP p, CallbackInfo ci) {
      Checks.forget(p.getName());
   }
}
