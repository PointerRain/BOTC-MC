package golden.botc_mc.botc_mc.game.voice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;
import golden.botc_mc.botc_mc.botc;

public final class VoiceRegionCommands {
    public static void register(VoiceRegionManager mgr) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Root: /botc voice
            LiteralArgumentBuilder<ServerCommandSource> botcRoot = literal("botc");
            // Require operator permissions for voice commands (setup/debug)
            LiteralArgumentBuilder<ServerCommandSource> voiceRoot = literal("voice").requires(src -> src.hasPermissionLevel(2));

            // Group subtree: /botc voice group add <name>  (aliases: group create, group-create)
            LiteralArgumentBuilder<ServerCommandSource> groupRoot = literal("group").requires(src -> src.hasPermissionLevel(2));

            // Build the primary 'add' node and keep a reference for redirects
            LiteralArgumentBuilder<ServerCommandSource> groupAdd = literal("add").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendFeedback(() -> Text.literal("Only a player can create a voice group."), false);
                    return 0;
                }
                String name = StringArgumentType.getString(ctx, "name");
                if (!SvcBridge.isAvailableRuntime()) {
                    src.sendFeedback(() -> Text.literal("SvcBridge not available; cannot create group."), false);
                    return 0;
                }
                java.util.UUID id = SvcBridge.createOrGetGroup(name, player);
                if (id != null) {
                    // persist in store so it auto-loads next boot
                    BotcVoicechatPlugin plugin = BotcVoicechatPlugin.getInstance(src.getServer());
                    PersistentGroupStore store = plugin.getStore();
                    PersistentGroup pg = new PersistentGroup(name);
                    pg.voicechatId = id;
                    store.addGroup(pg);
                    final String createdMsg = "Group created: " + name + " (" + id + ")";
                    src.sendFeedback(() -> Text.literal(createdMsg), false);
                    return 1;
                } else {
                    src.sendFeedback(() -> Text.literal("Failed to create group: " + name), false);
                    return 0;
                }
            }));

            groupRoot.then(groupAdd);
            // alias: group create -> redirect to add
            groupRoot.then(literal("create").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("name", StringArgumentType.word()).redirect(groupAdd.build())));
            // alias: group-create (flat)
            voiceRoot.then(literal("group-create").requires(src -> src.hasPermissionLevel(2)).redirect(groupAdd.build()));

            // Group repair: /botc voice group repair <groupOrId> (alias: group fix)
            LiteralArgumentBuilder<ServerCommandSource> groupRepair = literal("repair").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("groupOrId", StringArgumentType.word()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                String arg = StringArgumentType.getString(ctx, "groupOrId");
                boolean ok;
                try { java.util.UUID.fromString(arg); ok = SvcBridge.clearPasswordAndOpenByIdString(arg); }
                catch (Exception e) { ok = SvcBridge.clearPasswordAndOpenByName(arg); }
                if (ok) src.sendFeedback(() -> Text.literal("Repaired voice group " + arg), false);
                else src.sendError(Text.literal("Failed to repair voice group " + arg));
                return ok ? 1 : 0;
            }));
            groupRoot.then(groupRepair);
            groupRoot.then(literal("fix").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("groupOrId", StringArgumentType.word()).redirect(groupRepair.build())));

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
                        botc.LOGGER.warn("SvcBridge createOrGetGroup failed: {}", t.getMessage());
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

            // region remove: /botc voice region remove <id> (alias: region del)
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

            // region list: /botc voice region list (alias: regions)
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

            // Clear all: renamed to wipe-all (keeps compatibility alias groups clear)
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
            // alias: groups clear
            voiceRoot.then(literal("groups").requires(src -> src.hasPermissionLevel(2)).then(literal("clear").redirect(wipeAllNode.build())));

            // Bridge info
            LiteralArgumentBuilder<ServerCommandSource> bridgeInfo = literal("bridge-info").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                src.sendFeedback(() -> Text.literal("SvcBridge available: " + SvcBridge.isAvailable()), false);
                for (String d : SvcBridge.getDiagnostics()) {
                    src.sendFeedback(() -> Text.literal(d), false);
                }
                return 1;
            });
            voiceRoot.then(bridgeInfo);

            // Position inspect: /botc voice debug-pos <player>  (alias: inspect-pos)
            LiteralArgumentBuilder<ServerCommandSource> debugPos = literal("debug-pos").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                String name = StringArgumentType.getString(ctx, "player");
                for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
                    if (p.getName().getString().equalsIgnoreCase(name)) {
                        VoiceRegion r = mgr.regionForPlayer(p);
                        String msg = "pos=" + p.getBlockX()+","+p.getBlockY()+","+p.getBlockZ()+" region=" + (r==null?"<none>":r.id+"/"+r.groupName+" bounds="+r.boundsDebug());
                        src.sendFeedback(() -> Text.literal(msg), false);
                        botc.LOGGER.info("POSDEBUG player={} uuid={} pos={},{},{} region={}", p.getName().getString(), p.getUuid(), p.getBlockX(), p.getBlockY(), p.getBlockZ(), (r==null?"<none>":r.id+"/"+r.groupName+" bounds="+r.boundsDebug()));
                        return 1;
                    }
                }
                src.sendError(Text.literal("Player not found: " + name));
                return 0;
            }));
            voiceRoot.then(debugPos);
            voiceRoot.then(literal("inspect-pos").requires(src -> src.hasPermissionLevel(2)).redirect(debugPos.build()));

            // Audit regions snapshot: /botc voice audit (alias: scan-regions)
            LiteralArgumentBuilder<ServerCommandSource> auditNode = literal("audit").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                var players = src.getServer().getPlayerManager().getPlayerList();
                src.sendFeedback(() -> Text.literal("VoiceRegion audit: players=" + players.size() + " regions=" + mgr.list().size()), false);
                for (ServerPlayerEntity p : players) {
                    VoiceRegion r = mgr.regionForPlayer(p);
                    String line = p.getName().getString() + " pos=" + p.getBlockX()+","+p.getBlockY()+","+p.getBlockZ()+" region=" + (r==null?"<none>":r.id+"/"+r.groupName+" bounds="+r.boundsDebug());
                    src.sendFeedback(() -> Text.literal(line), false);
                    botc.LOGGER.info("SCAN player={} uuid={} pos={},{},{} region={}", p.getName().getString(), p.getUuid(), p.getBlockX(), p.getBlockY(), p.getBlockZ(), (r==null?"<none>":r.id+"/"+r.groupName+" bounds="+r.boundsDebug()));
                }
                return 1;
            });
            voiceRoot.then(auditNode);
            voiceRoot.then(literal("scan-regions").requires(src -> src.hasPermissionLevel(2)).redirect(auditNode.build()));

            // Build tree
            botcRoot.then(voiceRoot);
            dispatcher.register(botcRoot);
        });
    }
}
