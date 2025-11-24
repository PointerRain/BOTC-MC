package golden.botc_mc.botc_mc.game;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.Map;
import golden.botc_mc.botc_mc.game.state.GameLifecycleStatus;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import golden.botc_mc.botc_mc.botc;

/**
 * Active game session controller for a Blood on the Clocktower match.
 * Handles:
 * <ul>
 *   <li>Participant/spectator spawning and respawn logic.</li>
 *   <li>Lifecycle phase progression via {@link botcStageManager}.</li>
 *   <li>Player damage/death interception in early prototype state.</li>
 *   <li>Boss bar timer updates per phase.</li>
 * </ul>
 * The static {@code open} method installs listeners on the provided {@link GameSpace}.
 */
public class botcActive {
    private static final Logger LOG = LoggerFactory.getLogger("botc-mc");

    /** Plasmid game space hosting the session. */
    public final GameSpace gameSpace;

    private final Object2ObjectMap<PlayerRef, botcPlayer> participants;
    private final SpawnLogic spawnLogic;
    private final botcStageManager stageManager;
    private final botcTimerBar timerBar;
    private final ServerWorld world;

    private GameLifecycleStatus lifecycleStatus = GameLifecycleStatus.STOPPED;
    private boolean startingLogged = false;

    private botcActive(GameSpace gameSpace, ServerWorld world, Map map, GlobalWidgets widgets, Set<PlayerRef> participants) {
        this.gameSpace = gameSpace;
        this.spawnLogic = new SpawnLogic(world, map);
        this.participants = new Object2ObjectOpenHashMap<>();
        this.world = world;

        for (PlayerRef player : participants) {
            this.participants.put(player, new botcPlayer());
        }

        this.stageManager = new botcStageManager();
        this.timerBar = botcTimerBar.of(widgets);
    }

