package perms.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import perms.PermCommands;
import perms.PermStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gates every slash command by group.
 *
 * priority 500 (default is 1000) so this @Inject runs BEFORE the moderncmds and landclaim
 * handlers, which are at default priority. A denied command is cancelled here and never
 * reaches them or the vanilla dispatcher.
 *
 * Operators bypass entirely, so a broken permission file cannot lock you out.
 */
@Mixin(value = NetServerHandler.class, priority = 500)
public abstract class GateMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void perms$gate(String command, CallbackInfo ci) {
      String name = this.playerEntity.getName();
      boolean op = this.mcServer.configManager.isOp(name.toLowerCase());

      String line = command.startsWith("/") ? command.substring(1) : command;
      String[] parts = line.trim().split("\\s+");
      String first = parts[0].toLowerCase();
      if (first.length() == 0) return;
      String sub = parts.length > 1 ? parts[1] : "";

      // Gate FIRST, so /perms and /nick obey the same rules as everything else.
      if (!PermStore.mayUse(name, first, sub, op)) {
         if (sub.length() > 0 && PermStore.hasAnySub(name, first)) {
            this.playerEntity.addChatMessage("You may use /" + first + ", but not \"" + sub + "\".");
         } else {
            this.playerEntity.addChatMessage("You do not have permission to use /" + first + ".");
         }
         ci.cancel();
         return;
      }

      if (PermCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
         return;
      }
      // /kick /ban /unban for approved non-ops, before vanilla's own op check refuses them
      if (perms.ModCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
