package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.TitleUtil;
import golden.botc_mc.botc_mc.game.state.BotcGameState;
import golden.botc_mc.botc_mc.game.state.BotcStateContext;
import golden.botc_mc.botc_mc.game.state.BotcStateMachine;
import golden.botc_mc.botc_mc.game.state.GameLifecycleStatus;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import net.minecraft.server.network.ServerPlayerEntity;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

/**
 * Manages game state transitions and the storyteller timer.
 * <p>
 * Phases are advanced manually by the storyteller via commands — there is no automatic progression.
 * The storyteller can start a Discussion timer; when it expires the gong sounds and players are
 * prompted to return to the town square. The gong can also be triggered directly via /botc gong.
 */
public class botcStageManager {
    /** Tick at which the game session should be torn down (-1 = not set). */
    private long closeTime = -1;
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
    private boolean timerStrikeGong = false;
    private String timerTitle = "";
    private long timerDurationTicks = 0;
    private long timerStartTick = 0;

    // Gong strike sequencing
    private long lastKnownTick = 0;
    private long gongNextStrikeAt = -1;
    private int gongStrikesLeft = 0;
    private long pendingTitleAt = -1;

    public botcStageManager() {
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
     * @param title display title shown on the boss bar (null/blank = no label)
     * @param strikeGong whether to strike the gong when the timer expires
     */
    public void startTimer(long currentTick, long durationTicks, String title, boolean strikeGong) {
        this.timerActive = true;
        this.timerDurationTicks = durationTicks;
        this.timerStartTick = currentTick;
        this.timerTitle = title != null ? title.strip() : "";
        this.timerStrikeGong = strikeGong;
        if (this.stateContext != null) {
            long secs = durationTicks / 20;
            String label = this.timerTitle.isBlank() ? "" : " " + this.timerTitle;
            this.stateContext.broadcast(Text.literal(
                "Timer started" + label + " (" + secs + "s)").formatted(Formatting.AQUA));
        }
    }

    /** Stop the current timer without striking the gong. */
    public void stopTimer() {
        this.timerActive = false;
        if (this.stateContext != null) {
            this.stateContext.broadcast(Text.literal("Timer stopped.").formatted(Formatting.GRAY));
        }
    }

    /** Stop the current timer and immediately strike the gong. */
    public void stopTimerAndGong(GameSpace space) {
        this.timerActive = false;
        if (this.stateContext != null) {
            this.stateContext.broadcast(Text.literal("Timer stopped — gong struck.").formatted(Formatting.GRAY));
        }
        this.strikeGong(space);
    }

    /**
     * Strike the gong: plays the gong sound with gold particles on the first strike,
     * then fires two further strikes ~0.7 s apart via the tick loop.
     * Only triggered at end of a Discussion timer or via /botc gong.
     */
    public void strikeGong(GameSpace space) {
        this.playStrike(space, true);
        // Schedule two follow-up dings (14 ticks ≈ 0.7 s apart)
        this.gongStrikesLeft = 2;
        this.gongNextStrikeAt = this.lastKnownTick + 14;
        // Show title after the animation has cleared (3 strikes finish at ~28 ticks; add buffer)
        this.pendingTitleAt = this.lastKnownTick + 45;
    }

    /**
     * Play a single gong strike.
     * @param withParticles if true, spawns gold dust particles around each player
     */
    private void playStrike(GameSpace space, boolean withParticles) {
        PlayerSet players = space.getPlayers();

        if (withParticles) {
            DustParticleEffect goldDust = new DustParticleEffect(0xFFD700, 2.0f);
            for (ServerPlayerEntity player : players) {
                if (player.isSpectator()) continue;
                if (!PolymerResourcePackUtils.hasMainPack(player)) continue;

                TitleUtil.showTotemEffect(player, new ItemStack(Items.BELL));

                player.networkHandler.sendPacket(new ParticleS2CPacket(
                    goldDust, true, true,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    0.5f, 0.5f, 0.5f, 0.05f, 15
                ));
            }
        }

        players.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.0F, 0.5F);
        players.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.5F, 1.0F);
    }

    /** Per-tick update: handles pre-start countdown, timer expiry, and close sequence. */
    public IdleTickResult tick(long time, GameSpace space) {
        this.lastKnownTick = time;

        if ((time % 200) == 0) {
            golden.botc_mc.botc_mc.botc.LOGGER.trace("StageManager tick={}", time);
        }

        if (this.closeTime > 0) {
            if (time >= this.closeTime) return IdleTickResult.GAME_CLOSED;
            return IdleTickResult.TICK_FINISHED;
        }

        if (space.getPlayers().isEmpty() && this.hadPlayers) {
            this.closeTime = time + (5 * 20);
            this.lifecycleStatus = GameLifecycleStatus.STOPPING;
            if (this.stateContext != null) {
                this.stateContext.broadcast(Text.literal("No players remain; closing game."));
            }
            return IdleTickResult.GAME_FINISHED;
        }

        if (!space.getPlayers().isEmpty()) this.hadPlayers = true;

        if (this.pendingTitleAt > 0 && time >= this.pendingTitleAt) {
            space.getPlayers().showTitle(
                Text.translatable("gui.botc-mc.gong").formatted(Formatting.GOLD), 80);
            this.pendingTitleAt = -1;
        }

        if (this.timerActive && this.getTimerTicksRemaining(time) <= 0) {
            this.timerActive = false;
            if (this.timerStrikeGong) {
                this.strikeGong(space);
            }
        }

        if (this.gongStrikesLeft > 0 && time >= this.gongNextStrikeAt) {
            this.playStrike(space, false);
            this.gongStrikesLeft--;
            this.gongNextStrikeAt = time + 14;
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

    /** Result codes from an idle tick evaluation. */
    public enum IdleTickResult {
        /** Continue processing normally. */ CONTINUE_TICK,
        /** Do not progress game logic this tick. */ TICK_FINISHED,
        /** Game finished and transitioning to close countdown. */ GAME_FINISHED,
        /** Game fully closed and should be torn down. */ GAME_CLOSED
    }
}
