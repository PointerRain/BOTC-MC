package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Server-side commands to configure BOTC settings in-game via a simple text menu.
 * - /botc settings : shows a list of settings and instructions
 * - /botc set <key> <value> : sets an integer value and saves to disk
 */
public final class botcCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("botc");

            root.then(literal("settings").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendFeedback(() -> Text.literal("This command may only be used by players."), false);
                    return 1;
                }

                showSettingsMenu(player);
                return 1;
            }));

            // /botc set <key> <value>
            root.then(
                literal("set")
                    .then(
                        CommandManager.argument("key", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                // suggest all available keys
                                for (String k : botcSettingsManager.keys()) {
                                    builder.suggest(k);
                                }
                                return builder.buildFuture();
                            })
                            .then(
                                CommandManager.argument("value", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        String key = StringArgumentType.getString(ctx, "key");
                                        int value = IntegerArgumentType.getInteger(ctx, "value");
                                        try {
                                            botcSettingsManager.setInt(key, value);
                                            botcSettingsManager.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("Set " + key + "=" + value), false);
                                            // also send the player a confirmation message with the exact staged value
                                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                                                player.sendMessage(Text.literal("Staged: " + key + " = " + value), false);
                                            }
                                            return 1;
                                        } catch (IllegalArgumentException | IOException ex) {
                                            ctx.getSource().sendError(Text.literal("Failed to set: " + ex.getMessage()));
                                            return 0;
                                        }
                                    })
                            )
                    )
            );

            dispatcher.register(root);
        });
    }

    private static void showSettingsMenu(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("----- BOTC Settings -----"), false);

        for (String key : botcSettingsManager.keys()) {
            int value = botcSettingsManager.getInt(key);
            player.sendMessage(Text.literal(" - " + key + " = " + value + " (use: /botc set " + key + " <value>)"), false);
        }

        player.sendMessage(Text.literal("-------------------------"), false);
    }
}
