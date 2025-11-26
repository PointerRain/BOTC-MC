package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import golden.botc_mc.botc_mc.game.map.PodiumGenerator;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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

            // /botc podium create <players> and /botc podium remove
            root.then(
                literal("podium")
                    .then(literal("create")
                        .then(CommandManager.argument("players", IntegerArgumentType.integer(1, 20))
                            .executes(ctx -> {
                                ServerCommandSource src = ctx.getSource();
                                int requested = IntegerArgumentType.getInteger(ctx, "players");
                                ServerWorld world = chooseWorldForSource(src);

                                // create (automatically removes any previous recorded set)
                                List<BlockPos> placed = PodiumGenerator.createAndRecord(world, 0, 64, 0, 24.0, requested);

                                src.sendFeedback(() -> Text.literal("Created " + placed.size() + " podiums (requested=" + requested + ")."), false);
                                if (!placed.isEmpty()) src.sendFeedback(() -> Text.literal("Sample: " + summarizePositions(placed)), false);
                                return 1;
                            })))
                    .then(literal("remove")
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();

                            List<BlockPos> removed = PodiumGenerator.removeRecorded();
                            src.sendFeedback(() -> Text.literal("Removed " + removed.size() + " recorded podium blocks."), false);
                            if (!removed.isEmpty()) src.sendFeedback(() -> Text.literal("Sample removed: " + summarizePositions(removed)), false);
                            return 1;
                        })
                    )
            );

            dispatcher.register(root);
        });
    }

    private static ServerWorld chooseWorldForSource(ServerCommandSource src) {
        try {
            if (src.getEntity() instanceof ServerPlayerEntity) {
                return (ServerWorld) src.getWorld();
            }
        } catch (Throwable ignored) {}
        return src.getServer().getOverworld();
    }

    private static String summarizePositions(List<BlockPos> list) {
        int maxShow = 8;
        return list.stream().limit(maxShow)
            .map(p -> "(" + p.getX() + "," + p.getY() + "," + p.getZ() + ")")
            .collect(Collectors.joining(", ")) + (list.size() > maxShow ? " ..." : "");
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