package golden.botc_mc.botc_mc.game;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.exceptions.InvalidAlignmentException;
import golden.botc_mc.botc_mc.game.exceptions.InvalidSeatException;
import golden.botc_mc.botc_mc.game.gui.GrimoireGUI;
import golden.botc_mc.botc_mc.game.map.Map;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.resource.Resource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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


            // Get running games info
            root.then(literal("games").executes(ctx -> {
                List<botcActive> activeGames = botc.getActiveGames();
                if (activeGames.isEmpty()) {
                    ctx.getSource().sendFeedback(() -> Text.literal("No active BOTC games."), false);
                    return 0;
                }
                ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(activeGames.getFirst())), false);
                return activeGames.size();
            }));


            // Add a seat
            root.then(literal("seat").then(
                    literal("count").then(
                            CommandManager.argument("count", IntegerArgumentType.integer(5, 20))
                                    .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                        if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("You are not in an active " +
                                                            "BOTC game."));
                                                    return 0;
                                                }
                                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                                activeGame.getSeatManager().setPlayerCount(count);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Updated the seat " +
                                                        "count to " + count), true);
                                                return 1;
                                            }
                                    )
                    )
            ));

            // Clear a seat
            root.then(literal("seat").then(
                    literal("clear").then(
                            CommandManager.argument("seat", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                        if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("You are not in an active " +
                                                            "BOTC game."));
                                                    return 0;
                                                }
                                                int seatNumber = IntegerArgumentType.getInteger(ctx, "seat");
                                                Seat seat = activeGame.getSeatManager().getSeatFromNumber(seatNumber);
                                                if (seat == null) {
                                                    ctx.getSource().sendError(Text.literal("Seat " + seatNumber + " " +
                                                            "does not exist."));
                                                    return 0;
                                                }
                                                seat.clearCharacter();
                                                seat.removePlayerEntity();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Cleared seat " + seatNumber + "."), true);
                                                return 1;
                                            }
                                    )
                    )
            ));
            root.then(literal("seat").then(
                    literal("sit").then(
                            CommandManager.argument("seat", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                        if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("You are not in an active " +
                                                            "BOTC game."));
                                                    return 0;
                                                }
                                                int seatNumber = IntegerArgumentType.getInteger(ctx, "seat");
                                        Seat seat;
                                        try {
                                            seat = activeGame.getSeatManager().assignPlayerToSeat(player,
                                                    seatNumber);
                                        } catch (IllegalArgumentException | InvalidSeatException ex) {
                                            ctx.getSource().sendError(Text.literal("Failed to assign seat " + seatNumber + " to player " + player.getName().getString() + ": " + ex.getMessage()));
                                            return 0;
                                        }
                                        if (seat == null) {
                                            ctx.getSource().sendError(Text.literal("Failed to assign seat " + seatNumber + " to player " + player.getName().getString() + "."));
                                            return 0;
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("Assigned seat " + seatNumber + " to player " + player.getName().getString() + "."), true);
                                        return 1;
                                    }
                            )
                    )
            ));


            // Get botcCharacter info for a player
            root.then(
                    literal("character").then(
                            literal("get").then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                Seat seat = botc.getSeatFromPlayer(player);
                                                if (seat == null) {
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Player " + player.getName().getString() + " has no seat assigned."), false);
                                                    return 1;
                                                }
                                                ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(seat))
                                                        , false);
                                                return 1;
                                            })
                            )
                    ));


            // Step up to storyteller (only if no storyteller assigned)
            root.then(literal("step-up").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                if (activeGame == null || player == null) {
                    ctx.getSource().sendError(Text.literal("You are not in an active BOTC game."));
                    return 0;
                }
                try {
                    activeGame.getSeatManager().stepUpToStoryteller(player);
                    ctx.getSource().sendFeedback(() -> Text.literal("Stepped up to storyteller seat."), true);
                    return 1;
                } catch (InvalidSeatException ex) {
                    ctx.getSource().sendError(Text.literal(ex.getMessage()));
                    return 0;
                }
            }));

            // Step down from storyteller
            root.then(literal("step-down").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                if (activeGame == null || player == null) {
                    ctx.getSource().sendError(Text.literal("You are not in an active BOTC game."));
                    return 0;
                }
                try {
                    activeGame.getSeatManager().stepDownFromStoryteller(player);
                    ctx.getSource().sendFeedback(() -> Text.literal("Stepped down from storyteller seat."), true);
                    return 1;
                } catch (InvalidSeatException ex) {
                    ctx.getSource().sendError(Text.literal(ex.getMessage()));
                    return 0;
                }
            }));

            // Vacate a seat
            root.then(literal("seat").then(
                    literal("vacate").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                        if (activeGame == null || player == null) {
                                    ctx.getSource().sendError(Text.literal("Player is not in an active BOTC game."));
                                    return 0;
                                }
                                activeGame.getSeatManager().removePlayerFromSeat(player);
                                ctx.getSource().sendFeedback(() -> Text.literal("Vacated seat for player " + player.getName().getString() + "."), true);
                                return 1;
                            }
                    )));

            // Set character for a player
            root.then(literal("character").then(
                    literal("set").then(
                            CommandManager.argument("player", EntityArgumentType.player()).then(
                                    CommandManager.argument("character", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                                if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("Player is not in an " +
                                                            "active BOTC game."));
                                                    return 0;
                                                }
                                                Seat seat = activeGame.getSeatManager().getSeatFromPlayer(player);
                                                if (seat == null) {
                                                    ctx.getSource().sendError(Text.literal("Player has no seat " +
                                                            "assigned."));
                                                    return 0;
                                                }
                                                String characterId = StringArgumentType.getString(ctx, "character");
                                                botcCharacter character = activeGame.getScript().getCharacter(characterId);
                                                if (character == null) {
                                                    ctx.getSource().sendError(Text.literal("There is no character " +
                                                            "with this id on this script"));
                                                    return 0;
                                                }
                                                try {
                                                    seat.setCharacter(character);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set character for " +
                                                            "player " + player.getName().getString() + " to " + character.name() + "."), true);
                                                    return 1;
                                                } catch (IllegalArgumentException ex) {
                                                    ctx.getSource().sendError(Text.literal("Failed to set character: " +
                                                            ex.getMessage()));
                                                    return 0;
                                                }
                                            })
                            )
                    )
            ));

            // Kill a player
            root.then(literal("kill").then(
                    CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                        botcActive activeGame = botc.getActiveGameFromPlayer(player);
                        if (activeGame == null || player == null) {
                            ctx.getSource().sendError(Text.literal("Player is not in an active BOTC game."));
                            return 0;
                        }
                        Seat seat = activeGame.getSeatManager().getSeatFromPlayer(player);
                        if (seat == null) {
                            ctx.getSource().sendError(Text.literal("Player has no seat assigned."));
                            return 0;
                        }
                        if (seat.kill()) {
                            ctx.getSource().sendFeedback(() -> Text.literal("Killed player " + player.getName().getString() + "."), true);
                            return 1;
                        } else {
                            ctx.getSource().sendError(Text.literal("Player " + player.getName().getString() + " is " +
                                    "already dead."));
                            return 0;
                        }
                    })));

            // Revive a player
            root.then(literal("revive").then(
                    CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                        botcActive activeGame = botc.getActiveGameFromPlayer(player);
                        if (activeGame == null || player == null) {
                            ctx.getSource().sendError(Text.literal("Player is not in an active BOTC game."));
                            return 0;
                        }
                        Seat seat = activeGame.getSeatManager().getSeatFromPlayer(player);
                        if (seat == null) {
                            ctx.getSource().sendError(Text.literal("Player has no seat assigned."));
                            return 0;
                        }
                        if (seat.revive()) {
                            ctx.getSource().sendFeedback(() -> Text.literal("Revived player " + player.getName().getString() + "."), true);
                            return 1;
                        } else {
                            ctx.getSource().sendError(Text.literal("Player " + player.getName().getString() + " is " +
                                    "already alive."));
                            return 0;
                        }
                    })));

            // Toggle a player's alignment
            root.then(literal("alignment").then(
                    CommandManager.argument("player", EntityArgumentType.player()).then(
                            literal("toggle")
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                        botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                        if (activeGame == null || player == null) {
                                            ctx.getSource().sendError(Text.literal("Player is not in an active BOTC " +
                                                    "game."));
                                            return 0;
                                        }
                                        PlayerSeat seat = activeGame.getSeatManager().getPlayerSeatFromPlayer(player);
                                        if (seat == null) {
                                            ctx.getSource().sendError(Text.literal("Player has no seat assigned."));
                                            return 0;
                                        }
                                        Team.Alignment newAlignment = seat.toggleAlignment();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Toggled alignment for player" +
                                                " " + player.getName().getString() + " to " + newAlignment + "."),
                                                true);
                                        return 1;
                                    }))));

            root.then(literal("alignment").then(
                    CommandManager.argument("player", EntityArgumentType.player()).then(
                            CommandManager.argument("alignment", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                        botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                        if (activeGame == null || player == null) {
                                            ctx.getSource().sendError(Text.literal("Player is not in an active BOTC " +
                                                    "game."));
                                            return 0;
                                        }
                                        PlayerSeat seat = activeGame.getSeatManager().getPlayerSeatFromPlayer(player);
                                        if (seat == null) {
                                            ctx.getSource().sendError(Text.literal("Player has no seat assigned."));
                                            return 0;
                                        }
                                        String alignmentStr = StringArgumentType.getString(ctx, "alignment");
                                        try {
                                            Team.Alignment alignment =
                                                    Team.Alignment.valueOf(alignmentStr.toUpperCase());
                                            Team.Alignment newAlignment = seat.setAlignment(alignment);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Set alignment for player" +
                                                            " " + player.getName().getString() + " to " + newAlignment + ".")
                                                    , true);
                                            return 1;
                                        } catch (InvalidAlignmentException ex) {
                                            ctx.getSource().sendError(Text.literal("Cannot set alignment: " + ex.getMessage()));
                                            return 0;
                                        } catch (IllegalArgumentException ex) {
                                            ctx.getSource().sendError(Text.literal("Invalid alignment: " + alignmentStr));
                                            return 0;
                                        }
                                    }))));

            // Add a reminder to a player
            root.then(literal("reminder").then(
                    literal("add").then(
                            CommandManager.argument("player", EntityArgumentType.player()).then(
                                    CommandManager.argument("reminder", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                                if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("Player is not in an " +
                                                            "active BOTC game."));
                                                    return 0;
                                                }
                                                PlayerSeat seat =
                                                        activeGame.getSeatManager().getPlayerSeatFromPlayer(player);
                                                if (seat == null) {
                                                    ctx.getSource().sendError(Text.literal("Player has no seat " +
                                                            "assigned."));
                                                    return 0;
                                                }
                                                String reminder = StringArgumentType.getString(ctx, "reminder");
                                                seat.addReminderToken(reminder);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Added reminder for " +
                                                        "player " + player.getName().getString() + ": " + reminder),
                                                        true);
                                                return 1;
                                            })
                            )
                    )
            ));

            // Remove a reminder from a player
            root.then(literal("reminder").then(
                    literal("remove").then(
                            CommandManager.argument("player", EntityArgumentType.player()).then(
                                    CommandManager.argument("reminder", IntegerArgumentType.integer(1, 255))
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                                                if (activeGame == null || player == null) {
                                                    ctx.getSource().sendError(Text.literal("Player is not in an " +
                                                            "active BOTC game."));
                                                    return 0;
                                                }
                                                PlayerSeat seat =
                                                        activeGame.getSeatManager().getPlayerSeatFromPlayer(player);
                                                if (seat == null) {
                                                    ctx.getSource().sendError(Text.literal("Player has no seat " +
                                                            "assigned."));
                                                    return 0;
                                                }
                                                int reminderIndex = IntegerArgumentType.getInteger(ctx, "reminder") - 1;
                                                botcCharacter.ReminderToken reminder = seat.removeReminder(reminderIndex);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Removed reminder for" +
                                                        " player " + player.getName().getString() + ": " + reminder),
                                                        true);
                                                return 1;
                                            })
                            )
                    )
            ));

