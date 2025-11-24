package golden.botc_mc.botc_mc.game;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;
import golden.botc_mc.botc_mc.game.state.BotcGameState;

/**
 * Boss bar UI helper for displaying either a generic waiting countdown or the active
 * phase timer. Progress is updated every second (20 ticks) to reduce packet spam.
 */
public final class botcTimerBar {
    private final BossBarWidget widget;

    /** Create a timer bar bound to global widgets.
     * @param widgets global HUD widgets
     */
    public botcTimerBar(GlobalWidgets widgets) {
        Text title = Text.literal("Waiting for the game to start...");
        this.widget = widgets.addBossBar(title, BossBar.Color.GREEN, BossBar.Style.NOTCHED_10);
    }

    /** Update raw tick counts.
     * @param ticksUntilEnd remaining ticks in phase
     * @param totalTicksUntilEnd total ticks for phase
     */
    public void update(long ticksUntilEnd, long totalTicksUntilEnd) {
        if (ticksUntilEnd % 20 == 0) {
            this.widget.setTitle(this.getText(ticksUntilEnd));
            this.widget.setProgress((float) ticksUntilEnd / totalTicksUntilEnd);
        }
    }

    /** Format a mm:ss string for the waiting countdown. */
    private Text getText(long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;

        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%02d:%02d left", minutes, seconds);

        return Text.literal(time);
    }

    /** Update phase-specific boss bar title and progress.
     * @param state current game state
     * @param ticksRemaining ticks remaining in state
     * @param totalTicks total ticks for state
     */
    public void updatePhase(BotcGameState state, long ticksRemaining, long totalTicks) {
        if (totalTicks <= 0) {
            totalTicks = 1;
        }
        if (ticksRemaining < 0) {
            ticksRemaining = 0;
        }

        if (ticksRemaining % 20 == 0) {
            this.widget.setTitle(this.getPhaseText(state, ticksRemaining));
            this.widget.setProgress((float) ticksRemaining / (float) totalTicks);
        }
    }

    /** Format a mm:ss string including the phase name. */
    private Text getPhaseText(BotcGameState state, long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;
        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%s - %02d:%02d", state.name().replace('_', ' '), minutes, seconds);
        return Text.literal(time);
    }
}