    /**
     * Open an active game using provided map and configuration.
     * @param gameSpace game space to bind
     * @param world backing overworld instance
     * @param map loaded map metadata
     */
    public static void open(GameSpace gameSpace, ServerWorld world, Map map) {
        gameSpace.setActivity(game -> {
            Set<PlayerRef> participants = gameSpace.getPlayers().participants().stream()
                    .map(PlayerRef::of)
                    .collect(Collectors.toSet());
            GlobalWidgets widgets = GlobalWidgets.addTo(game);
            botcActive active = new botcActive(gameSpace, world, map, widgets, participants);

            game.setRule(GameRuleType.CRAFTING, EventResult.DENY);
            game.setRule(GameRuleType.PORTALS, EventResult.DENY);
            game.setRule(GameRuleType.PVP, EventResult.DENY);
            game.setRule(GameRuleType.HUNGER, EventResult.DENY);
            game.setRule(GameRuleType.FALL_DAMAGE, EventResult.DENY);
            game.setRule(GameRuleType.USE_BLOCKS, EventResult.DENY);
            game.setRule(GameRuleType.BLOCK_DROPS, EventResult.DENY);
            game.setRule(GameRuleType.UNSTABLE_TNT, EventResult.DENY);

            game.listen(GameActivityEvents.ENABLE, active::onOpen);
            game.listen(GameActivityEvents.DISABLE, active::onClose);
            game.listen(GameActivityEvents.STATE_UPDATE, state -> state.canPlay(false));

            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> {
                Vec3d safe = active.spawnLogic.getSafeSpawnPosition();
                return joinAcceptor.teleport(world, safe);
            });
            game.listen(GamePlayerEvents.ADD, active::addPlayer);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> { active.onPlayerDamage(player, source, amount); return EventResult.DENY; });
            game.listen(PlayerDeathEvent.EVENT, (player, source) -> { active.onPlayerDeath(player, source); return EventResult.DENY; });
        });
    }

    /**
     * Game open hook: spawn existing participants/spectators and initialize the state machine.
     */
    private void onOpen() {
        for (var participant : this.gameSpace.getPlayers().participants()) this.spawnParticipant(participant);
        for (var spectator : this.gameSpace.getPlayers().spectators()) this.spawnSpectator(spectator);
        this.stageManager.attachContext(this.gameSpace);
        this.stageManager.markPlayersPresent(!this.gameSpace.getPlayers().participants().isEmpty());
        this.stageManager.onOpen(this.world.getTime());
    }

    /**
     * Game close hook; placeholder for teardown logic (voice region cleanup, etc.).
     */
    private void onClose() {
        int participantsCount = this.gameSpace.getPlayers().participants().size();
        int spectatorsCount = this.gameSpace.getPlayers().spectators().size();
        LOG.info("[BOTC:CLOSE] Closing game lifecycle={} participants={} spectators={}", this.lifecycleStatus, participantsCount, spectatorsCount);
        try {
            golden.botc_mc.botc_mc.game.voice.VoiceRegionService.setActive(null); // clear active voice context (updated signature)
        } catch (Throwable t) {
            LOG.warn("[BOTC:CLOSE] Voice region cleanup failed: {}", t.toString());
        }
        // Future: flush stats, persist results, release resources.
    }

    /** Add a newly joined player (as spectator if not in participants). */
    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(PlayerRef.of(player)) || this.gameSpace.getPlayers().spectators().contains(player)) {
            this.spawnSpectator(player);
        }
    }

    /** Remove a player from participant tracking. */
    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(PlayerRef.of(player));
    }

    /** Intercepts damage; prototype logic respawns player and cancels damage.
     * Converted to void; listener lambda always returns {@link EventResult#DENY}.
     */
    private void onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        LOG.debug("[BOTC:DAMAGE] player={} amount={} source={}", player.getGameProfile().getName(), amount, source.getName());
        this.spawnParticipant(player);
    }

    /** Intercepts death; prototype respawn and cancels death handling.
     * Converted to void; listener lambda always returns {@link EventResult#DENY}.
     */
    private void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        LOG.debug("[BOTC:DEATH] player={} source={}", player.getGameProfile().getName(), source.getName());
        this.spawnParticipant(player);
    }

    /** Respawn logic for a participant (adventure mode). */
    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    /** Respawn logic for a spectator (spectator mode). */
    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    /** Per-tick game update: progresses lifecycle, updates timer bar, and handles end conditions. */
    private void tick() {
        long time = this.world.getTime();

        botcStageManager.IdleTickResult result = this.stageManager.tick(time, gameSpace);

        GameLifecycleStatus currentLifecycle = this.stageManager.getLifecycleStatus();
        if (currentLifecycle != this.lifecycleStatus) {
            this.lifecycleStatus = currentLifecycle;
            onLifecycleStateChanged();
        }

        switch (result) {
            case CONTINUE_TICK -> { /* keep ticking */ }
            case TICK_FINISHED -> { return; }
            case GAME_FINISHED -> {
                this.lifecycleStatus = GameLifecycleStatus.STOPPING;
                onLifecycleStateChanged();
                this.broadcastWin(this.determineWinner());
                return;
            }
            case GAME_CLOSED -> {
                this.lifecycleStatus = GameLifecycleStatus.STOPPED;
                onLifecycleStateChanged();
                this.gameSpace.close(GameCloseReason.FINISHED);
                return;
            }
        }

        long remaining = this.stageManager.getStateTicksRemaining();
        long total = this.stageManager.getStateDuration();
        this.timerBar.updatePhase(this.stageManager.getCurrentState(), remaining, total);

        if ((time % 100) == 0) {
            long ticksInState = this.stageManager.getTicksInState();
            botc.LOGGER.debug("State {} ticksInState={}", this.stageManager.getCurrentState(), ticksInState);
        }

        // TODO tick logic per state
    }

    /** Broadcast the result of a finished game (placeholder win logic). */
    private void broadcastWin(ServerPlayerEntity winner) {
        Text message = (winner != null)
                ? Text.literal(winner.getGameProfile().getName() + " has won the game!").formatted(Formatting.GOLD)
                : Text.literal("The game ended, but nobody won!").formatted(Formatting.GOLD);
        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    /** Compute a win result: trivial prototype selects sole remaining participant if only one remains. */
    private ServerPlayerEntity determineWinner() {
        var participantEntities = this.gameSpace.getPlayers().participants();
        return participantEntities.size() == 1 ? participantEntities.iterator().next() : null;
    }

    /** React to lifecycle state changes (logging and starting hooks). */
    private void onLifecycleStateChanged() {
        LOG.info("Lifecycle changed to {}", this.lifecycleStatus);
        switch (this.lifecycleStatus) {
            case STARTING -> handleGameStarting();
            case RUNNING -> LOG.info("Game is now RUNNING");
            case STOPPING -> LOG.info("Game is STOPPING");
            case STOPPED -> LOG.info("Game is STOPPED");
        }
    }

    /** One-time logging hook when the game transitions from STARTING to RUNNING. */
    private void handleGameStarting() {
        if (startingLogged) {
            return; // Already logged starting logic
        }
        startingLogged = true;
        // Print a concise console line when the game begins
        int participantCount = this.gameSpace.getPlayers().participants().size();
        LOG.info("Game STARTING at tick {} with {} participant(s)", this.world.getTime(), participantCount);
    }
}
