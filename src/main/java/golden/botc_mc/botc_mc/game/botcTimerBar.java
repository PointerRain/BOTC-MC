package golden.botc_mc.botc_mc.game;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;

/**
 * Boss bar display for the active storyteller timer.
 * Hidden when no timer is running; shows the timer title and a MM:SS countdown
 * while a Discussion timer is active.
 */
public record botcTimerBar(BossBarWidget widget) {

    public static botcTimerBar of(GlobalWidgets widgets) {
        var bar = widgets.addBossBar(
            Text.literal("Waiting for the game to start..."),
            BossBar.Color.GREEN,
            BossBar.Style.NOTCHED_10
        );
        return new botcTimerBar(bar);
    }

    /**
     * Update the boss bar each tick.
     * @param timerActive whether a storyteller timer is running
     * @param timerTitle title of the active timer
     * @param ticksRemaining ticks remaining on the timer
     * @param totalTicks total duration of the timer in ticks
     */
    public void update(boolean timerActive, String timerTitle, long ticksRemaining, long totalTicks) {
        if (timerActive) {
            this.widget.setVisible(true);
            long clampedTicks = Math.max(0, ticksRemaining);
            long clampedTotal = Math.max(1, totalTicks);
            // Update every second to avoid unnecessary packets
            if (clampedTicks % 20 == 0) {
                long secs = clampedTicks / 20;
                long minutes = secs / 60;
                long seconds = secs % 60;
                this.widget.setTitle(Text.literal(timerTitle + " - " + String.format("%02d:%02d", minutes, seconds)));
                this.widget.setProgress((float) clampedTicks / (float) clampedTotal);
            }
        } else {
            this.widget.setVisible(false);
        }
    }
}
