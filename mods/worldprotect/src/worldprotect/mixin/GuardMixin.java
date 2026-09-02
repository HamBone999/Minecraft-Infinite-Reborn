package worldprotect.mixin;

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

import worldprotect.Guard;
import worldprotect.RegionCommands;
import worldprotect.RegionStore;
import worldprotect.WandItem;

/**
 * Everything that has to be REFUSED rather than merely observed lives here, on
 * NetServerHandler, for the reason landclaim's mixin already records: the block events are
 * either fired after the fact or not cancellable at all.
 *
 * Verified signatures:
 *   handleBlockDig(Lnet/minecraft/network/packet/player/DigPacket;)V
 *   handlePlace(Lnet/minecraft/network/packet/player/PlacePacket;)V
 *   handleUseEntity(Lnet/minecraft/network/packet/player/UsePacket;)V
 *   handleSlashCommand(Ljava/lang/String;)V   (private)
 *   UsePacket { playerEntityId, targetEntity, isLeftClick, offHand }
 */
// priority 500, below the default 1000, so this mixin is applied -- and therefore its HEAD
// callbacks run -- before landclaim's on the same methods.
//
// It matters. landclaim's handlePlace hook selects a claim corner for a gold shovel and then
// cancels, so whichever mixin runs first decides the outcome: if landclaim went first a player
// could mark out a claim inside spawn protection, and if this one goes first the region refuses
// the click and says why. Leaving that to chance would make the bug intermittent, which is the
// worst kind. Region rules are the operator's, so they come first.
@Mixin(value = NetServerHandler.class, priority = 500)
public abstract class GuardMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handleBlockDig", at = @At("HEAD"), cancellable = true)
   private void worldprotect$dig(DigPacket packet, CallbackInfo ci) {
      // Wand first: a left click with the wand sets a corner and must not also break the block.
      if (packet.status == 0 && WandItem.isHeld(this.playerEntity)
         && this.mcServer.configManager.isOp(this.playerEntity.getName().toLowerCase())) {
         RegionStore.setCorner(this.playerEntity.getName(), true,
            packet.xPosition, packet.yPosition, packet.zPosition);
         this.playerEntity.addChatMessage("Corner 1: "
            + packet.xPosition + "," + packet.yPosition + "," + packet.zPosition);
         ci.cancel();
         return;
      }

      if (!Guard.mayBuild(this.playerEntity, this.mcServer, packet.xPosition, packet.zPosition)) {
         ci.cancel();
      }
   }

   @Inject(method = "handlePlace", at = @At("HEAD"), cancellable = true)
   private void worldprotect$place(PlacePacket packet, CallbackInfo ci) {
      if (packet.direction != 255 && WandItem.isHeld(this.playerEntity)
         && this.mcServer.configManager.isOp(this.playerEntity.getName().toLowerCase())) {
         RegionStore.setCorner(this.playerEntity.getName(), false,
            packet.xPosition, packet.yPosition, packet.zPosition);
         this.playerEntity.addChatMessage("Corner 2: "
            + packet.xPosition + "," + packet.yPosition + "," + packet.zPosition);
         ci.cancel();
         return;
      }

      if (packet.direction == 255) {
         return;
      }

      // A right click on a block is either placing something or using what is there. The
      // build flag covers the first, interact the second, and we cannot tell them apart from
      // the packet alone -- so refuse if either says no.
      if (!Guard.mayBuild(this.playerEntity, this.mcServer, packet.xPosition, packet.zPosition)
         || !Guard.mayInteract(this.playerEntity, this.mcServer, packet.xPosition, packet.zPosition)) {
         ci.cancel();
      }
   }

   @Inject(method = "handleUseEntity", at = @At("HEAD"), cancellable = true)
   private void worldprotect$useEntity(UsePacket packet, CallbackInfo ci) {
      if (packet.isLeftClick == 0) {
         return;
      }

      // There is no entity-by-id lookup on World, and only players matter here, so the much
      // smaller player list is both the correct place to look and the cheaper one.
      java.util.List<EntityPlayerMP> players = this.mcServer.configManager.playerEntities;
      for (int i = 0; i < players.size(); i++) {
         EntityPlayerMP victim = players.get(i);
         if (victim.entityId == packet.targetEntity) {
            if (!Guard.mayAttack(this.playerEntity, this.mcServer, victim)) {
               ci.cancel();
            }

            return;
         }
      }
   }

   @Inject(method = "handlePlayerMovement", at = @At("HEAD"))
   private void worldprotect$move(PlayerMovementPacket packet, CallbackInfo ci) {
      if (!packet.moving) {
         return;
      }

      // Not cancellable: refusing the packet would desync the client rather than stop the
      // player. Pushing them back to where they were is what actually keeps them out, and it
      // is the same move the movement checks already make.
      if (!Guard.mayBeAt(this.playerEntity, this.mcServer,
            (int)Math.floor(packet.xPosition), (int)Math.floor(packet.yPosition), (int)Math.floor(packet.zPosition))) {
         this.playerEntity.playerNetServerHandler.teleportTo(
            this.playerEntity.posX, this.playerEntity.posY, this.playerEntity.posZ,
            this.playerEntity.yaw, this.playerEntity.pitch);
      }
   }

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void worldprotect$commands(String command, CallbackInfo ci) {
      if (RegionCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
