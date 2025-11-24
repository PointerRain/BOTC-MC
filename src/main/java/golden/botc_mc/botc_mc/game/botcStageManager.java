package golden.botc_mc.botc_mc.game;

import com.google.common.collect.ImmutableSet;
import golden.botc_mc.botc_mc.game.state.BotcGameState;
import golden.botc_mc.botc_mc.game.state.BotcStateContext;
import golden.botc_mc.botc_mc.game.state.BotcStateMachine;
import golden.botc_mc.botc_mc.game.state.GameLifecycleStatus;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Set;

/**
 * Manages timed transitions between game states and lobby lifecycle.
 */
public class botcStageManager {
    /** Tick time when current state finishes (-1 if indefinite). */
    private long closeTime = -1;
    /** Tick time when current state finishes (-1 if indefinite). */
    public long finishTime = -1;
    /** Tick time when game session opens. */
    private long startTime = -1;
    /** Frozen positional snapshot for players during certain phases. */
    private final Object2ObjectMap<ServerPlayerEntity, FrozenPlayer> frozen;
    /** Prevent spectator mode from being set multiple times. */
    private boolean setSpectator = false;
    /** State machine driving game state transitions. */
    private final BotcStateMachine stateMachine;
    /** Runtime context for the current game space. */
    private BotcStateContext stateContext;
    // Ensure configuredDurations initialized to DEFAULT_PHASES and no obsolete defaults() calls remain.
    private static final botcPhaseDurations DEFAULT_PHASES = new botcPhaseDurations(120,45,20,60);
    private botcPhaseDurations configuredDurations = DEFAULT_PHASES;
    // track whether any players have been present since the game opened
    /** Track whether any players have been present since the game opened. */
    private boolean hadPlayers = false;
    /** Current lifecycle status (lobby, running, finished, closed). */
    private GameLifecycleStatus lifecycleStatus = GameLifecycleStatus.STOPPED;

    /** Default constructor initializes idle lobby state. */
    public botcStageManager() {
        this.frozen = new Object2ObjectOpenHashMap<>();
        this.stateMachine = new BotcStateMachine(DEFAULT_PHASES); // removed defaults() reference
        this.stateMachine.onStateChanged(this::handleStateChanged);
    }

    /**
     * Gets the current active BOTC game state.
     * @return current active BOTC game state
     */
    public BotcGameState getCurrentState() {
        return this.stateMachine.getCurrentState();
    }

    /**
     * Retrieves the current lifecycle status (lobby, running, finished, or closed).
     * @return lifecycle status of the game
     */
    public GameLifecycleStatus getLifecycleStatus() {
        return this.lifecycleStatus;
    }

    /**
     * Returns the number of ticks elapsed in the current state so far.
     * @return ticks elapsed in current state
     */
    public long getTicksInState() {
        return this.stateMachine.getTicksInState();
    }

    /**
     * Computes remaining ticks until the current state is scheduled to finish.
     * @return remaining ticks until state finish (0 if finished)
     */
    public long getStateTicksRemaining() {
        long duration = this.getStateDuration();
        long elapsed = this.stateMachine.getTicksInState();
        return Math.max(0, duration - elapsed);
    }

    /**
     * Duration in ticks for the current state based on configured phase durations.
     * Falls back to 1 tick minimum to avoid divide-by-zero when a state has zero length.
     * @return duration in ticks for current state
     */
    public long getStateDuration() {
        BotcGameState state = this.stateMachine.getCurrentState();
        if (this.configuredDurations == null) return 1L;
        long d = this.configuredDurations.durationTicks(state);
        return Math.max(1L, d);
    }

    /** Open hook invoked when the game session begins.
     * @param time opening tick
     */
    public void onOpen(long time) {
        this.startTime = time - (time % 20) + (4 * 20) + 19;
        botcSettings settings = botcSettingsManager.get();
        int timeLimitSecs = settings.timeLimitSecs > 0 ? settings.timeLimitSecs : 300;
        this.finishTime = this.startTime + (timeLimitSecs * 20L);
        this.stateMachine.start(time, this.stateContext);
        this.lifecycleStatus = GameLifecycleStatus.STOPPED;
        // removed setLifecycleStatus call (method no longer exists on context)
    }

