package golden.botc_mc.botc_mc.game.state;

import golden.botc_mc.botc_mc.game.botcPhaseDurations;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal finite state machine responsible for sequencing the Blood on the Clocktower loop.
 * Each tick, the owning manager calls {@link #tick(long, BotcStateContext)} to decide whether to advance states
 * or perform actions. The FSM stores elapsed ticks per state and consults configured durations.
 */
public final class BotcStateMachine {
    private final Map<BotcGameState, Consumer<BotcStateContext>> entryActions;
    private final Map<BotcGameState, Consumer<BotcStateContext>> exitActions;
    private final Map<BotcGameState, BotcGameState> defaultTransitions;
    private botcPhaseDurations durations;
    private final List<Consumer<BotcGameState>> stateListeners;

    private BotcGameState currentState;
    private long stateEnteredTick;
    private long ticksInState;

    public BotcStateMachine(botcPhaseDurations durations) {
        this.durations = durations;
        this.entryActions = new EnumMap<>(BotcGameState.class);
        this.exitActions = new EnumMap<>(BotcGameState.class);
        this.defaultTransitions = new EnumMap<>(BotcGameState.class);
        this.stateListeners = new ArrayList<>();
        this.currentState = BotcGameState.LOBBY;
    }

    public BotcGameState getCurrentState() {
        return this.currentState;
    }

    public long getTicksInState() {
        return this.ticksInState;
    }

    public void onEnter(BotcGameState state, Consumer<BotcStateContext> action) {
        this.entryActions.put(state, action);
    }

    public void onExit(BotcGameState state, Consumer<BotcStateContext> action) {
        this.exitActions.put(state, action);
    }

    public void onStateChanged(Consumer<BotcGameState> listener) {
        if (listener != null) {
            this.stateListeners.add(listener);
        }
    }

    public void setDefaultTransition(BotcGameState from, BotcGameState to) {
        this.defaultTransitions.put(from, to);
    }

    public void setDurations(botcPhaseDurations durations) {
        if (durations != null) {
            this.durations = durations;
        }
    }

    /** Resets to the initial state and fires the entry action. */
    public void start(long tick, BotcStateContext context) {
        this.currentState = BotcGameState.LOBBY;
        this.stateEnteredTick = tick;
        this.ticksInState = 0;
        if (context != null) {
            this.fireEntry(context);
        }
    }

    /** Advances the internal timer and transitions whenever the configured duration elapses. */
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

    /** Forces a manual transition regardless of elapsed time. */
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
