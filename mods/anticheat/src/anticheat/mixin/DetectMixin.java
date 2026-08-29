package anticheat.mixin;

import anticheat.AcCommands;
import anticheat.Checks;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.network.packet.player.DigPacket;
import net.minecraft.network.packet.player.PlacePacket;
import net.minecraft.network.packet.player.PlayerMovementPacket;
import net.minecraft.network.packet.player.UsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * priority 400 -- ahead of perms (500) and the command plugins (1000), so spam detection
 * sees a command before anything else consumes it.
 *
 * Note the movement hook does NOT cancel. Cancelling a movement packet on this build would
 * fight the server's own position reconciliation and cause exactly the rubber-banding we
 * spent today diagnosing. It records only; a kick is the enforcement path.
 */
@Mixin(value = NetServerHandler.class, priority = 400)
public abstract class DetectMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handlePlayerMovement", at = @At("HEAD"))
   private void anticheat$move(PlayerMovementPacket packet, CallbackInfo ci) {
      NetServerHandler self = (NetServerHandler) (Object) this;
      Checks.airborne(self, this.playerEntity, packet.yPosition, packet.onGround);
      if (!packet.moving) return;
      Checks.movement(self, this.playerEntity,
                      packet.xPosition, packet.yPosition, packet.zPosition);
   }

   @Inject(method = "handleBlockDig", at = @At("HEAD"), cancellable = true)
   private void anticheat$dig(DigPacket packet, CallbackInfo ci) {
      NetServerHandler self = (NetServerHandler) (Object) this;
      // read the block BEFORE it is removed, so x-ray sees what was actually mined
      int id = this.playerEntity.world.getBlockId(packet.xPosition, packet.yPosition, packet.zPosition);
      Checks.broke(self, this.playerEntity, packet.xPosition, packet.yPosition, packet.zPosition, id);
      if (Checks.reach(self, this.playerEntity,
                       packet.xPosition, packet.yPosition, packet.zPosition)) ci.cancel();
   }

   /**
    * direction 255 means "used the item in the air" -- throwing a rock, eating, drinking.
    * NetServerHandler.handlePlace branches on exactly that before it looks at the coordinates,
    * because in that case they are not a block position at all and sit near the origin.
    *
    * Measuring reach against them produced the thousand-block "reach" flags in the log: a player
    * spamming a throwable at (-2764, 139, 327) reads as 2777 blocks from a block at 0,0,0.
    * Nothing is placed either, so it must not count toward the fast-place rate.
    */
   @Inject(method = "handlePlace", at = @At("HEAD"), cancellable = true)
   private void anticheat$place(PlacePacket packet, CallbackInfo ci) {
      if (packet.direction == 255) return;
      NetServerHandler self = (NetServerHandler) (Object) this;
      Checks.placed(self, this.playerEntity);
      if (Checks.reach(self, this.playerEntity,
                       packet.xPosition, packet.yPosition, packet.zPosition)) ci.cancel();
   }

   @Inject(method = "handleUseEntity", at = @At("HEAD"))
   private void anticheat$attack(UsePacket packet, CallbackInfo ci) {
      if (packet.isLeftClick != 0) {
         Checks.attacked((NetServerHandler) (Object) this, this.playerEntity);
      }
   }

   @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
   private void anticheat$chat(ChatPacket packet, CallbackInfo ci) {
      if (Checks.spam((NetServerHandler) (Object) this, this.playerEntity)) ci.cancel();
   }

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void anticheat$cmd(String command, CallbackInfo ci) {
      if (AcCommands.handle(this.playerEntity, this.mcServer, command)) ci.cancel();
   }
}
