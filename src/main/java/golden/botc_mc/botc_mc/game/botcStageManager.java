package golden.botc_mc.botc_mc.game;

import com.google.common.collect.ImmutableSet;
import golden.botc_mc.botc_mc.game.state.BotcGameState;
import golden.botc_mc.botc_mc.game.state.BotcStateContext;
import golden.botc_mc.botc_mc.game.state.BotcStateMachine;
import golden.botc_mc.botc_mc.game.state.GameLifecycleStatus;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
 * Manages game state transitions and the storyteller timer.
 * <p>
 * Phases are advanced manually by the storyteller via commands — there is no automatic progression.
 * The storyteller can start a Discussion timer; when it expires the bell rings and players are
 * prompted to return to the town square. The bell can also be triggered directly via /botc bell.
 */
public class botcStageManager {
    /** Tick at which the game session should be torn down (-1 = not set). */
    private long closeTime = -1;
    /** Tick at which the pre-start countdown finishes. */
    private long startTime = -1;
    /** Frozen positional snapshot for players during the pre-start countdown. */
    private final Object2ObjectMap<ServerPlayerEntity, FrozenPlayer> frozen;
    /** Prevent spectator mode from being set multiple times. */
    private boolean setSpectator = false;
    /** State machine driving game state transitions. */
    private final BotcStateMachine stateMachine;
    /** Runtime context for the current game space. */
    private BotcStateContext stateContext;
    private boolean hadPlayers = false;
    /** Current lifecycle status (lobby, running, finished, or closed). */
    private GameLifecycleStatus lifecycleStatus = GameLifecycleStatus.STOPPED;

    /** Current phase number (0 = setup, 1+ once cycling starts). */
    private int phaseNumber = 0;

    // Storyteller timer fields
    private boolean timerActive = false;
    private String timerTitle = "Timer";
    private long timerDurationTicks = 0;
    private long timerStartTick = 0;

    // Bell chime sequencing
    private long lastKnownTick = 0;
    private long chimeNextDingAt = -1;
    private int chimeDingsLeft = 0;

    public botcStageManager() {
        this.frozen = new Object2ObjectOpenHashMap<>();
        this.stateMachine = new BotcStateMachine();
        this.stateMachine.onStateChanged(this::handleStateChanged);
    }

    public BotcGameState getCurrentState() {
        return this.stateMachine.getCurrentState();
    }

    public GameLifecycleStatus getLifecycleStatus() {
        return this.lifecycleStatus;
    }

    /** Human-readable label for the current phase, e.g. "Night 1" or "Day 2". */
    public String getPhaseLabel() {
        return switch (this.stateMachine.getCurrentState()) {
            case SETUP -> "Setup";
            case DAY -> "Day " + this.phaseNumber;
            case NIGHT -> "Night " + this.phaseNumber;
            case END -> "End";
        };
    }

    public boolean isTimerActive() { return this.timerActive; }
    public String getTimerTitle() { return this.timerTitle; }
    public long getTimerDurationTicks() { return this.timerDurationTicks; }

    public long getTimerTicksRemaining(long currentTick) {
        if (!this.timerActive) return 0;
        return Math.max(0, this.timerStartTick + this.timerDurationTicks - currentTick);
    }

    /** Open hook invoked when the game session begins. */
    public void onOpen(long time) {
        this.startTime = time - (time % 20) + (4 * 20) + 19;
        this.stateMachine.start(this.stateContext);
        this.lifecycleStatus = GameLifecycleStatus.STOPPED;
    }

    /** Attach game space and register state entry callbacks. */
    public void attachContext(GameSpace space) {
        this.stateContext = new BotcStateContext(space);
        this.configureStateCallbacks();
        int participants = space.getPlayers().participants().size();
        int spectators = space.getPlayers().spectators().size();
        golden.botc_mc.botc_mc.botc.LOGGER.debug("attachContext participants={} spectators={}", participants, spectators);
    }

    public void markPlayersPresent(boolean present) {
        if (present) this.hadPlayers = true;
    }

    private void configureStateCallbacks() {
        this.stateMachine.onEnter(BotcGameState.SETUP, ctx ->
            ctx.broadcast(Text.literal("Game setup started. Day 0.").formatted(Formatting.YELLOW)));
        this.stateMachine.onEnter(BotcGameState.NIGHT, ctx ->
            ctx.broadcast(Text.literal("Night " + this.phaseNumber + " has begun.").formatted(Formatting.DARK_BLUE)));
        this.stateMachine.onEnter(BotcGameState.DAY, ctx ->
            ctx.broadcast(Text.literal("Day " + this.phaseNumber + " has begun.").formatted(Formatting.YELLOW)));
        this.stateMachine.onEnter(BotcGameState.END, ctx ->
            ctx.broadcast(Text.literal("The game has ended.").formatted(Formatting.RED)));
    }

    /**
     * Advance to the next phase (storyteller command).
     * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> DAY 2 -> ...
     */
    public void advance() {
        switch (this.stateMachine.getCurrentState()) {
            case SETUP -> {
                this.phaseNumber = 1;
                this.stateMachine.transitionTo(BotcGameState.NIGHT, this.stateContext);
            }
            case NIGHT -> this.stateMachine.transitionTo(BotcGameState.DAY, this.stateContext);
            case DAY -> {
                this.phaseNumber++;
                this.stateMachine.transitionTo(BotcGameState.NIGHT, this.stateContext);
            }
            case END -> { /* already ended */ }
        }
    }

    /** End the game (storyteller command). */
    public void endGame() {
        this.stateMachine.transitionTo(BotcGameState.END, this.stateContext);
    }

