package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import golden.botc_mc.botc_mc.game.map.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.TreeMap;

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

            // /botc map set <mapId>
            root.then(
                literal("map")
                    .then(
                        literal("set")
                            .then(
                                CommandManager.argument("mapId", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        buildMapSuggestions(context.getSource(), builder);
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> executeMapSet(ctx.getSource(), StringArgumentType.getString(ctx, "mapId")))
                            )
                    )
            );

            dispatcher.register(root);
        });
    }

    /**
     * Build map suggestions from data/botc-mc/map_config/*.json resources.
     * Extracts map names and tooltips showing the template they resolve to.
     * @param source command source for accessing the server's resource manager
     * @param builder suggestion builder to populate
     */
    private static void buildMapSuggestions(ServerCommandSource source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        var server = source.getServer();
        var rm = server.getResourceManager();
        java.util.Map<String, Text> suggestions = new TreeMap<>();

        try {
            var found = rm.findResources("map_config", path -> path.toString().endsWith(".json"));
            for (java.util.Map.Entry<Identifier, Resource> entry : found.entrySet()) {
                String name = extractMapName(entry.getKey());
                String tooltip = buildMapTooltip(name, entry.getValue());
                suggestions.put(name, Text.literal(tooltip));
            }
        } catch (Exception ignored) {
            // Silent fail - no suggestions if resource loading fails
        }

        // If no datapack resources found (or to supplement), also check local maps/map_config on disk
        try {
            java.nio.file.Path localDir = java.nio.file.Paths.get("maps", "map_config");
            if (java.nio.file.Files.exists(localDir) && java.nio.file.Files.isDirectory(localDir)) {
                try (var stream = java.nio.file.Files.list(localDir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String name = fileName.substring(0, fileName.length() - 5);
                        try {
                            String json = java.nio.file.Files.readString(p);
                            String template = Map.extractJsonField(json, "template");
                            String tooltip = "Configured map: " + name + (template != null ? " -> template " + template : "");
                            suggestions.putIfAbsent(name, Text.literal(tooltip));
                        } catch (IOException ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {
            // ignore local filesystem errors
        }

        for (java.util.Map.Entry<String, Text> s : suggestions.entrySet()) {
            builder.suggest(s.getKey(), s.getValue());
        }
    }

    /**
     * Extract the simple map name from a resource identifier.
     * Example: botc-mc:map_config/map1.json -> map1
     * @param id resource identifier
     * @return simple map name without path or extension
     */
    private static String extractMapName(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }

    /**
     * Build a tooltip string for a map config by reading its template field.
     * @param name map name
     * @param resource map config resource
     * @return tooltip string
     */
    private static String buildMapTooltip(String name, Resource resource) {
        String tooltipStr = "Configured map: " + name;
        try (InputStream in = resource.getInputStream()) {
            if (in != null) {
                String json = new String(in.readAllBytes());
                String template = Map.extractJsonField(json, "template");
                // legacy 'map_id' fallback removed; only 'template' is supported now
                if (template != null) {
                    tooltipStr = tooltipStr + " -> template " + template;
                }
            }
        } catch (IOException ignored) {
            // Silent fail - return basic tooltip if JSON reading fails
        }
        return tooltipStr;
    }


    /**
     * Execute the /botc map set command.
     * @param source command source
     * @param raw raw map id from user input
     * @return command result (1 for success, 0 for failure)
     */
    private static int executeMapSet(ServerCommandSource source, String raw) {
        String mapId = raw.contains(":") ? raw : ("botc-mc:" + raw);
        try {
            botcSettingsManager.setString("mapId", mapId);
            botcSettingsManager.save();
            source.sendFeedback(() -> Text.literal("Set map to: " + mapId), false);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            source.sendError(Text.literal("Failed to set map: " + ex.getMessage()));
            return 0;
        }
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

        // Show string settings
        for (String key : botcSettingsManager.stringKeys()) {
            String value = botcSettingsManager.getString(key);
            if ("mapId".equals(key)) {
                player.sendMessage(Text.literal(" - " + key + " = " + value + " (use: /botc map set <mapId>)"), false);
            }
        }

        player.sendMessage(Text.literal("-------------------------"), false);
    }

    /** Hidden constructor to prevent instantiation. */
    private botcCommands() {}
}

