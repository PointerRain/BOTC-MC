package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.item.ItemStack;

import java.util.List;

import static golden.botc_mc.botc_mc.game.gui.GrimoireGUI.getReminderItems;

public class PlayerPopoutLayer extends Layer {
    public PlayerPopoutLayer(GrimoireGUI gui, PlayerSeat seat, int seatNumber) {
        super(1, Math.min(9, 2 + seat.getReminders().size()));

        ItemStack headItem = PlayerHeadItemStack.of(seat, seatNumber);
        ItemStack tokenItem = TokenItemStack.of(seat);

        GuiElementInterface.ClickCallback tokenCallback = (i, c, a, g) ->
                gui.selectCharacter(seat);

        List<ItemStack> reminderItems = getReminderItems(seat.getReminders(), 16);
        botc.LOGGER.info("Showing popout for seat {} with {} reminders.", seatNumber, reminderItems.size());
//        int offset = 62 + (9 - Math.min(reminderItems.size(), 7)) / 2;
        this.setSlot(0, headItem);
        this.setSlot(1, tokenItem, tokenCallback);
        for (int i = 0; i < reminderItems.size(); i++) {
            this.setSlot(2 + i, reminderItems.get(i));
        }
    }
}
