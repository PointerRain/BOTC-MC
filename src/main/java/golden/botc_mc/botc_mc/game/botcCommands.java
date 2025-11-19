package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Server-side commands to configure BOTC settings in-game via a simple text menu.
 * - /botc settings : shows a list of settings and instructions
 * - /botc set <key> <value> : sets an integer value and saves to disk
 */
public final class botcCommands {
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

            // /botc map set <id>
            root.then(literal("map")
                .then(literal("set")
                    .then(CommandManager.argument("id", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            // Suggest canonical ids like "botc-mc:test" by scanning map_template/*.nbt
                            Set<String> suggestions = new LinkedHashSet<>();
                            server.getResourceManager()
                                    .findResources("map_template", path -> path.getPath().endsWith(".nbt"))
                                    .keySet()
                                    .forEach(fullId -> {
                                        String ns = fullId.getNamespace();
                                        String p = fullId.getPath(); // e.g., "map_template/test.nbt"
                                        if (p.startsWith("map_template/")) {
                                            p = p.substring("map_template/".length()); // "test.nbt"
                                        }
                                        if (p.endsWith(".nbt")) {
                                            p = p.substring(0, p.length() - 4); // "test"
                                        }
                                        suggestions.add(ns + ":" + p);
                                    });
                            suggestions.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String rawId = StringArgumentType.getString(ctx, "id");

                            // Normalize input: allow users to paste full resource ids like
                            // "botc-mc:map_template/test.nbt" and convert to canonical "botc-mc:test"
                            Identifier parsed;
                            try {
                                parsed = Identifier.of(rawId);
                            } catch (IllegalArgumentException ex) {
                                ctx.getSource().sendError(Text.literal("Invalid map id: " + rawId + ". Use namespace:path, e.g. botc-mc:test"));
                                return 0;
                            }

                            String ns = parsed.getNamespace();
                            String p = parsed.getPath();
                            if (p.startsWith("map_template/")) {
                                p = p.substring("map_template/".length());
                            }
                            if (p.endsWith(".nbt")) {
                                p = p.substring(0, p.length() - 4);
                            }

                            Identifier normalized;
                            try {
                                normalized = Identifier.of(ns, p);
                            } catch (IllegalArgumentException ex) {
                                ctx.getSource().sendError(Text.literal("Invalid normalized id: " + ns + ":" + p));
                                return 0;
                            }

                            botcSettings settings = botcSettings.load();
                            settings.mapId = normalized.toString();
                            try {
                                settings.save();
                            } catch (IOException e) {
                                ctx.getSource().sendError(Text.literal("Failed to save settings: " + e.getMessage()));
                                return 0;
                            }

                            ctx.getSource().sendFeedback(() -> Text.literal("Set BOTC map to " + normalized), false);
                            return 1;
                        }))));
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
