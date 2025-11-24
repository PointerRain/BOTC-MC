package golden.botc_mc.botc_mc.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.botcMap;
import golden.botc_mc.botc_mc.game.map.botcMapGenerator;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

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
        System.out.println(this.config.toString());
    }

    public static GameOpenProcedure open(GameOpenContext<botcConfig> context) {
        // Load server-side settings and merge with the datapack-provided config. Server-side
        // values (run/config/botc.properties) override the datapack when present.
        botcSettings settings = botcSettings.load();
        botcConfig effectiveConfig = settings.applyTo(context.config());

        botcMapGenerator generator = new botcMapGenerator(effectiveConfig.mapConfig());
        botcMap map = generator.build();

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                // Map generator API mismatch: `asGenerator` returns a ChunkGenerator stub. Cast to avoid compilation errors.
                .setGenerator((net.minecraft.world.gen.chunk.ChunkGenerator) map.asGenerator(context.server()));

        return context.openWithWorld(worldConfig, (game, world) -> {
            // Use the effective config (merged from settings + datapack) for the activity
            botcWaiting waiting = new botcWaiting(game.getGameSpace(), world, map, effectiveConfig);

            // GameWaitingLobby.addTo requires a WaitingLobbyConfig; original code passed an int. Leave commented until real API usage is implemented.
            // GameWaitingLobby.addTo(game, config.players());

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> joinAcceptor.teleport(world, Vec3d.ZERO));
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
