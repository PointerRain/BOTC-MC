package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;

public class PlayerMenuLayer extends Layer {

    public PlayerMenuLayer(GrimoireGUI gui, PlayerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>();
        ItemStack changeCharacterButton = new ItemStack(Items.PAPER);
        changeCharacterButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Change Character"));
        elements.add(new GuiElement(changeCharacterButton, (i, c, a, g) ->
            gui.selectCharacter(seat)));
        if (seat.getAlignment() != null) {
            ItemStack changeAlignmentButton = new ItemStack(Items.PAPER);
            changeAlignmentButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Change Alignment"));
            elements.add(new GuiElement(changeAlignmentButton, (i, c, a, g) ->
            {seat.toggleAlignment();
            gui.reopen(seat);}));
        }
        if (!seat.getReminders().isEmpty()) {
            ItemStack removeRemindersButton = new ItemStack(Items.PAPER);
            removeRemindersButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Remove Reminders"));
            elements.add(new GuiElement(removeRemindersButton, (i, c, a, g) ->
                seat.clearReminders()));
        }
        if (seat.isAlive()) {
            ItemStack killReviveButton = new ItemStack(Items.PAPER);
            killReviveButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Kill Player"));
            elements.add(new GuiElement(killReviveButton, (i, c, a, g) ->
            {seat.kill();
            gui.reopen(seat);}));
        } else {
            ItemStack killReviveButton = new ItemStack(Items.PAPER);
            killReviveButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Revive Player"));
            elements.add(new GuiElement(killReviveButton, (i, c, a, g) ->
            {seat.revive();
            gui.reopen(seat);}));
            ItemStack removeDeadVoteButton = new ItemStack(Items.PAPER);
            removeDeadVoteButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Remove Dead Vote"));
            elements.add(new GuiElement(removeDeadVoteButton, (i, c, a, g) -> {}));
        }

        ItemStack startNominationButton = new ItemStack(Items.PAPER);
        startNominationButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Start Nomination"));
        elements.add(new GuiElement(startNominationButton, (i, c, a, g) -> {}));
        if (seat.hasPlayerEntity()) {
            ItemStack emptySeatButton = new ItemStack(Items.PAPER);
            emptySeatButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Empty Seat"));
            elements.add(new GuiElement(emptySeatButton, (i, c, a, g) -> {
                seat.removePlayerEntity();
                gui.reopen(seat);
            }));
        } else {
            ItemStack joinSeatButton = new ItemStack(Items.PAPER);
            joinSeatButton.set(DataComponentTypes.CUSTOM_NAME, Text.of("Join Seat"));
            elements.add(new GuiElement(joinSeatButton, (i, c, a, g) -> {
                seat.setPlayerEntity(gui.player);
                gui.reopen(seat);
            }));
        }

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }
}

// Buttons
// Change character
// Change alignment
// Add / Remove reminders
// Kill / Revive
// Remove dead vote
// Start nomination
// Empty seat
