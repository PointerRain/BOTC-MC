package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import net.minecraft.item.ItemStack;

public class StorytellerLayer extends Layer {
    public StorytellerLayer(GrimoireGUI gui) {
        super(3, 9);

        GrimoireGUI.LayoutStyle layout = gui.seatManager.getStorytellers().size() <= 3 ? GrimoireGUI.LayoutStyle.SINGLE_COLUMN : GrimoireGUI.LayoutStyle.SINGLE_ROW;

        for (int n = 0; n < gui.seatManager.getStorytellers().size(); n++) {
            StorytellerSeat seat = gui.seatManager.getStorytellers().get(n);
            ItemStack headItem = PlayerHeadItemStack.of(seat);
            ItemStack tokenItem = TokenItemStack.of(seat);

            GuiElementInterface.ClickCallback headCallback = (i, c, a, g) ->
                gui.showPlayerPopout(seat);
            GuiElementInterface.ClickCallback tokenCallback = (i, c, a, g) ->
                gui.selectCharacter(seat);

            if (layout == GrimoireGUI.LayoutStyle.SINGLE_COLUMN) {
                this.setSlot(9 * n, headItem, headCallback);
                this.setSlot(9 * n + 1, tokenItem, tokenCallback);
            }
            if (layout == GrimoireGUI.LayoutStyle.SINGLE_ROW) {
                this.setSlot(n, headItem, headCallback);
                this.setSlot(n + 9, tokenItem, tokenCallback);
            }
        }

        int perRow = layout == GrimoireGUI.LayoutStyle.SINGLE_COLUMN ? 7 : 9 - gui.seatManager.getStorytellers().size();
        if (gui.seatManager.getNPCs().size() > 5) {
            perRow = Math.min(perRow, (gui.seatManager.getNPCs().size() / 3));
        }
        for (int n = 0; n < gui.seatManager.getNPCs().size(); n++) {
            botcCharacter npc = gui.seatManager.getNPCs().get(n);
            ItemStack tokenItem = TokenItemStack.of(npc);
            GuiElementInterface.ClickCallback tokenCallback = (i, c, a, g) -> {
                if (c == ClickType.MOUSE_LEFT_SHIFT) {
                    gui.seatManager.removeNPC(npc);
                }
            };
            this.setSlot(9 * (n / perRow) + 8 - (n % perRow), tokenItem, tokenCallback);
        }
    }
}
