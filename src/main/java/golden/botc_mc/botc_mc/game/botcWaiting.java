package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.Map;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionManager;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionService;

/**
 * Waiting/pre-game lobby phase controller. Responsible for loading the map, applying settings,
 * activating voice regions, and spawning players safely until the storyteller starts the game.
 */
public class botcWaiting {
    private final GameSpace gameSpace;
    private final Map map;
    private final SpawnLogic spawnLogic;
    private final ServerWorld world;
    private final Script script;

    private botcWaiting(GameSpace gameSpace, ServerWorld world, Map map, Script script) {
        this.gameSpace = gameSpace;
        this.world = world;
        this.map = map;
        this.script = script;
        this.spawnLogic = new SpawnLogic(world, map);
    }

    /**
     * Opens the waiting lobby game procedure with map and config, installing per-map voice groups.
     * Lobby/waiting room stage prior to active game start.
     * @param context game open context containing server, config, and other metadata
     * @return a GameOpenProcedure that initializes the waiting lobby world and listeners
     */
    public static GameOpenProcedure open(GameOpenContext<botcConfig> context) {
        // Load server-side settings and merge with the datapack-provided config. Server-side
        // values (run/config/botc.properties) override the datapack when present.
        botcSettings settings = botcSettings.load();
        botcConfig effectiveConfig = settings.applyTo(context.config());
        botc.LOGGER.info("Opening game with script: {}", effectiveConfig.script());

        Identifier mapId = effectiveConfig.mapId() != null ? effectiveConfig.mapId() : Identifier.of(settings.mapId);
        Map map = Map.load(context.server(), mapId);

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                // Map generator API mismatch: `asGenerator` returns a ChunkGenerator stub. Cast to avoid compilation errors.
                .setGenerator(map.asGenerator(context.server()));

        return context.openWithWorld(worldConfig, (game, world) -> {
            botcWaiting waiting = new botcWaiting(game.getGameSpace(), world, map, effectiveConfig.script());
            VoiceRegionManager vrm = VoiceRegionManager.forMap(world, mapId);
            VoiceRegionService.setActive(vrm);

            // Compute and set a safe spawn after world is available
            Vec3d initialSafe = waiting.spawnLogic.getSafeSpawnPosition();
            world.setSpawnPos(BlockPos.ofFloored(initialSafe), 0.0F);

            game.setRule(GameRuleType.FALL_DAMAGE, EventResult.DENY);

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            // Compute safe spawn at accept time to ensure chunks are generated
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> {
                Vec3d safeNow = waiting.spawnLogic.getSafeSpawnPosition();
                return joinAcceptor.teleport(world, safeNow);
            });
            game.listen(PlayerDeathEvent.EVENT, (player, source) -> { player.setHealth(20.0f); waiting.spawnPlayer(player); return EventResult.DENY; });
        });
    }

    /** Transition callback from waiting into active gameplay. */
    private GameResult requestStart() {
        botcActive.open(this.gameSpace, this.world, this.map, this.script);
        return GameResult.ok();
    }

    /** Add a player to the lobby (respawns them). */
    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    /** Respawn a player using the lobby spawn logic. */
    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}