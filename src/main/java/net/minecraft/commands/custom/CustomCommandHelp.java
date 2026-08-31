package net.minecraft.commands.custom;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Registers the extra commands with Brigadier so /help lists them.
 *
 * HelpCommand walks the dispatcher root and filters with CommandNode.canUse, so a registered
 * node appears automatically and requires(...) hides op-only entries from ordinary players.
 * Execution still goes through handleSlashCommand, which runs first; these executors are the
 * fallback and call the same handler, so the two routes cannot disagree.
 */
public final class CustomCommandHelp {

   private CustomCommandHelp() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      String[] plain = { "commands", "home", "sethome", "delhome", "spawn", "back", "list", "seed", "warps", "tpaccept", "tpdeny", "r" };
      String[] plainArgs = { "warp", "tpa", "msg" };
      String[] opPlain = { "setspawn", "heal", "ascend", "descend", "light" };
      String[] opArgs = {
         "setwarp", "delwarp", "time", "weather", "dimension", "dim",
         "setblock", "fill", "clone", "summon", "kill", "clear", "effect", "enchant",
         "xp", "say", "spawnpoint", "god", "killall", "up"
      };

      // The // commands are not registered here. A Brigadier literal cannot usefully be
      // named "/set", and they are dispatched by handleSlashCommand before Brigadier sees
      // the line anyway. /commands lists them instead.

      for (int i = 0; i < plain.length; i++) reg(d, plain[i], false, false);
      for (int i = 0; i < plainArgs.length; i++) reg(d, plainArgs[i], true, false);
      for (int i = 0; i < opPlain.length; i++) reg(d, opPlain[i], false, true);
      for (int i = 0; i < opArgs.length; i++) reg(d, opArgs[i], true, true);

      System.out.println("[commands] " + (plain.length + plainArgs.length + opPlain.length + opArgs.length) + " commands registered for /help");
   }

   private static void reg(CommandDispatcher<CommandSourceStack> d, String name, boolean takesArgs, boolean opOnly) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);
      if (opOnly) {
         node = node.requires(
            new java.util.function.Predicate<CommandSourceStack>() {
               public boolean test(CommandSourceStack src) {
                  return src.hasPermission(CommandSourceStack.LEVEL_OP);
               }
            }
         );
      }

      node = node.executes(
         new com.mojang.brigadier.Command<CommandSourceStack>() {
            public int run(CommandContext<CommandSourceStack> ctx) {
               return dispatch(ctx);
            }
         }
      );

      if (takesArgs) {
         node.then(
            Commands.argument("args", StringArgumentType.greedyString())
               .executes(
                  new com.mojang.brigadier.Command<CommandSourceStack>() {
                     public int run(CommandContext<CommandSourceStack> ctx) {
                        return dispatch(ctx);
                     }
                  }
               )
         );
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

      EntityPlayerMP p = (EntityPlayerMP)pl;
      ServerCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
