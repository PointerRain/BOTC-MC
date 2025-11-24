package golden.botc_mc.botc_mc.game;

/**
 * Boss bar UI helper for displaying either a generic waiting countdown or the active
 * phase timer. Progress is updated every second (20 ticks) to reduce packet spam.
 */
public record botcTimerBar(xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget widget) {
    // Factory replacing previous constructor
    public static botcTimerBar of(xyz.nucleoid.plasmid.api.game.common.GlobalWidgets widgets) {
        net.minecraft.text.Text title = net.minecraft.text.Text.literal("Waiting for the game to start...");
        var bar = widgets.addBossBar(title, net.minecraft.entity.boss.BossBar.Color.GREEN, net.minecraft.entity.boss.BossBar.Style.NOTCHED_10);
        return new botcTimerBar(bar);
    }

    /** Update phase-specific boss bar title and progress.
     * @param state current game state
     * @param ticksRemaining ticks remaining in state
     * @param totalTicks total ticks for state
     */
    public void updatePhase(golden.botc_mc.botc_mc.game.state.BotcGameState state, long ticksRemaining, long totalTicks) {
        if (totalTicks <= 0) totalTicks = 1;
        if (ticksRemaining < 0) ticksRemaining = 0;
        if (ticksRemaining % 20 == 0) {
            this.widget.setTitle(getPhaseText(state, ticksRemaining));
            this.widget.setProgress((float) ticksRemaining / (float) totalTicks);
        }
    }
    private net.minecraft.text.Text getPhaseText(golden.botc_mc.botc_mc.game.state.BotcGameState state, long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;
        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%s - %02d:%02d", state.name().replace('_', ' '), minutes, seconds);
        return net.minecraft.text.Text.literal(time);
    }
}
