package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import golden.botc_mc.botc_mc.botc;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Server-side commands
 */
public final class botcCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("botc");

            // /botc kill <player>
            root.then(literal("kill")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                        botcActive game = botc.getActiveGameByPlayer(targetPlayer);
                        if (game == null) {
                            context.getSource().sendError(Text.literal("Player is not part of the BOTC game."));
                            return 0;
                        }
                        boolean success = game.kill(targetPlayer);
                        if (!success) {
                            context.getSource().sendError(Text.literal("Player " + targetPlayer.getName().getString() + " is already dead."));
                            return 0;
                        }
                        context.getSource().sendFeedback(() -> Text.literal("Player " + targetPlayer.getName().getString() + " has been killed."), false);
                        return 1;
                    })
                )
            );
            // /botc revive <player>
            root.then(literal("revive")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                        botcActive game = botc.getActiveGameByPlayer(targetPlayer);
                        if (game == null) {
                            context.getSource().sendError(Text.literal("Player is not part of the BOTC game."));
                            return 0;
                        }
                        boolean success = game.revive(targetPlayer);
                        if (!success) {
                            context.getSource().sendError(Text.literal("Player " + targetPlayer.getName().getString() + " is not dead."));
                            return 0;
                        }
                        context.getSource().sendFeedback(() -> Text.literal("Player " + targetPlayer.getName().getString() + " has been revived."), false);
                        return 1;
                    })
                )
            );

            dispatcher.register(root);
        });
    }
}