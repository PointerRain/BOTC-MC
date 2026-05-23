package golden.botc_mc.botc_mc.game.state;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Finite state machine for BOTC game phase tracking.
 * Transitions are driven exclusively by storyteller commands — there is no automatic progression.
 */
public class BotcStateMachine {
    private final Map<BotcGameState, Consumer<BotcStateContext>> entryActions;
    private final Map<BotcGameState, Consumer<BotcStateContext>> exitActions;
    private final List<Consumer<BotcGameState>> stateListeners;

    private BotcGameState currentState;

    public BotcStateMachine() {
        this.entryActions = new EnumMap<>(BotcGameState.class);
        this.exitActions = new EnumMap<>(BotcGameState.class);
        this.stateListeners = new ArrayList<>();
        this.currentState = BotcGameState.SETUP;
    }

    public BotcGameState getCurrentState() {
        return this.currentState;
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

    /** Initialize the state machine in SETUP state and fire the entry action.
     * @param context state context
     */
    public void start(BotcStateContext context) {
        this.currentState = BotcGameState.SETUP;
        if (context != null) {
            Consumer<BotcStateContext> entry = this.entryActions.get(this.currentState);
            if (entry != null) entry.accept(context);
        }
        this.notifyStateListeners();
    }

    /** Manually transition to a new state, firing exit/entry callbacks.
     * @param next target state
     * @param context state context
     */
    public void transitionTo(BotcGameState next, BotcStateContext context) {
        if (next == null || next == this.currentState) return;

        if (context != null) {
            Consumer<BotcStateContext> exit = this.exitActions.get(this.currentState);
            if (exit != null) exit.accept(context);
        }

        this.currentState = next;

        if (context != null) {
            Consumer<BotcStateContext> entry = this.entryActions.get(this.currentState);
            if (entry != null) entry.accept(context);
        }

        this.notifyStateListeners();
    }

    private void notifyStateListeners() {
        for (Consumer<BotcGameState> listener : this.stateListeners) {
            listener.accept(this.currentState);
        }
    }
}
