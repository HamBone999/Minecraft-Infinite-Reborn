package landclaim.mixin;

import landclaim.Claim;
import landclaim.ClaimCommands;
import landclaim.ClaimLimits;
import landclaim.ClaimStore;
import net.minecraft.game.item.ItemList;
import net.minecraft.network.packet.player.DigPacket;
import net.minecraft.network.packet.player.PlacePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * All three hooks live on NetServerHandler because that is the only place a block
 * change can still be REFUSED. The event API cannot do this job: MODDING.md says
 * BlockBreakEvent is "flag only, the break already happened" and BlockPlaceEvent is
 * not cancellable at all.
 *
 * Verified signatures:
 *   NetServerHandler.handleBlockDig(Lnet/minecraft/network/packet/player/DigPacket;)V
 *   NetServerHandler.handlePlace(Lnet/minecraft/network/packet/player/PlacePacket;)V
 *   NetServerHandler.handleSlashCommand(Ljava/lang/String;)V   (private)
 *   DigPacket   { xPosition, yPosition, zPosition, face, status }
 *   PlacePacket { xPosition, yPosition, zPosition, direction, itemStack }
 */
@Mixin(NetServerHandler.class)
public abstract class ProtectionMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   private boolean landclaim$mayEdit(int x, int z) {
      Claim c = ClaimStore.at(x, z);
      if (c == null) return true;
      String name = this.playerEntity.getName();
      if (c.mayBuild(name)) return true;
      if (this.mcServer.configManager.isOp(name.toLowerCase())) return true;
      this.playerEntity.addChatMessage("This land belongs to " + c.owner + ".");
      return false;
   }

   @Inject(method = "handleBlockDig", at = @At("HEAD"), cancellable = true)
   private void landclaim$guardDig(DigPacket packet, CallbackInfo ci) {
      if (!landclaim$mayEdit(packet.xPosition, packet.zPosition)) {
         ci.cancel();
      }
   }

   @Inject(method = "handlePlace", at = @At("HEAD"), cancellable = true)
   private void landclaim$guardPlace(PlacePacket packet, CallbackInfo ci) {
      boolean goldShovel = packet.itemStack != null
                        && packet.itemStack.itemID == ItemList.shovelGold.id;

      if (goldShovel && packet.xPosition != -1) {
         landclaim$select(packet.xPosition, packet.zPosition);
         ci.cancel();
         return;
      }
      if (!landclaim$mayEdit(packet.xPosition, packet.zPosition)) {
         ci.cancel();
      }
   }

   private void landclaim$select(int x, int z) {
      String name = this.playerEntity.getName();
      int[] first = ClaimStore.getCorner1(name);
      if (first == null) {
         ClaimStore.setCorner1(name, x, z);
         this.playerEntity.addChatMessage("Corner 1 set at " + x + "," + z + ". Right-click the opposite corner.");
         return;
      }
      Claim c = new Claim(name.toLowerCase(), first[0], first[1], x, z);
      ClaimStore.clearCorner(name);

      // Ops are unlimited. Everyone else spends from an allowance that grows with playtime.
      if (!this.mcServer.configManager.isOp(name.toLowerCase())) {
         long remaining = ClaimLimits.remaining(name);
         if (c.area() > remaining) {
            this.playerEntity.addChatMessage("That claim is " + c.area() + " blocks but you only have "
                  + remaining + " left.");
            this.playerEntity.addChatMessage("You have " + ClaimLimits.budgetFor(name) + " total, "
                  + ClaimLimits.usedBy(name) + " already claimed. More with playtime -- see /claim blocks.");
            return;
         }
      }

      String err = ClaimStore.add(c);
      if (err != null) {
         this.playerEntity.addChatMessage("Cannot claim: " + err);
         return;
      }
      this.playerEntity.addChatMessage("Claimed " + c.area() + " blocks, "
            + c.x1 + "," + c.z1 + " to " + c.x2 + "," + c.z2 + ".");
   }

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void landclaim$commands(String command, CallbackInfo ci) {
      if (ClaimCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