    /** Attach space and config context for runtime operations.
     * @param space game space
     */
    public void attachContext(GameSpace space) {
        this.stateContext = new BotcStateContext(space);
        botcSettings s = botcSettingsManager.get();
        botcPhaseDurations durations = new botcPhaseDurations(s.dayDiscussionSecs, s.nominationSecs, s.executionSecs, s.nightSecs);
        this.configureStates(durations);
        if (this.stateContext != null) {
            int participants = space.getPlayers().participants().size();
            int spectators = space.getPlayers().spectators().size();
            golden.botc_mc.botc_mc.botc.LOGGER.debug("attachContext participants={} spectators={}", participants, spectators);
        }
    }

    /**
     * Record players were present at game start to allow finish conditions.
     * @param present true if at least one player was present
     */
    public void markPlayersPresent(boolean present) {
        if (present) this.hadPlayers = true;
    }

    private void configureStates(botcPhaseDurations durations) {
        this.configuredDurations = durations;
        this.stateMachine.setDurations(durations);
        this.stateMachine.setDefaultTransition(BotcGameState.LOBBY, BotcGameState.PRE_DAY);
        this.stateMachine.setDefaultTransition(BotcGameState.PRE_DAY, BotcGameState.DAY_DISCUSSION);
        this.stateMachine.setDefaultTransition(BotcGameState.DAY_DISCUSSION, BotcGameState.NOMINATION);
        this.stateMachine.setDefaultTransition(BotcGameState.NOMINATION, BotcGameState.EXECUTION);
        this.stateMachine.setDefaultTransition(BotcGameState.EXECUTION, BotcGameState.NIGHT);
        this.stateMachine.setDefaultTransition(BotcGameState.NIGHT, BotcGameState.DAY_DISCUSSION);
        this.stateMachine.setDefaultTransition(BotcGameState.END, BotcGameState.END);
        // register exit action to mark onExit usage
        this.stateMachine.onExit(BotcGameState.NOMINATION, ctx -> ctx.broadcast(Text.literal("Nomination phase ended.")));
        this.stateMachine.onEnter(BotcGameState.LOBBY, ctx -> ctx.broadcast(Text.literal("Waiting for storyteller...")));
        this.stateMachine.onEnter(BotcGameState.PRE_DAY, ctx -> ctx.broadcast(Text.literal("Day is about to begin!")));
        this.stateMachine.onEnter(BotcGameState.DAY_DISCUSSION, ctx -> ctx.broadcast(Text.literal("Day discussion has started.")));
        this.stateMachine.onEnter(BotcGameState.NOMINATION, ctx -> ctx.broadcast(Text.literal("Nomination window open.")));
        this.stateMachine.onEnter(BotcGameState.EXECUTION, ctx -> ctx.broadcast(Text.literal("Execution vote resolving...")));
        this.stateMachine.onEnter(BotcGameState.NIGHT, ctx -> ctx.broadcast(Text.literal("Night phase: storytellers resolving actions.")));
        this.stateMachine.onEnter(BotcGameState.END, ctx -> ctx.broadcast(Text.literal("Game closing.")));
    }


