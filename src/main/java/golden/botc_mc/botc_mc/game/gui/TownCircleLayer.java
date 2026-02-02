package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Layer for displaying player seats in the Grimoire GUI.
 * Arranges seats based on the selected layout style to maximise number of visible reminders.
 * @see GrimoireGUI.LayoutStyle
 */
public class TownCircleLayer extends Layer {
    public TownCircleLayer(GrimoireGUI gui, GrimoireGUI.LayoutStyle layout) {
        super(gui.getHeight(), gui.getWidth());

        int seatCount = gui.seatManager.getSeatCount();
        int maxReminders = GrimoireGUI.LayoutStyle.getMaxReminders(layout);

        for (int n = 0; n < seatCount; n++) {
            PlayerSeat seat = gui.seatManager.getSeatFromNumber(n+1);

            ItemStack headItem = PlayerHeadItemStack.of(seat, n + 1);
            ItemStack tokenItem = TokenItemStack.of(seat);
            List<GuiElement> reminderItems = gui.getReminderItems(seat, seat.getReminders(), maxReminders);

            GuiElementInterface.ClickCallback headCallback = (i, c, a, g) -> {
                if (c == ClickType.MOUSE_LEFT_SHIFT) {
                    if (seat.isAlive()) seat.kill();
                    else seat.revive();
                    gui.reopen(seat);
                }
                gui.showSeatPopout(seat);
            };
            GuiElementInterface.ClickCallback tokenCallback = (i, c, a, g) -> {
                switch (c) {
                    case MOUSE_RIGHT -> gui.selectCharacter(seat);
                    case MOUSE_LEFT_SHIFT -> {
                        seat.toggleAlignment();
                        gui.reopen(seat);
                    }
                    case MOUSE_RIGHT_SHIFT -> seat.setCharacter(botcCharacter.EMPTY);
                    default -> gui.showSeatPopout(seat);
                }
            };

            this.setSlot(getIndexForLayout(layout, n, 0, seatCount), headItem, headCallback);
            this.setSlot(getIndexForLayout(layout, n, 1, seatCount), tokenItem, tokenCallback);
            for (int i = 0; i < reminderItems.size(); i++) {
                this.setSlot(getIndexForLayout(layout, n, 2 + i, seatCount), reminderItems.get(i));
            }
        }
    }

    /**
     * Calculate the slot index for a given layout style, seat number, and item index.
     * @param layout The layout style to use.
     * @param seatNumber The seat number (0-indexed).
     * @param i The item index for the seat (0=head, 1=token, 2+=reminders).
     * @param seatCount The total number of seats.
     * @return The calculated slot index.
     */
    private int getIndexForLayout(GrimoireGUI.LayoutStyle layout, int seatNumber, int i, int seatCount) {
        int perColumn = seatCount / 2 + seatCount % 2;
        return switch (layout) {
            case UNKNOWN -> 0;
            case SINGLE_COLUMN -> 9 * seatNumber + i;
            case SINGLE_ROW -> seatNumber + 9 * i;
            case TWO_COLUMNS -> seatNumber < perColumn ?
                    9 * seatNumber + 8 - i :
                    9 * (this.getHeight() - 5 - seatNumber % perColumn) + i;
            case TWO_ROWS -> seatNumber < perColumn ?
                    seatNumber + i * 9 :
                    (6 - i) * 9 - seatNumber % perColumn - 1;
        };
    }
}
