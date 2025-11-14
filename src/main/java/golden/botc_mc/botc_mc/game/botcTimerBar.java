package golden.botc_mc.botc_mc.game;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;
import golden.botc_mc.botc_mc.game.state.BotcGameState;

public final class botcTimerBar {
    private final BossBarWidget widget;

    public botcTimerBar(GlobalWidgets widgets) {
        Text title = Text.literal("Waiting for the game to start...");
        this.widget = widgets.addBossBar(title, BossBar.Color.GREEN, BossBar.Style.NOTCHED_10);
    }

    public void update(long ticksUntilEnd, long totalTicksUntilEnd) {
        if (ticksUntilEnd % 20 == 0) {
            this.widget.setTitle(this.getText(ticksUntilEnd));
            this.widget.setProgress((float) ticksUntilEnd / totalTicksUntilEnd);
        }
    }

    private Text getText(long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;

        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%02d:%02d left", minutes, seconds);

        return Text.literal(time);
    }

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

    private Text getPhaseText(BotcGameState state, long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;
        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%s - %02d:%02d", state.name().replace('_', ' '), minutes, seconds);
        return Text.literal(time);
    }
}
