package golden.botc_mc.botc_mc.game.state;

import golden.botc_mc.botc_mc.game.botcPhaseDurations;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Finite state machine driving BOTC game phase progression.
 * Minimal finite state machine responsible for sequencing the Blood on the Clocktower loop.
 * Each tick, the owning manager calls {@link #tick(long, BotcStateContext)} to decide whether to advance states
 * or perform actions. The FSM stores elapsed ticks per state and consults configured durations.
 */
public class BotcStateMachine {
    private final Map<BotcGameState, Consumer<BotcStateContext>> entryActions;
    private final Map<BotcGameState, Consumer<BotcStateContext>> exitActions;
    private final Map<BotcGameState, BotcGameState> defaultTransitions;
    private botcPhaseDurations durations;
    private final List<Consumer<BotcGameState>> stateListeners;

    private BotcGameState currentState;
    private long stateEnteredTick;
    private long ticksInState;

    /** Construct with initial phase durations.
     * @param durations configured durations
     */
    public BotcStateMachine(botcPhaseDurations durations) {
        this.durations = durations;
        this.entryActions = new EnumMap<>(BotcGameState.class);
        this.exitActions = new EnumMap<>(BotcGameState.class);
        this.defaultTransitions = new EnumMap<>(BotcGameState.class);
        this.stateListeners = new ArrayList<>();
        this.currentState = BotcGameState.LOBBY;
    }

    /**
     * Gets the current finite state machine state.
     * @return current BOTC game state
     */
    public BotcGameState getCurrentState() {
        return this.currentState;
    }

    /**
     * Number of ticks spent in the current state (resets on transition).
     * @return ticks spent in current state
     */
    public long getTicksInState() {
        return this.ticksInState;
    }

    /** Register action invoked when entering a state.
     * @param state target state
     * @param action callback with context
     */
    public void onEnter(BotcGameState state, Consumer<BotcStateContext> action) {
        this.entryActions.put(state, action);
    }

    /** Register action invoked when exiting a state.
     * @param state target state
     * @param action callback with context
     */
    public void onExit(BotcGameState state, Consumer<BotcStateContext> action) {
        this.exitActions.put(state, action);
    }

    /** Listen for state change events.
     * @param listener consumer receiving new state
     */
    public void onStateChanged(Consumer<BotcGameState> listener) {
        if (listener != null) {
            this.stateListeners.add(listener);
        }
    }

    /** Set default transition mapping.
     * @param from source state
     * @param to destination state
     */
    public void setDefaultTransition(BotcGameState from, BotcGameState to) {
        this.defaultTransitions.put(from, to);
    }

    /** Update phase durations.
     * @param durations new durations
     */
    public void setDurations(botcPhaseDurations durations) {
        if (durations != null) {
            this.durations = durations;
        }
    }

    /** Begin processing with initial context.
     * @param tick starting tick
     * @param context state context
     */
    public void start(long tick, BotcStateContext context) {
        this.currentState = BotcGameState.LOBBY;
        this.stateEnteredTick = tick;
        this.ticksInState = 0;
        if (context != null) {
            this.fireEntry(context);
        }
    }

    /** Advance state machine by one tick.
     * @param currentTick current tick
     * @param context context
     */
    public void tick(long currentTick, BotcStateContext context) {
        this.ticksInState = currentTick - this.stateEnteredTick;
        long duration = this.durations.durationTicks(this.currentState);

        if (context == null) {
            return;
        }

        if (this.ticksInState >= duration) {
            this.transitionTo(this.defaultTransitions.getOrDefault(this.currentState, BotcGameState.END), currentTick, context);
        }
    }

    /** Transition explicitly to next state.
     * @param next target state
     * @param currentTick current tick
     * @param context context
     */
    public void transitionTo(BotcGameState next, long currentTick, BotcStateContext context) {
        if (next == null) {
            next = BotcGameState.END;
        }
        if (next == this.currentState) {
            return;
        }

        if (context != null) {
            Consumer<BotcStateContext> exit = this.exitActions.get(this.currentState);
            if (exit != null) {
                exit.accept(context);
            }
        }

        this.currentState = next;
        this.stateEnteredTick = currentTick;
        this.ticksInState = 0;

        if (context != null) {
            this.fireEntry(context);
        }
    }

    private void fireEntry(BotcStateContext context) {
        Consumer<BotcStateContext> entry = this.entryActions.get(this.currentState);
        if (entry != null) {
            entry.accept(context);
        } else if (context != null) {
            context.broadcast(Text.literal("Entering "+ this.currentState.name()));
        }
        this.notifyStateListeners();
    }

    private void notifyStateListeners() {
        for (Consumer<BotcGameState> listener : this.stateListeners) {
            listener.accept(this.currentState);
        }
    }
}
