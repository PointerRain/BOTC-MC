package golden.botc_mc.botc_mc.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.botcMap;
import golden.botc_mc.botc_mc.game.map.botcMapGenerator;
import golden.botc_mc.botc_mc.game.map.BotcMapTemplate;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Set;

import golden.botc_mc.botc_mc.botc;

public class botcWaiting {
    private final GameSpace gameSpace;
    private final botcMap map;
    private final botcConfig config;
    private final botcSpawnLogic spawnLogic;
    private final ServerWorld world;

    private botcWaiting(GameSpace gameSpace, ServerWorld world, botcMap map, botcConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.world = world;
        this.spawnLogic = new botcSpawnLogic(gameSpace, world, map);
    }

    public static GameOpenProcedure open(GameOpenContext<botcConfig> context) {
        // Load server-side settings and merge with the datapack-provided config. Server-side
        // values (run/config/botc.properties) override the datapack when present.
        botcSettings settings = botcSettings.load();
        botcConfig effectiveConfig = settings.applyTo(context.config());

        // Try to load a runtime-selected map id from settings. If present and discovered,
        // use BotcMapTemplate as a generator; otherwise fall back to the built-in botcMapGenerator.
        botcMapGenerator generator = new botcMapGenerator(effectiveConfig.mapConfig());
        botcMap map = generator.build();

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                // Map generator API mismatch: `asGenerator` returns a ChunkGenerator stub.
                .setGenerator(map.asGenerator(context.server()));

        // If a runtime map id is provided, attempt to discover and load it using BotcMapTemplate
        String selectedMapId = botcSettings.getMapId();
        BotcMapTemplate mapTpl = null;
        if (selectedMapId != null && !selectedMapId.isEmpty()) {
            // normalize unqualified ids (e.g. "example") to the botc namespace
            if (!selectedMapId.contains(":")) {
                selectedMapId = "botc:" + selectedMapId;
            }
            try {
                MinecraftServer server = context.server();
                golden.botc_mc.botc_mc.game.map.MapManager mgr = new golden.botc_mc.botc_mc.game.map.MapManager();
                mgr.discoverRuntimeMaps();
                var infoOpt = mgr.getInfo(selectedMapId);
                if (infoOpt.isPresent() && infoOpt.get().nbtFile != null) {
                    mapTpl = BotcMapTemplate.load(server, infoOpt.get().nbtFile);
                    worldConfig = new RuntimeWorldConfig().setGenerator(mapTpl.asGenerator(server));
                } else {
                    throw new RuntimeException("Map info or nbtFile missing for " + selectedMapId);
                }
            } catch (Exception e) {
                // log & fallback
                botc.LOGGER.warn("Failed to load selected map {}: {}", selectedMapId, e.getMessage());
            }
        }

        BotcMapTemplate finalMapTpl = mapTpl; // capture for lambda
        // botcMap finalMap = map; // redundant

        return context.openWithWorld(worldConfig, (game, world) -> {
            // If we have a template, create a template-backed botcMap now so we can compute spawn
            botcMap activeMap = map;
            if (finalMapTpl != null) {
                // Construct a botcMap backed by the template; this map's spawn will be computed below
                activeMap = new botcMap(finalMapTpl.getTemplate(), effectiveConfig.mapConfig());
                botc.LOGGER.info("Using template-backed map from template for activity");
            }

            // Use the effective config (merged from settings + datapack) for the activity
            botcWaiting waiting = new botcWaiting(game.getGameSpace(), world, activeMap, effectiveConfig);

            // capture the active map for lambdas (must be final/effectively final)
            final botcMap capturedMap = activeMap;

            // If template was used, compute spawn now that the world exists
            if (finalMapTpl != null) {
                try {
                    var spawnRegion = finalMapTpl.getRegions().spawn();
                    BlockBounds bounds = spawnRegion.bounds();
                    // compute center using bounds.center() (a Vec3d) and convert to BlockPos
                    BlockPos centerPos = BlockPos.ofFloored(bounds.center());
                    int centerX = centerPos.getX();
                    int centerZ = centerPos.getZ();

                    int topExclusive = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, centerX, centerZ);
                    int maxBlockY = topExclusive - 1;
                    int bottomY = world.getBottomY();
                    int spawnY = Math.max(bottomY, maxBlockY);

                    // ensure spawn is at least one above the ground block
                    capturedMap.spawn = new BlockPos(centerX, spawnY + 1, centerZ);
                    botc.LOGGER.info("Map template spawn set to X: {}, Y: {}, Z: {}", centerX, spawnY + 1, centerZ);

                    // Teleport any already-accepted players who are standing on fallback Vec3d.ZERO to the real spawn
                    double spawnDx = centerX + 0.5;
                    double spawnDz = centerZ + 0.5;
                    double spawnDy = spawnY + 1.0;
                    for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
                        // players in the gameSpace but currently at Vec3d.ZERO (fallback) should be moved
                        if (waiting.gameSpace.getPlayers().participants().contains(p) || waiting.gameSpace.getPlayers().spectators().contains(p)) {
                            var pos = p.getPos();
                            if (Math.abs(pos.x) < 0.001 && Math.abs(pos.y) < 0.001 && Math.abs(pos.z) < 0.001) {
                                botc.LOGGER.info("Teleporting player {} from fallback to computed spawn {}", p.getName().getString(), capturedMap.spawn);
                                p.teleport(world, spawnDx, spawnDy, spawnDz, Set.of(), 0.0F, 0.0F, true);
                            }
                        }
                    }
                } catch (Exception e) {
                    botc.LOGGER.warn("Failed to compute spawn from template: {}", e.getMessage());
                }
            }

            // GameWaitingLobby.addTo requires a WaitingLobbyConfig; original code passed an int. Leave commented until real API usage is implemented.
            // GameWaitingLobby.addTo(game, config.players());

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            // Teleport accepted players to the map spawn (centered on the block and one block above).
            // Fallback to Vec3d.ZERO if no spawn is defined to avoid NPEs.
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> {
                if (capturedMap.spawn != null) {
                    Vec3d spawnPos = new Vec3d(
                            capturedMap.spawn.getX() + 0.5,
                            capturedMap.spawn.getY() + 1.0,
                            capturedMap.spawn.getZ() + 0.5
                    );
                    botc.LOGGER.info("Player accepted; teleporting to spawn {}", capturedMap.spawn);
                    return joinAcceptor.teleport(world, spawnPos);
                } else {
                    botc.LOGGER.info("Player accepted; spawn not yet defined, teleporting to fallback Vec3d.ZERO");
                    return joinAcceptor.teleport(world, Vec3d.ZERO);
                }
            });
            game.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
        });
    }

    private GameResult requestStart() {
        botcActive.open(this.gameSpace, this.world, this.map, this.config);
        return GameResult.ok();
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        player.setHealth(20.0f);
        this.spawnPlayer(player);
        return EventResult.DENY;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
