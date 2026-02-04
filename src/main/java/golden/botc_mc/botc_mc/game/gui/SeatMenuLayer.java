package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;

/**
 * Menu layer for actions on player and storyteller seats.
 */
public class SeatMenuLayer extends Layer {

    /**
     * Constructor for a SeatMenuLayer for a PlayerSeat.
     * @param gui The GrimoireGUI instance.
     * @param seat The PlayerSeat to manage.
     */
    public SeatMenuLayer(GrimoireGUI gui, PlayerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>(9);
        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.cancel"), ButtonIcon.CLOSE, (i, c, a, g) -> gui.reopen()));
        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.botc-mc.add_reminder"), ButtonIcon.ADD, (i, c, a, g) -> gui.addReminder(seat)));
        if (seat.isAlive()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.kill"), ButtonIcon.KILL, (i, c, a, g) -> {
                seat.kill();
                gui.reopen(seat);
            }));
        } else {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.revive"), ButtonIcon.REVIVE, (i, c, a, g) -> {
                        seat.revive();
                        gui.reopen(seat);
            }));
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

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }

    /**
     * Constructor for SeatMenuLayer for StorytellerSeat.
     * Buttons:
     * - Close Menu
     * - Kill Player / Revive Player
     * - Step Up / Step Down
     * - Edit Grimoire
     * @param gui The GrimoireGUI instance.
     * @param seat The StorytellerSeat to manage.
     */
    public SeatMenuLayer(GrimoireGUI gui, StorytellerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>(9);
        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.cancel"), ButtonIcon.CLOSE, (i, c, a, g) -> gui.reopen()));
        if (seat.isAlive()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.kill"), ButtonIcon.KILL, (i, c, a, g) -> {
                        seat.kill();
                        gui.reopen(seat);
            }));
        } else {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.revive"), ButtonIcon.REVIVE, (i, c, a, g) -> {
                        seat.revive();
                        gui.reopen(seat);
            }));
        }
        if (seat.hasPlayerEntity()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.step_down"), ButtonIcon.DOWN, (i, c, a, g) -> {
                        seat.removePlayerEntity();
                        gui.reopen();
            }));
        } else {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.step_up"), ButtonIcon.UP, (i, c, a, g) -> {
                        seat.setPlayerEntity(gui.getPlayer());
                        gui.reopen();
            }));
        }

        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.botc-mc.edit_grim"), ButtonIcon.EDIT, (i, c, a, g) -> gui.editGrimoire()));

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }
}