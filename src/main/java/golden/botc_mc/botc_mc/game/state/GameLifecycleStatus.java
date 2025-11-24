package golden.botc_mc.botc_mc.game.state;

/**
 * High-level lifecycle flags for the storyteller activity. The values are coarse so subsystems can
 * cheaply gate behaviors without needing to understand every phase of the finite state machine.
 */
public enum GameLifecycleStatus {
    /** Game is inactive and no players should be interacting with the arena. */
    STOPPED,
    /** Game is counting down / pre-roll and waiting to enter the first live phase. */
    STARTING,
    /** The main gameplay loop is active (day/night rotations). */
    RUNNING,
    /** Game is winding down and will close shortly. */
    STOPPING;

    /** Determine whether the lifecycle status represents an active game (starting or running).
     * @return true if active
     */
    public boolean isActive() {
        return this == STARTING || this == RUNNING;
    }
}
