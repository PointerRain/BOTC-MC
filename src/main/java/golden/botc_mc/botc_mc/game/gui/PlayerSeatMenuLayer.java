package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menu layer for actions on player and storyteller seats.
 */
public class PlayerSeatMenuLayer extends AbstractSeatMenuLayer<PlayerSeat> {

    /**
     * Constructor for a PlayerSeatMenuLayer for a PlayerSeat.
     * @param gui The GrimoireGUI instance.
     * @param seat The PlayerSeat to manage.
     */
    public PlayerSeatMenuLayer(GrimoireGUI gui, PlayerSeat seat) {
        super(gui, seat);
    }

    @Override
    protected List<GuiElement> getItems() {
        List<GuiElement> elements = super.getItems();

        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.botc-mc.add_reminder"), ButtonIcon.ADD, (i, c, a, g) -> gui.addReminder(seat)));
        if (!seat.isAlive()) {
            if (seat.canGhostVote()) {
                elements.add(ButtonBuilder.buildButton(
                        Text.translatable("gui.botc-mc.remove_ghost_vote"), ButtonIcon.REMOVE_VOTE, (i, c, a, g) -> {
                            seat.removeGhostVote();
                            gui.reopen(seat);
                }));
            } else {
                elements.add(ButtonBuilder.buildButton(
                        Text.translatable("gui.botc-mc.return_ghost_vote"), ButtonIcon.RETURN_VOTE, (i, c, a, g) -> {
                            seat.restoreGhostVote();
                            gui.reopen(seat);
                }));
            }
        }

        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.botc-mc.start_nomination"), ButtonIcon.NOMINATE, (i, c, a, g) -> {
                    // Implement start nomination logic here
        }));

        if (seat.hasPlayerEntity()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.empty_seat"), ButtonIcon.DELETE, (i, c, a, g) -> {
                        seat.removePlayerEntity();
                        gui.reopen(seat);
                    }));
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.promote"), ButtonIcon.UP, (i, c, a, g) -> {
                        ServerPlayerEntity player = seat.getPlayerEntity();
                        StorytellerSeat newSeat = (StorytellerSeat) gui.seatManager.assignPlayerToStorytellerSeat(player);
                        gui.reopen(newSeat);
                    }));
        }

        return elements;
    }

    @Override
    protected void reopen() {
        this.gui.reopen(this.seat);
    }
}