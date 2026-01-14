package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.item.ItemStack;

import java.util.List;


public class TownCircleLayer extends Layer {
    public TownCircleLayer(GrimoireGUI gui, GrimoireGUI.LayoutStyle layout) {
        super(gui.getHeight(), gui.getWidth());

        int maxReminders = GrimoireGUI.LayoutStyle.getMaxReminders(layout);

        for (int n = 0; n < gui.seatManager.getSeatCount(); n++) {
            PlayerSeat seat = gui.seatManager.getSeatFromNumber(n+1);

            ItemStack headItem = PlayerHeadItemStack.of(seat, n + 1);
            ItemStack tokenItem = TokenItemStack.of(seat);
            List<GuiElement> reminderItems = gui.getReminderItems(seat, seat.getReminders(), maxReminders);

            GuiElementInterface.ClickCallback headCallback = (i, c, a, g) -> {
                if (c == ClickType.MOUSE_LEFT_SHIFT) {
                    if (seat.isAlive()) seat.kill();
                    else seat.revive();
                }
                gui.showPlayerPopout(seat);
            };
            GuiElementInterface.ClickCallback tokenCallback = (i, c, a, g) -> {
                switch (c) {
                    case MOUSE_RIGHT -> gui.selectCharacter(seat);
                    case MOUSE_LEFT_SHIFT -> {
                        seat.toggleAlignment();
                        gui.reopen(seat);
                    }
                    case MOUSE_RIGHT_SHIFT -> {
                        seat.setCharacter(botcCharacter.EMPTY);
                    }
                    default -> gui.showPlayerPopout(seat);
                }
            };

            if (layout == GrimoireGUI.LayoutStyle.SINGLE_COLUMN) {
                this.setSlot(9 * n, headItem, headCallback);
                this.setSlot(9 * n + 1, tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(9 * n + 2 + i, reminderItems.get(i));
                }
            }
            if (layout == GrimoireGUI.LayoutStyle.SINGLE_ROW) {
                this.setSlot(n, headItem, headCallback);
                this.setSlot(n + 9, tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(n + 18 + 9 * i, reminderItems.get(i));
                }
            }
            if (layout == GrimoireGUI.LayoutStyle.TWO_COLUMNS) {
                int perColumn = gui.seatManager.getSeatCount() / 2 + gui.seatManager.getSeatCount() % 2;
                this.setSlot(n < perColumn ? 9 * n + 8 : 9 * ((gui.getHeight() - 5) - n % perColumn),
                        headItem, headCallback);
                this.setSlot(n < perColumn ? 9 * n + 7 : 9 * ((gui.getHeight() - 5) - n % perColumn) + 1,
                        tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(n < perColumn ? 9 * n + 6 - i : 9 * ((gui.getHeight() - 5) - n % perColumn) + 2 + i,
                            reminderItems.get(i));
                }
            }
            if (layout == GrimoireGUI.LayoutStyle.TWO_ROWS) {
                int perRow = gui.seatManager.getSeatCount() / 2 + gui.seatManager.getSeatCount() % 2;
                this.setSlot(n < perRow ? n     : 6 * 9 - (n % perRow) - 1, headItem, headCallback);
                this.setSlot(n < perRow ? n + 9 : 5 * 9 - (n % perRow) - 1, tokenItem, tokenCallback);
                if (!reminderItems.isEmpty()) {
                    this.setSlot(n < perRow ? n + 18 : 4 * 9 - (n % perRow) - 1, reminderItems.getFirst());
                }
            }
        }
    }
}
