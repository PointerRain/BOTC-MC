package golden.botc_mc.botc_mc.game;

/** Phase durations (seconds) for each game phase.
 * @param dayDiscussionSecs seconds for day discussion
 * @param nominationSecs seconds for nomination window
 * @param executionSecs seconds for execution window
 * @param nightSecs seconds for night phase
 */
public record botcPhaseDurations(int dayDiscussionSecs,
                                 int nominationSecs,
                                 int executionSecs,
                                 int nightSecs) {
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
