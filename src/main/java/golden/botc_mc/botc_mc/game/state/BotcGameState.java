package golden.botc_mc.botc_mc.game.state;

/**
 * Core phases for the Blood on the Clocktower loop. The order reflects the default progression
 * handled by the finite state machine, starting at lobby setup and ending when the match resolves.
 */
public enum BotcGameState {
    /** Waiting for players to finish spawning and the storyteller to start the match. */
    LOBBY,
    /** Countdown/setup before day discussion opens. */
    PRE_DAY,
    /** Day discussion where players talk openly. */
    DAY_DISCUSSION,
    /** Nomination voting window. */
    NOMINATION,
    /** Execution (final vote + resolution). */
    EXECUTION,
    /** Night phase where night abilities resolve. */
    NIGHT,
    /** Cleanup and end screens. */
    END
}

