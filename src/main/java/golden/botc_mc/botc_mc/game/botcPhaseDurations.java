package golden.botc_mc.botc_mc.game;

/**
 * Phase durations (seconds) for each game phase.
 * <p>
 * This record represents the configured durations used by the state machine. Values are specified
 * in seconds and converted to ticks (20 ticks = 1 second) when used by the game loop.
 *
 * @param dayDiscussionSecs seconds for day discussion
 * @param nominationSecs seconds for nomination window
 * @param executionSecs seconds for execution window
 * @param nightSecs seconds for night phase
 */
public record botcPhaseDurations(int dayDiscussionSecs,
                                 int nominationSecs,
                                 int executionSecs,
                                 int nightSecs) {

    /**
     * Default phase durations
     * @return default botcPhaseDurations instance
     */
    public static botcPhaseDurations defaults() {
        return new botcPhaseDurations(120, 45, 20, 60);
    }

    /** Compute ticks for a state.
     * @param state game state
     * @return duration in ticks (>=1)
     */
    public long durationTicks(golden.botc_mc.botc_mc.game.state.BotcGameState state) {
        long seconds = switch (state) {
            case DAY_DISCUSSION -> this.dayDiscussionSecs;
            case NOMINATION -> this.nominationSecs;
            case EXECUTION -> this.executionSecs;
            case NIGHT -> this.nightSecs;
            default -> 5; // short placeholder (e.g., countdown/end) when no explicit duration is required
        };

        return Math.max(1L, seconds * 20L);
    }
}
