package golden.botc_mc.botc_mc.game;

/** Minimal phase durations record (seconds per phase). */
public record botcPhaseDurations(int dayDiscussionSecs,
                                 int nominationSecs,
                                 int executionSecs,
                                 int nightSecs) {
    /** Convert a state to its configured duration in ticks (20 ticks/sec).
     * @param state game state whose duration to compute
     * @return ticks for the state, 0 if unmapped
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
