package perms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.player.EntityPlayerMP;

/** Registers these commands with Brigadier so /help lists them. Op-gated via requires(). */
public final class HelpListing {

   private HelpListing() { }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      String[] names = { "perms", "nick" };
      for (int i = 0; i < names.length; i++) reg(d, names[i]);

      // Group these under one heading in /help. Wrapped because HelpCategories lives in the
      // server jar: an older server without it should leave these commands uncategorised, not
      // stop the addon loading.
      try {
         net.minecraft.commands.custom.HelpCategories.register("Permissions", "perms", "nick");
      } catch (Throwable t) {
         System.out.println("[perms] /help categories unavailable on this server build");
      }
      System.out.println("[perms] " + names.length + " commands registered for /help");
   }

   private static void reg(CommandDispatcher<CommandSourceStack> d, String name) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node =
         Commands.literal(name)
            .requires(new java.util.function.Predicate<CommandSourceStack>() {
               public boolean test(CommandSourceStack src) { return src.hasPermission(CommandSourceStack.LEVEL_OP); }
            })
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
            });
      node.then(Commands.argument("args", StringArgumentType.greedyString())
         .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
            public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
         }));
      d.register(node);
   }

   private static int dispatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      net.minecraft.game.entity.player.Player pl = src.getPlayer();
      if (!(pl instanceof EntityPlayerMP)) { src.sendFailure("Players only."); return 0; }
      EntityPlayerMP p = (EntityPlayerMP) pl;
      PermCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
