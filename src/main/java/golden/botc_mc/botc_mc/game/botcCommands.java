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

            // /botc map ... commands for runtime map management
            root.then(
                literal("map")
                    .then(literal("list").executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        golden.botc_mc.botc_mc.game.map.MapManager mgr = new golden.botc_mc.botc_mc.game.map.MapManager();
                        mgr.discoverRuntimeMaps();
                        StringBuilder sb = new StringBuilder();
                        sb.append("Available maps:\n");
                        for (var info : mgr.listInfos()) {
                            sb.append(" - ").append(info.id).append(" : ").append(info.name);
                            if (info.authors != null && !info.authors.isEmpty()) {
                                sb.append(" (by ").append(String.join(", ", info.authors)).append(")");
                            }
                            if (info.description != null && !info.description.isEmpty()) {
                                sb.append(" - ").append(info.description);
                            }
                            sb.append("\n");
                        }
                        src.sendFeedback(() -> Text.literal(sb.toString()), false);
                        return 1;
                    }))
                    .then(literal("set").then(
                        CommandManager.argument("id", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                // Build suggestions from discovered packaged and runtime maps
                                golden.botc_mc.botc_mc.game.map.MapManager mgr = new golden.botc_mc.botc_mc.game.map.MapManager();
                                mgr.discoverRuntimeMaps();
                                for (String mid : mgr.listIds()) {
                                    builder.suggest(mid);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                // normalize unqualified ids
                                if (!id.contains(":")) id = "botc:" + id;
                                final String idFinal = id;
                                golden.botc_mc.botc_mc.game.map.MapManager mgr = new golden.botc_mc.botc_mc.game.map.MapManager();
                                mgr.discoverRuntimeMaps();
                                if (mgr.listIds().contains(idFinal)) {
                                    // Persist via settings manager
                                    golden.botc_mc.botc_mc.game.botcSettings s = botcSettingsManager.get();
                                    s.mapId = idFinal;
                                    try { botcSettingsManager.save(); } catch (IOException e) { /* best-effort */ }
                                    ctx.getSource().sendFeedback(() -> Text.literal("Set default map to " + idFinal), false);
                                    return 1;
                                } else {
                                    ctx.getSource().sendError(Text.literal("Unknown map id: " + idFinal));
                                    return 0;
                                }
                            })
                    ))
            );

            // /botc debug - lightweight diagnostic for mod/game registration
            root.then(literal("debug").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                boolean registered = (golden.botc_mc.botc_mc.botc.TYPE != null);
                src.sendFeedback(() -> Text.literal("botc GameType registered: " + registered), false);
                return 1;
            }));

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
