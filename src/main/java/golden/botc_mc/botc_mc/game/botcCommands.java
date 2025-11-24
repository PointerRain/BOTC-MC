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
 * Command registration helpers for BOTC.
 * <p>
 * Exposes admin-only runtime commands to inspect and mutate BOTC settings.
 * Commands are registered via Fabric's CommandRegistrationCallback and are
 * intended to be executed by server operators only (permission level 4).
 */
public final class botcCommands {
    /** Register the command tree with the dispatcher.
     * <p>
     * Top-level command: {@code /botc}
     * <ul>
     *   <li>{@code /botc settings} : show editable runtime settings to the executing player</li>
     *   <li>{@code /botc set <key> <value>} : sets an integer value and saves to disk
     *       (use {@code /botc set} with a key from {@link botcSettingsManager#keys()})</li>
     * </ul>
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("botc")
                    .requires(src -> src.hasPermissionLevel(4)); // admin-only

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

    /**
     * Send a compact textual menu listing the current editable settings for a player.
     * This is a convenience helper used by the {@code /botc settings} command.
     * @param player player to show the menu to
     */
    private static void showSettingsMenu(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("----- BOTC Settings -----"), false);

        for (String key : botcSettingsManager.keys()) {
            int value = botcSettingsManager.getInt(key);
            player.sendMessage(Text.literal(" - " + key + " = " + value + " (use: /botc set " + key + " <value>)"), false);
        }

        player.sendMessage(Text.literal("-------------------------"), false);
    }

    /** Hidden constructor to prevent instantiation. */
    private botcCommands() {}
}