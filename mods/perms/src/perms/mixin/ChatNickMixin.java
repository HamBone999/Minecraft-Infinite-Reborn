package perms.mixin;

import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import perms.NickStore;
import perms.PermStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Formats chat as  [Tag]Name: message  using the speaker's group tag, and their nickname
 * if one is set.
 *
 * Chat only. The name above the head and the player list come from the entity identity and
 * the login handshake; changing those would mean lying to the client about who is who, which
 * is exactly what the infinite|registry sync exists to prevent.
 *
 * Anything vanilla would reject -- too long, illegal characters, a command -- is handed back
 * to vanilla untouched so its kick guards still apply.
 *
 * priority 600: after anticheat (400), so a spam-cancelled message never reaches this.
 */
@Mixin(value = NetServerHandler.class, priority = 600)
public abstract class ChatNickMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   private static String strip(String s) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
         if (s.charAt(i) == (char) 167) { i++; continue; }
         sb.append(s.charAt(i));
      }
      return sb.toString();
   }

   @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
   private void perms$formatChat(ChatPacket packet, CallbackInfo ci) {
      if (packet.message == null) return;
      String msg = packet.message.trim();
      if (msg.length() == 0 || msg.charAt(0) == '/') return;   // command: vanilla path
      if (msg.length() > 100) return;                          // let vanilla kick
      for (int i = 0; i < msg.length(); i++) {
         char c = msg.charAt(i);
         if (c < 32 || c == 127 || c == 167) return;           // ditto
      }

      String real = this.playerEntity.getName();
      String nick = NickStore.get(real);
      String shown = nick == null ? real : nick;
      String tag = PermStore.prefixOf(real);
      String nameCol = PermStore.nameColorOf(real);
      String white = String.valueOf((char) 167) + "f";

      this.mcServer.configManager.sendPacketToAll(
            new ChatPacket(tag + nameCol + shown + white + ": " + msg));
      // console keeps the real name and drops the colour codes
      System.out.println(strip(tag) + shown + (nick == null ? "" : " (" + real + ")") + ": " + msg);
      ci.cancel();
   }
}