    /**
     * Start a storyteller countdown timer.
     * @param currentTick current world tick
     * @param durationTicks timer duration in ticks
     * @param title display title shown on the boss bar
     */
    public void startTimer(long currentTick, long durationTicks, String title) {
        this.timerActive = true;
        this.timerDurationTicks = durationTicks;
        this.timerStartTick = currentTick;
        this.timerTitle = title != null && !title.isBlank() ? title : "Timer";
        if (this.stateContext != null) {
            long secs = durationTicks / 20;
            this.stateContext.broadcast(Text.literal(
                "Timer started: " + this.timerTitle + " (" + secs + "s)").formatted(Formatting.AQUA));
        }
    }

    /** Stop the current timer without ringing the bell. */
    public void stopTimer() {
        this.timerActive = false;
        if (this.stateContext != null) {
            this.stateContext.broadcast(Text.literal("Timer stopped.").formatted(Formatting.GRAY));
        }
    }

    /**
     * Ring the bell: plays the totem-style animation with a bell item on the first ding,
     * then fires two further dings ~0.7 s apart via the tick loop.
     * Only triggered at end of a Discussion timer or via /botc bell.
     */
    public void ringBell(GameSpace space) {
        this.playDing(space, true);
        // Schedule two follow-up dings (14 ticks ≈ 0.7 s apart)
        this.chimeDingsLeft = 2;
        this.chimeNextDingAt = this.lastKnownTick + 14;
        space.getPlayers().showTitle(
            Text.literal("Gather at the town square, townsfolk!").formatted(Formatting.GOLD), 80);
    }

    /**
     * Play a single bell ding.
     * @param withAnimation if true, triggers the totem-of-undying animation with a bell item
     *                      and gold dust particles; subsequent dings pass false for sound only.
     */
    private void playDing(GameSpace space, boolean withAnimation) {
        PlayerSet players = space.getPlayers();

        if (withAnimation) {
            // 0xFFD700 = gold (R=255, G=215, B=0); scale=2.0 for chunky visible particles
            DustParticleEffect goldDust = new DustParticleEffect(0xFFD700, 2.0f);
            for (ServerPlayerEntity player : players) {
                if (player.isSpectator()) continue;

                // DEATH_PROTECTION component is required in 1.21 for entity status 35
                // to display the custom item rather than a totem-of-undying.
                ItemStack bellItem = new ItemStack(Items.BELL);
                bellItem.set(DataComponentTypes.DEATH_PROTECTION, new DeathProtectionComponent(java.util.List.of()));

                // Swap to bell, trigger animation for all nearby players, then restore off-hand.
                ItemStack previousOffhand = player.getOffHandStack();
                player.setStackInHand(Hand.OFF_HAND, bellItem);
                player.currentScreenHandler.sendContentUpdates();
                player.getWorld().sendEntityStatus(player, (byte) 35);
                // Status 35 hardcodes the totem sound client-side; cancel it immediately.
                player.networkHandler.sendPacket(new StopSoundS2CPacket(Identifier.of("item.totem.use"), null));
                player.setStackInHand(Hand.OFF_HAND, previousOffhand);
                player.currentScreenHandler.sendContentUpdates();

                player.networkHandler.sendPacket(new ParticleS2CPacket(
                    goldDust, true, true,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    0.5f, 0.5f, 0.5f, 0.05f, 15
                ));
            }
        }

        players.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.0F, 1.0F);
    }

    /** Per-tick update: handles pre-start countdown, timer expiry, and close sequence. */
    public IdleTickResult tick(long time, GameSpace space) {
        this.lastKnownTick = time;

        if ((time % 200) == 0) {
            golden.botc_mc.botc_mc.botc.LOGGER.trace("StageManager tick={}", time);
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

        if (space.getPlayers().isEmpty() && this.hadPlayers) {
            if (!this.setSpectator) {
                this.setSpectator = true;
                for (ServerPlayerEntity player : space.getPlayers()) player.changeGameMode(GameMode.SPECTATOR);
            }
            this.closeTime = time + (5 * 20);
            this.lifecycleStatus = GameLifecycleStatus.STOPPING;
            if (this.stateContext != null) {
                this.stateContext.broadcast(Text.literal("No players remain; closing game."));
            }
            return IdleTickResult.GAME_FINISHED;
        }

        if (!space.getPlayers().isEmpty()) this.hadPlayers = true;

        if (this.timerActive && this.getTimerTicksRemaining(time) <= 0) {
            this.timerActive = false;
            if ("Discussion".equalsIgnoreCase(this.timerTitle)) {
                this.ringBell(space);
            }
        }

        if (this.chimeDingsLeft > 0 && time >= this.chimeNextDingAt) {
            this.playDing(space, false);
            this.chimeDingsLeft--;
            this.chimeNextDingAt = time + 14;
        }

        return IdleTickResult.CONTINUE_TICK;
    }

    private void handleStateChanged(BotcGameState newState) {
        this.lifecycleStatus = switch (newState) {
            case SETUP -> GameLifecycleStatus.STARTING;
            case DAY, NIGHT -> GameLifecycleStatus.RUNNING;
            case END -> GameLifecycleStatus.STOPPING;
        };
    }

    private void tickStartWaiting(long time, GameSpace space) {
        float sec_f = (this.startTime - time) / 20.0f;

        if (sec_f > 1) {
            for (ServerPlayerEntity player : space.getPlayers()) {
                if (player.isSpectator()) continue;

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

    /** Snapshot of a frozen player's last position used during the pre-start countdown. */
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
