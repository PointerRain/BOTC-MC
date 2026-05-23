package golden.botc_mc.botc_mc.game.state;

/**
 * Core phases for the Blood on the Clocktower game loop.
 * Storytellers advance phases manually via commands.
 * Setup is day 0; first-night actions are on night 1, then day 1, night 2, and so on.
 */
public enum BotcGameState {
    /** Pre-game setup (day 0). Storyteller prepares roles and distributes them. */
    SETUP,
    /** Day phase where players discuss openly. */
    DAY,
    /** Night phase where night abilities resolve. */
    NIGHT,
    /** Game over. */
    END
}
