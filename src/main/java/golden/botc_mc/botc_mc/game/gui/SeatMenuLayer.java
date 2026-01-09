package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;

public class SeatMenuLayer extends Layer {

    public SeatMenuLayer(GrimoireGUI gui, PlayerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>(9);
        elements.add(buildButton(Text.of("Close Menu"), (i, c, a, g) -> gui.reopen()));
        elements.add(buildButton(Text.of("Change Character"), (i, c, a, g) -> gui.selectCharacter(seat)));
        if (seat.getAlignment() != null) {
            elements.add(buildButton(Text.of("Change Alignment"), (i, c, a, g) -> {
                seat.toggleAlignment();
                gui.reopen(seat);
            }));
        }
        elements.add(buildButton(Text.of("Add Reminder"), (i, c, a, g) -> gui.addReminder(seat)));
        if (!seat.getReminders().isEmpty()) {
            elements.add(buildButton(Text.of("Remove Reminders"), (i, c, a, g) -> seat.clearReminders()));
        }
        if (seat.isAlive()) {
            elements.add(buildButton(Text.of("Kill Player"), (i, c, a, g) -> {
                seat.kill();
                gui.reopen(seat);
            }));
        } else {
            elements.add(buildButton(Text.of("Revive Player"), (i, c, a, g) -> {
                seat.revive();
                gui.reopen(seat);
            }));
            elements.add(buildButton(Text.of("Remove Dead Vote"), (i, c, a, g) -> {
                // Implement removing dead vote logic here
            }));
        }
        elements.add(buildButton(Text.of("Start Nomination"), (i, c, a, g) -> {
            // Implement start nomination logic here
        }));
        if (seat.hasPlayerEntity()) {
            elements.add(buildButton(Text.of("Empty Seat"), (i, c, a, g) -> {
                seat.removePlayerEntity();
                gui.reopen(seat);
            }));
        } else {
            elements.add(buildButton(Text.of("Join Seat"), (i, c, a, g) -> {
                seat.setPlayerEntity(gui.player);
                gui.reopen(seat);
            }));
        }

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }

    public SeatMenuLayer(GrimoireGUI gui, StorytellerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>(9);
        elements.add(buildButton(Text.of("Close Menu"), (i, c, a, g) -> gui.reopen()));
        elements.add(buildButton(Text.of("Change Character"), (i, c, a, g) -> gui.selectCharacter(seat)));
        if (seat.isAlive()) {
            elements.add(buildButton(Text.of("Kill Player"), (i, c, a, g) -> {
                seat.kill();
                gui.reopen();
            }));
        } else {
            elements.add(buildButton(Text.of("Revive Player"), (i, c, a, g) -> {
                seat.revive();
                gui.reopen();
            }));
        }
        if (seat.hasPlayerEntity()) {
            elements.add(buildButton(Text.of("Step Down"), (i, c, a, g) -> {
                seat.removePlayerEntity();
                gui.reopen();
            }));
        } else {
            elements.add(buildButton(Text.of("Step Up"), (i, c, a, g) -> {
                seat.setPlayerEntity(gui.player);
                gui.reopen();
            }));
        }

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }

    private static GuiElement buildButton(Text name, GuiElement.ClickCallback callback) {
        ItemStack itemButton = new ItemStack(Items.PAPER);
        itemButton.set(DataComponentTypes.CUSTOM_NAME, name);
        return new GuiElement(itemButton, callback);
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