    /** Per-tick update handling transitions and closure.
     * @param time current tick
     * @param space game space
     * @return result indicating follow-up action
     */
    public IdleTickResult tick(long time, GameSpace space) {
        if ((time % 200) == 0) {
            long tIn = this.getTicksInState();
            golden.botc_mc.botc_mc.botc.LOGGER.trace("StageManager tick={} stateTicks={}", time, tIn);
        }
        // Close countdown handling
        if (this.closeTime > 0) {
            if (time >= this.closeTime) return IdleTickResult.GAME_CLOSED;
            return IdleTickResult.TICK_FINISHED;
        }
        // Pre-start countdown phase
        if (time < this.startTime) {
            this.tickStartWaiting(time, space);
            return IdleTickResult.TICK_FINISHED;
        }
        // Finish condition (time limit or empty players after start)
        boolean finishedByTime = time > this.finishTime;
        boolean finishedByEmpty = (space.getPlayers().isEmpty() && this.hadPlayers);
        if (finishedByTime || finishedByEmpty) {
            if (!this.setSpectator) {
                this.setSpectator = true;
                for (ServerPlayerEntity player : space.getPlayers()) player.changeGameMode(GameMode.SPECTATOR);
            }
            this.closeTime = time + (5 * 20);
            this.lifecycleStatus = GameLifecycleStatus.STOPPING;
            System.out.println("[BOTC] startTime=" + this.startTime + " finishTime=" + this.finishTime + " closeTime=" + this.closeTime + " now=" + time + " players=" + space.getPlayers().participants().size() + " hadPlayers=" + this.hadPlayers);
            if (this.stateContext != null) { // fixed malformed if syntax
                String reason = finishedByEmpty ? "No players remain; closing game." : "Game time finished; closing game.";
                this.stateContext.broadcast(Text.literal(reason));
            }
            return IdleTickResult.GAME_FINISHED;
        }
        if (!space.getPlayers().isEmpty()) this.hadPlayers = true;
        this.stateMachine.tick(time, this.stateContext);
        return IdleTickResult.CONTINUE_TICK;
    }

    /** Handle state-machine driven lifecycle status changes. */
    private void handleStateChanged(BotcGameState newState) {
        // Map game state to lifecycle status without duplicate STOPPED branch.
        this.lifecycleStatus = switch (newState) {
            case PRE_DAY -> GameLifecycleStatus.STARTING;
            case DAY_DISCUSSION, NOMINATION, EXECUTION, NIGHT -> GameLifecycleStatus.RUNNING;
            case END -> GameLifecycleStatus.STOPPING;
            case LOBBY -> GameLifecycleStatus.STOPPED; // explicit
        };
        // context propagation removed earlier intentionally
    }

    /** Countdown display / player freezing logic during pre-start waiting. */
    private void tickStartWaiting(long time, GameSpace space) {
        float sec_f = (this.startTime - time) / 20.0f;

        if (sec_f > 1) {
            for (ServerPlayerEntity player : space.getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }

                FrozenPlayer state = this.frozen.computeIfAbsent(player, p -> new FrozenPlayer());

                if (state.lastPos == null) {
                    state.lastPos = player.getPos();
                }

                // Set X and Y as relative so it will send 0 change when we pass yaw (yaw - yaw = 0) and pitch
                Set<PositionFlag> flags = ImmutableSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT);

                // Teleport without changing the pitch and yaw
                player.networkHandler.requestTeleport(new PlayerPosition(state.lastPos, Vec3d.ZERO, 0, 0), flags);
            }
        }

        int sec = (int) Math.floor(sec_f) - 1;

        if ((this.startTime - time) % 20 == 0) {
            PlayerSet players = space.getPlayers();

            if (sec > 0) {
                players.showTitle(Text.literal(Integer.toString(sec)).formatted(Formatting.BOLD), 20);
                players.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                players.showTitle(Text.literal("Go!").formatted(Formatting.BOLD), 20);
                players.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 2.0F);
            }
        }
    }

    /** Snapshot of a frozen player's last position. */
    public static class FrozenPlayer {
        /** Default constructor creates an empty positional snapshot holder. */
        public FrozenPlayer() {}
        /** Last recorded position used to keep player visually stationary. */
        public Vec3d lastPos;
    }

    /** Result codes from an idle tick evaluation. */
    public enum IdleTickResult {
        /** Continue processing normally. */ CONTINUE_TICK,
        /** Do not progress game logic this tick. */ TICK_FINISHED,
        /** Game finished and transitioning to close countdown. */ GAME_FINISHED,
        /** Game fully closed and should be torn down. */ GAME_CLOSED
    }
}
