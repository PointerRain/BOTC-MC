package golden.botc_mc.botc_mc.game.voice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.world.ServerWorld;

public final class VoiceRegionCommands {
    private static boolean registered = false; // prevent duplicate registration

    public static void register(VoiceRegionManager fallback) {
        if (registered) {
            botc.LOGGER.debug("VoiceRegionCommands.register() called more than once; ignoring subsequent call");
            return;
        }
        registered = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Root: /botc voice
            LiteralArgumentBuilder<ServerCommandSource> botcRoot = literal("botc");
            // Require operator permissions for voice commands (setup/debug)
            LiteralArgumentBuilder<ServerCommandSource> voiceRoot = literal("voice").requires(src -> src.hasPermissionLevel(2));

            VoiceRegionManager mgrOrFallback = VoiceRegionService.getActiveManager();
            final VoiceRegionManager mgr = (mgrOrFallback != null) ? mgrOrFallback : fallback;

            // Group subtree: /botc voice group add <name>  (aliases: group create)
            LiteralArgumentBuilder<ServerCommandSource> groupRoot = literal("group").requires(src -> src.hasPermissionLevel(2));

            // Build the primary 'add' node and keep a reference for redirects
            LiteralArgumentBuilder<ServerCommandSource> groupAdd = literal("add").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendFeedback(() -> Text.literal("Only a player can create a voice group."), false);
                    return 0;
                }
                String name = StringArgumentType.getString(ctx, "name");

                // If group already exists, acknowledge instead of failing
                if (SvcBridge.groupExists(name)) {
                    src.sendFeedback(() -> Text.literal("Group already exists: " + name), false);
                    return 1;
                }
                // If creation globally disabled, inform once and skip
                if (SvcBridge.isGroupCreationDisabled()) {
                    src.sendFeedback(() -> Text.literal("Group creation disabled (voicechat " + SvcBridge.getVoicechatVersion() + ") - using regions without voice groups."), false);
                    return 0;
                }

                if (!SvcBridge.isAvailableRuntime()) {
                    src.sendFeedback(() -> Text.literal("Voice chat not initialized yet; try again later."), false);
                    return 0;
                }

                java.util.UUID id = SvcBridge.createOrGetGroup(name, player);
                if (id != null) {
                    BotcVoicechatPlugin plugin = BotcVoicechatPlugin.getInstance(src.getServer());
                    PersistentGroupStore store = plugin.getStore();
                    PersistentGroup pg = new PersistentGroup(name);
                    pg.voicechatId = id;
                    store.addGroup(pg);
                    src.sendFeedback(() -> Text.literal("Group created: " + name + " (" + id + ")"), false);
                    return 1;
                }

