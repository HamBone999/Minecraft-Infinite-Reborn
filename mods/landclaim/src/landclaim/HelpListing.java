package landclaim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.player.EntityPlayerMP;

/** Puts the claim commands into /help. See moderncmds.HelpListing for the rationale. */
public final class HelpListing {

   private HelpListing() { }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      register(d, "claim", true);
      register(d, "claiminfo", false);
      register(d, "abandon", false);
      register(d, "trust", true);
      register(d, "untrust", true);

      // Group these under one heading in /help. Wrapped because HelpCategories lives in the
      // server jar: an older server without it should leave these commands uncategorised, not
      // stop the addon loading.
      try {
         net.minecraft.commands.custom.HelpCategories.register("Land Claims", "claim", "claiminfo", "abandon", "trust", "untrust");
      } catch (Throwable t) {
         System.out.println("[landclaim] /help categories unavailable on this server build");
      }
      System.out.println("[landclaim] 5 commands registered for /help");
   }

   private static void register(CommandDispatcher<CommandSourceStack> d, String name, boolean takesArgs) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node =
         Commands.literal(name).executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
            public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
         });
      if (takesArgs) {
         node.then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
            }));
      }
      d.register(node);
   }

   private static int dispatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      net.minecraft.game.entity.player.Player pl = src.getPlayer();
      if (!(pl instanceof EntityPlayerMP)) {
         src.sendFailure("That command can only be run by a player.");
         return 0;
      }
      EntityPlayerMP p = (EntityPlayerMP) pl;
      ClaimCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
