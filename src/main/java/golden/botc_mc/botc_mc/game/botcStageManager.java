package golden.botc_mc.botc_mc.game;

import com.google.common.collect.ImmutableSet;
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
import golden.botc_mc.botc_mc.game.state.BotcGameState;
import golden.botc_mc.botc_mc.game.state.BotcStateContext;
import golden.botc_mc.botc_mc.game.state.BotcStateMachine;

import java.util.Set;

public class botcStageManager {
    private long closeTime = -1;
    public long finishTime = -1;
    private long startTime = -1;
    private final Object2ObjectMap<ServerPlayerEntity, FrozenPlayer> frozen;
    private boolean setSpectator = false;
    private final BotcStateMachine stateMachine;
    private BotcStateContext stateContext;
    private botcPhaseDurations configuredDurations = botcPhaseDurations.defaults();
    // track whether any players have been present since the game opened
    private boolean hadPlayers = false;

    public botcStageManager() {
        this.frozen = new Object2ObjectOpenHashMap<>();
        this.stateMachine = new BotcStateMachine(botcPhaseDurations.defaults());
    }

    public void onOpen(long time, botcConfig config) {
        this.startTime = time - (time % 20) + (4 * 20) + 19;
        this.finishTime = this.startTime + (config.timeLimitSecs() * 20L);
        this.stateMachine.start(time, this.stateContext);
    }

    public void attachContext(GameSpace space, botcConfig config) {
        this.stateContext = new BotcStateContext(space);
        this.configureStates(config.phaseDurations());
    }

    /**
     * Signal the manager that players were present when the game opened. Call this from the
     * activity right after players are spawned so the manager won't consider an empty player
     * set (which can exist transiently) as a reason to close the game immediately.
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

        this.stateMachine.onEnter(BotcGameState.LOBBY, ctx -> ctx.broadcast(Text.literal("Waiting for storyteller...")));
        this.stateMachine.onEnter(BotcGameState.PRE_DAY, ctx -> ctx.broadcast(Text.literal("Day is about to begin!")));
        this.stateMachine.onEnter(BotcGameState.DAY_DISCUSSION, ctx -> ctx.broadcast(Text.literal("Day discussion has started.")));
        this.stateMachine.onEnter(BotcGameState.NOMINATION, ctx -> ctx.broadcast(Text.literal("Nomination window open.")));
        this.stateMachine.onEnter(BotcGameState.EXECUTION, ctx -> ctx.broadcast(Text.literal("Execution vote resolving...")));
        this.stateMachine.onEnter(BotcGameState.NIGHT, ctx -> ctx.broadcast(Text.literal("Night phase: storytellers resolving actions.")));
        this.stateMachine.onEnter(BotcGameState.END, ctx -> ctx.broadcast(Text.literal("Game closing.")));
    }

    public BotcGameState getCurrentState() {
        return this.stateMachine.getCurrentState();
    }

    public long getStateDuration() {
        return this.configuredDurations.durationTicks(this.stateMachine.getCurrentState());
    }

    public long getStateTicksRemaining() {
        long duration = this.getStateDuration();
        long elapsed = this.stateMachine.getTicksInState();
        return Math.max(0, duration - elapsed);
    }

    public IdleTickResult tick(long time, GameSpace space) {
        // Game has finished. Wait a few seconds before finally closing the game.
        if (this.closeTime > 0) {
            if (time >= this.closeTime) {
                return IdleTickResult.GAME_CLOSED;
            }
            return IdleTickResult.TICK_FINISHED;
        }

        // Game hasn't started yet. Display a countdown before it begins.
        if (this.startTime > time) {
            this.tickStartWaiting(time, space);
            return IdleTickResult.TICK_FINISHED;
        }

        // Game has just finished. Transition to the waiting-before-close state.
        // Only treat an empty player set as a finish condition after the game has started.
        if (time > this.finishTime || (time >= this.startTime && space.getPlayers().isEmpty() && this.hadPlayers)) {
            if (!this.setSpectator) {
                this.setSpectator = true;
                for (ServerPlayerEntity player : space.getPlayers()) {
                    player.changeGameMode(GameMode.SPECTATOR);
                }
            }

            this.closeTime = time + (5 * 20);
            // server-side debug log to help diagnose immediate close issues
            System.out.println("[BOTC] startTime=" + this.startTime + " finishTime=" + this.finishTime + " closeTime=" + this.closeTime + " now=" + time + " players=" + space.getPlayers().participants().size() + " hadPlayers=" + this.hadPlayers);
            if (this.stateContext != null) {
                String reason = space.getPlayers().isEmpty() ? "No players remain; closing game." : "Game time finished; closing game.";
                this.stateContext.broadcast(Text.literal(reason));
            }
            return IdleTickResult.GAME_FINISHED;
        }

        // update hadPlayers when someone exists in the space
        if (!space.getPlayers().isEmpty()) {
            this.hadPlayers = true;
        }

        this.stateMachine.tick(time, this.stateContext);
        return IdleTickResult.CONTINUE_TICK;
    }

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

    public static class FrozenPlayer {
        public Vec3d lastPos;
    }

    public enum IdleTickResult {
        CONTINUE_TICK,
        TICK_FINISHED,
        GAME_FINISHED,
        GAME_CLOSED
    }
}
