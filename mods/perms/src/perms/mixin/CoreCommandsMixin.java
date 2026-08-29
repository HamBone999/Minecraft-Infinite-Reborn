package perms.mixin;

import com.mojang.brigadier.CommandDispatcher;
import perms.HelpListing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CoreCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CoreCommands.class)
public class CoreCommandsMixin {
   @Inject(method = "register", at = @At("TAIL"))
   private static void perms$addToHelp(CommandDispatcher<CommandSourceStack> d, CallbackInfo ci) {
      HelpListing.register(d);
   }
}