//            root.then(literal("reminder").then(
//                    literal("remove").then(
//                            CommandManager.argument("player", EntityArgumentType.player()).then(
//                                    CommandManager.argument("reminder", StringArgumentType.greedyString())
//                                            .executes(ctx -> {
//                                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
//                                                botcActive activeGame = botc.getActiveGameFromPlayer(player);
//                                                if (activeGame == null || player == null) {
//                                                    ctx.getSource().sendError(Text.literal("Player is not in an " +
//                                                            "active BOTC game."));
//                                                    return 0;
//                                                }
//                                                PlayerSeat seat =
//                                                        activeGame.getSeatManager().getPlayerSeatFromPlayer(player);
//                                                if (seat == null) {
//                                                    ctx.getSource().sendError(Text.literal("Player has no seat " +
//                                                            "assigned."));
//                                                    return 0;
//                                                }
//                                                String reminderText = StringArgumentType.getString(ctx, "reminder");
//                                                if (seat.hasReminder(reminderText)) {
//                                                    seat.removeReminder(reminderText);
//                                                    ctx.getSource().sendFeedback(() -> Text.literal("Removed reminder" +
//                                                            " for player " + player.getName().getString() + ": " + reminderText), true);
//                                                    return 1;
//                                                } else {
//                                                    ctx.getSource().sendError(Text.literal("Reminder not found for " +
//                                                            "player " + player.getName().getString() + ": " + reminderText));
//                                                    return 0;
//                                                }
//                                            })))));


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

            root.then(literal("gui").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) {
                    ctx.getSource().sendError(Text.literal("This command may only be used by players."));
                    return 0;
                }
                botcActive activeGame = botc.getActiveGameFromPlayer(player);
                if (activeGame == null) {
                    ctx.getSource().sendError(Text.literal("You are not in an active BOTC game."));
                    return 0;
                }
                GrimoireGUI gui = new GrimoireGUI(player, activeGame.getSeatManager(), activeGame.getScript());
                gui.open();
                return 1;
            }));

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
     * @return simple map name without a path or extension
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

    /** A hidden constructor to prevent instantiation. */
    private botcCommands() {}
}

// Empty seat
// Clear seat
// Toggle alignment
// Change botcCharacter
// Add reminder
// Remove reminder