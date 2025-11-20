package golden.botc_mc.botc_mc.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.*;
import net.minecraft.entity.damage.DamageSource;
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
import golden.botc_mc.botc_mc.game.voice.VoicechatPlugin;

public class botcWaiting {
    private final GameSpace gameSpace;
    private final Map map;
    private final botcConfig config;
    private final SpawnLogic spawnLogic;
    private final ServerWorld world;

    private botcWaiting(GameSpace gameSpace, ServerWorld world, Map map, botcConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.world = world;
        this.spawnLogic = new SpawnLogic(world, map);
        // Create per-map voice region manager and activate it
        VoiceRegionManager vrm = VoiceRegionManager.forMap(world, config.mapId());
        VoiceRegionService.setActive(world, config.mapId(), vrm);
    }

    public static GameOpenProcedure open(GameOpenContext<botcConfig> context) {
        // Load server-side settings and merge with the datapack-provided config. Server-side
        // values (run/config/botc.properties) override the datapack when present.
        botcSettings settings = botcSettings.load();
        botcConfig effectiveConfig = settings.applyTo(context.config());

        Identifier mapId = effectiveConfig.mapId();
        Map map = Map.load(context.server(), mapId);

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                // Map generator API mismatch: `asGenerator` returns a ChunkGenerator stub. Cast to avoid compilation errors.
                .setGenerator(map.asGenerator(context.server()));

        return context.openWithWorld(worldConfig, (game, world) -> {
            // Use the effective config (merged from settings + datapack) for the activity
            botcWaiting waiting = new botcWaiting(game.getGameSpace(), world, map, effectiveConfig);
            // Activate per-map voice groups
            try {
                VoicechatPlugin plugin = VoicechatPlugin.getInstance(context.server());
                plugin.onMapOpen(mapId);
            } catch (Throwable ignored) {}
            // Set a safe spawn for the world to avoid initial void placement
            Vec3d safe = waiting.spawnLogic.getSafeSpawnPosition();
            world.setSpawnPos(BlockPos.ofFloored(safe), 0.0F);

            // Deny fall damage while in waiting to prevent void deaths before teleport
            game.setRule(GameRuleType.FALL_DAMAGE, EventResult.DENY);

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            // Teleport accepted players to the map spawn (centered on the block and one block above).
            // Fallback to Vec3d.ZERO if no spawn is defined to avoid NPEs.
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> joinAcceptor.teleport(world, safe));
            game.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
            // Removed DISPOSE listener; map voice groups are unloaded in botcActive.onClose
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