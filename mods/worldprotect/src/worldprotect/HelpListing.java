package worldprotect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.custom.HelpCategories;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Puts the region commands into /help, under their own heading.
 *
 * Only /rg is registered as a command node. The aliases would each get their own line and say
 * the same thing three more times, and the subcommands are not Brigadier nodes at all -- they
 * are parsed out of the argument string -- so they are listed as plain lines instead. Without
 * that, /help would show "/rg" and nothing about what it does.
 */
public final class HelpListing {

   private static final String CATEGORY = "World Protect (op)";

   private HelpListing() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node =
         Commands.literal("rg")
            .requires(new java.util.function.Predicate<CommandSourceStack>() {
               public boolean test(CommandSourceStack src) {
                  return src.hasPermission(CommandSourceStack.LEVEL_OP);
               }
            })
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) {
                  return dispatch(ctx);
               }
            });

      node.then(Commands.argument("args", StringArgumentType.greedyString())
         .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
            public int run(CommandContext<CommandSourceStack> ctx) {
               return dispatch(ctx);
            }
         }));

      d.register(node);

      // Wrapped because HelpCategories lives in the server jar: an older server without it
      // should leave these uncategorised rather than stop the addon loading.
      try {
         HelpCategories.register(CATEGORY, "rg");
         HelpCategories.line(CATEGORY, "/rg wand | /rg pos1 | /rg pos2 -- select an area");
         HelpCategories.line(CATEGORY, "/rg define <name> | /rg redefine <name> | /rg remove <name>");
         HelpCategories.line(CATEGORY, "/rg list | /rg info [name]");
         HelpCategories.line(CATEGORY, "/rg flag <name> <flag> <value> -- " + Flags.describe());
         HelpCategories.line(CATEGORY, "/rg addmember | /rg removemember <name> <player>");
         HelpCategories.line(CATEGORY, "/rg priority <name> <n> | /rg bypass");
         HelpCategories.line(CATEGORY, "/rg claim [delete|of|deleteall|trust|untrust] -- player land claims");
      } catch (Throwable t) {
         System.out.println("[worldprotect] /help categories unavailable on this server build");
      }

      System.out.println("[worldprotect] commands registered for /help");
   }

   private static int dispatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      net.minecraft.game.entity.player.Player pl = src.getPlayer();
      if (!(pl instanceof EntityPlayerMP)) {
         src.sendFailure("That command can only be run by a player.");
         return 0;
      }

      EntityPlayerMP p = (EntityPlayerMP)pl;
      RegionCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