                // At this point, either creation failed unexpectedly or was disabled mid-attempt.
                if (SvcBridge.isGroupCreationDisabled()) {
                    src.sendFeedback(() -> Text.literal("Group creation now disabled (voicechat " + SvcBridge.getVoicechatVersion() + ")."), false);
                } else {
                    src.sendFeedback(() -> Text.literal("Could not create group: " + name), false);
                }
                return 0;
            }));

            groupRoot.then(groupAdd);
            // alias: group create -> redirect to add
            groupRoot.then(literal("create").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("name", StringArgumentType.word()).redirect(groupAdd.build())));

            // Register group subtree under voice
            voiceRoot.then(groupRoot);

            // Region subtree: /botc voice region add <id> <groupName> <pos1> <pos2>
            LiteralArgumentBuilder<ServerCommandSource> regionRoot = literal("region").requires(src -> src.hasPermissionLevel(2));

            // Build primary region add node (refactored into named sub-nodes for readability)
            var pos2Node = CommandManager.argument("pos2", BlockPosArgumentType.blockPos()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendFeedback(() -> Text.literal("Only a player can run this."), false);
                    return 0;
                }
                String id = StringArgumentType.getString(ctx, "id");
                String group = StringArgumentType.getString(ctx, "group");
                BlockPos a = BlockPosArgumentType.getBlockPos(ctx, "pos1");
                BlockPos b = BlockPosArgumentType.getBlockPos(ctx, "pos2");

                // Normalize vertical extent to include all blocks up/down
                int minY = Integer.MIN_VALUE / 2;
                int maxY = Integer.MAX_VALUE / 2;
                BlockPos aAll = new BlockPos(Math.min(a.getX(), b.getX()), minY, Math.min(a.getZ(), b.getZ()));
                BlockPos bAll = new BlockPos(Math.max(a.getX(), b.getX()), maxY, Math.max(a.getZ(), b.getZ()));

                java.util.UUID groupUuid = null;
                if (SvcBridge.isAvailableRuntime()) {
                    try {
                        groupUuid = SvcBridge.createOrGetGroup(group, player);
                    } catch (Throwable t) {
                        golden.botc_mc.botc_mc.botc.LOGGER.warn("SvcBridge createOrGetGroup failed: {}", t.getMessage());
                    }
                }

                mgr.create(id, group, groupUuid == null ? null : groupUuid.toString(), aAll, bAll);
                final String createdRegionMsg = "Created region " + id + " -> " + group + (groupUuid == null ? "" : (" (" + groupUuid + ")")) + " [full height]";
                src.sendFeedback(() -> Text.literal(createdRegionMsg), false);
                return 1;
            });

            var pos1Node = CommandManager.argument("pos1", BlockPosArgumentType.blockPos()).then(pos2Node);
            var groupNode = CommandManager.argument("group", StringArgumentType.word()).then(pos1Node);
            var idNode = CommandManager.argument("id", StringArgumentType.word()).then(groupNode);
            LiteralArgumentBuilder<ServerCommandSource> regionAdd = literal("add").requires(src -> src.hasPermissionLevel(2)).then(idNode);

            regionRoot.then(regionAdd);
            // alias: region create
            regionRoot.then(literal("create").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("id", StringArgumentType.word()).then(CommandManager.argument("group", StringArgumentType.word()).then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos()).then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos()).redirect(regionAdd.build()))))));
            // also expose single-word alias region-create
            voiceRoot.then(literal("region-create").requires(src -> src.hasPermissionLevel(2)).redirect(regionAdd.build()));

            // region remove: /botc voice region remove <id>
            LiteralArgumentBuilder<ServerCommandSource> regionRemove = literal("remove").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                String id = StringArgumentType.getString(ctx, "id");
                VoiceRegion removed = mgr.remove(id);
                if (removed != null) {
                    src.sendFeedback(() -> Text.literal("Removed voice region " + id), false);
                    return 1;
                } else {
                    src.sendError(Text.literal("No region: " + id));
                    return 0;
                }
            }));
            regionRoot.then(regionRemove);
            regionRoot.then(literal("del").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("id", StringArgumentType.word()).redirect(regionRemove.build())));

            // region list: /botc voice region list
            LiteralArgumentBuilder<ServerCommandSource> regionList = literal("list").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                src.sendFeedback(() -> Text.literal("Voice regions:"), false);
                for (VoiceRegion r : mgr.list()) {
                    src.sendFeedback(() -> Text.literal(r.id + " -> " + r.groupName + " (" + r.groupId + ") " + r.boundsDebug()), false);
                }
                return 1;
            });
            regionRoot.then(regionList);
            voiceRoot.then(literal("regions").requires(src -> src.hasPermissionLevel(2)).redirect(regionList.build()));

            // Also attach region subtree
            voiceRoot.then(regionRoot);

            // Clear all: /botc voice wipe-all
            LiteralArgumentBuilder<ServerCommandSource> wipeAllNode = literal("wipe-all").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                int leftPlayers = 0;
                int removedGroups = 0;
                int clearedRegions = 0;
                try {
                    if (SvcBridge.isAvailableRuntime()) {
                        var list = src.getServer().getPlayerManager().getPlayerList();
                        for (ServerPlayerEntity p : list) {
                            try { if (SvcBridge.leaveGroup(p)) leftPlayers++; } catch (Throwable ignored) {}
                        }
                        removedGroups = SvcBridge.removeAllGroupsBestEffort();
                    }
                } catch (Throwable t) {
                    botc.LOGGER.warn("clear-all SVC step failed: {}", t.toString());
                }
                try {
                    clearedRegions = mgr.clearAll();
                    BotcVoicechatPlugin plugin = BotcVoicechatPlugin.getInstance(src.getServer());
                    PersistentGroupStore store = plugin.getStore();
                    int persisted = store.clearAll();
                    botc.LOGGER.info("clear-all cleared {} persistent groups", persisted);
                } catch (Throwable t) {
                    botc.LOGGER.warn("clear-all region step failed: {}", t.toString());
                }
                final String clearMsg = "Wipe complete: left " + leftPlayers + " player(s), removed " + removedGroups + " group(s), cleared " + clearedRegions + " region(s).";
                src.sendFeedback(() -> Text.literal(clearMsg), true);
                return 1;
            });
            voiceRoot.then(wipeAllNode);

            // Debug toggles: /botc voice debug regions|task <true|false>
            LiteralArgumentBuilder<ServerCommandSource> debugRoot = literal("debug").requires(src -> src.hasPermissionLevel(2));
            debugRoot.then(literal("regions").then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                VoiceRegionManager.setDebugRegions(enabled);
                ctx.getSource().sendFeedback(() -> Text.literal("Voice region debug " + (enabled ? "enabled" : "disabled")), false);
                return 1;
            })));
            debugRoot.then(literal("task").then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                VoiceRegionTask.setDebugTask(enabled);
                ctx.getSource().sendFeedback(() -> Text.literal("Voice autojoin task debug " + (enabled ? "enabled" : "disabled")), false);
                return 1;
            })));
            voiceRoot.then(debugRoot);

            // Watchers: /botc voice watch add|remove <player>, list
            LiteralArgumentBuilder<ServerCommandSource> watchRoot = literal("watch").requires(src -> src.hasPermissionLevel(2));
            watchRoot.then(literal("add").then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                boolean added = VoiceRegionTask.addWatcher(target.getUuid());
                ctx.getSource().sendFeedback(() -> Text.literal((added ? "Now" : "Already") + " watching " + target.getName().getString()), false);
                return added ? 1 : 0;
            })));
            watchRoot.then(literal("remove").then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                boolean removed = VoiceRegionTask.removeWatcher(target.getUuid());
                ctx.getSource().sendFeedback(() -> Text.literal(removed ? "Stopped watching " + target.getName().getString() : target.getName().getString() + " was not being watched"), false);
                return removed ? 1 : 0;
            })));
            watchRoot.then(literal("list").executes(ctx -> {
                var watchers = VoiceRegionTask.watchers();
                if (watchers.isEmpty()) {
                    ctx.getSource().sendFeedback(() -> Text.literal("No active voice region watchers."), false);
                    return 0;
                }
                StringBuilder sb = new StringBuilder("Watching: ");
                boolean first = true;
                for (java.util.UUID uuid : watchers) {
                    if (!first) sb.append(", "); else first = false;
                    ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(uuid);
                    sb.append(player != null ? player.getName().getString() : uuid.toString());
                }
                ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                return watchers.size();
            }));
            voiceRoot.then(watchRoot);

            // Info: /botc voice info
            voiceRoot.then(literal("info").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                VoiceRegionManager active = VoiceRegionService.getActiveManager();
                VoiceRegionManager used = active != null ? active : mgr;
                StringBuilder sb = new StringBuilder();
                sb.append("VoiceRegion info\n");
                sb.append("Config path: ").append(used.getConfigPath()).append("\n");
                sb.append("Map bound: ").append(used.getMapId()==null?"<none>":used.getMapId()).append("\n");
                sb.append("Regions: ").append(used.list().size()).append("\n");
                for (VoiceRegion r : used.list()) {
                    sb.append(" - ").append(r.id).append(" -> ").append(r.groupName).append(" (gid=").append(r.groupId).append(") bounds=").append(r.boundsDebug()).append("\n");
                }
                ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                return used.list().size();
            }));

            // Reload: /botc voice reload
            voiceRoot.then(literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                VoiceRegionManager active = VoiceRegionService.getActiveManager();
                VoiceRegionManager used = active != null ? active : mgr;
                int before = used.list().size();
                int after = used.reload();
                ctx.getSource().sendFeedback(() -> Text.literal("Reloaded voice regions: " + before + " -> " + after), false);
                return after;
            }));
            // Build tree
            botcRoot.then(voiceRoot);
            dispatcher.register(botcRoot);
        });
    }
}
