package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;

/**
 * Menu layer for actions on player and storyteller seats.
 */
public class SeatMenuLayer extends Layer {

    /**
     * Constructor for SeatMenuLayer for PlayerSeat.
     * Buttons:
     * - Close Menu
     * - Add Reminder
     * - Kill Player / Revive Player
     * - Remove Dead Vote (if dead)
     * - Start Nomination
     * - Empty Seat (if occupied)
     * - Promote to Storyteller (if occupied)
     * @param gui The GrimoireGUI instance.
     * @param seat The PlayerSeat to manage.
     */
    public SeatMenuLayer(GrimoireGUI gui, PlayerSeat seat) {
        super(1, 9);

        ArrayList<GuiElement> elements = new ArrayList<>(9);
        elements.add(buildButton(Text.of("Close Menu"), (i, c, a, g) -> gui.reopen()));
        elements.add(buildButton(Text.of("Add Reminder"), (i, c, a, g) -> gui.addReminder(seat)));
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
            elements.add(buildButton(Text.of("Promote to Storyteller"), (i, c, a, g) -> {
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
        elements.add(buildButton(Text.of("Close Menu"), (i, c, a, g) -> gui.reopen()));
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
                seat.setPlayerEntity(gui.getPlayer());
                gui.reopen();
            }));
        }

        elements.add(buildButton(Text.of("Edit Grimoire"), (i, c, a, g) -> gui.editGrimoire()));

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }

    /**
     * Build a simple button GuiElement with a paper icon and custom name and click callback.
     * TODO: Replace paper icon with custom button icons.
     * @param name The display name of the button.
     * @param callback The click callback for the button.
     * @return The constructed GuiElement button.
     */
    public static GuiElement buildButton(Text name, GuiElement.ClickCallback callback) {
        ItemStack itemButton = new ItemStack(Items.PAPER);
        itemButton.set(DataComponentTypes.CUSTOM_NAME, name);
        return new GuiElement(itemButton, callback);
    }

    // Button icons
    // Add       +
    // Delete    bin
    // Left      <
    // Right     >
    // Close     X
    // Shuffle   ><
    // Up        ^
    // Down      v
    // Edit      ✎
    // Kill      skull
    // Revive    skull
}