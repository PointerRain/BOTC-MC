package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menu layer for actions on player and storyteller seats.
 */
public class StorytellerSeatMenuLayer extends AbstractSeatMenuLayer<StorytellerSeat> {

    /**
     * Constructor for a StorytellerSeatMenuLayer for a StorytellerSeat.
     * @param gui  The GrimoireGUI instance.
     * @param seat The StorytellerSeat to manage.
     */
    public StorytellerSeatMenuLayer(GrimoireGUI gui, StorytellerSeat seat) {
        super(gui, seat);
    }

    @Override
    protected List<GuiElement> getItems() {
        List<GuiElement> elements = super.getItems();

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

        ItemStack bagIcon = ButtonIcon.bagIcon(gui.script);

        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.botc-mc.build_bag"), bagIcon, (i, c, a, g) -> gui.buildBag()));

        return elements;
    }

    @Override
    protected void reopen() {
        this.gui.reopen(this.seat);
    }
}