package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu layer for actions on player and storyteller seats.
 */
public abstract class AbstractSeatMenuLayer<T extends Seat> extends Layer {

    GrimoireGUI gui;
    T seat;

    /**
     * Constructor for a PlayerSeatMenuLayer for a Seat.
     * @param gui  The GrimoireGUI instance.
     * @param seat The Seat to manage.
     */
    public AbstractSeatMenuLayer(GrimoireGUI gui, T seat) {
        super(1, 9);

        this.gui = gui;
        this.seat = seat;

        List<GuiElement> elements = getItems();

        for (GuiElement element : elements) {
            this.addSlot(element);
        }
    }

    /**
     * Get the items/buttons to be located on the hotbar menu.
     * @return The items.
     */
    protected List<GuiElement> getItems() {
        List<GuiElement> elements = new ArrayList<>(9);
        elements.add(ButtonBuilder.buildButton(
                Text.translatable("gui.cancel"), ButtonIcon.CLOSE, (i, c, a, g) -> gui.reopen()));
        if (seat.isAlive()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.kill"), ButtonIcon.KILL, (i, c, a, g) -> {
                        seat.kill();
                        this.reopen();
                    }));
        } else {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.revive"), ButtonIcon.REVIVE, (i, c, a, g) -> {
                        seat.revive();
                        this.reopen();
                    }));
        }

        if (seat.hasPlayerEntity()) {
            elements.add(ButtonBuilder.buildButton(
                    Text.translatable("spectatorMenu.teleport"), ButtonIcon.TELEPORT, (i, c, a, g) -> {
                        ServerPlayerEntity player = seat.getPlayerEntity();
                        gui.getPlayer().teleport(player.getX(), player.getY(), player.getZ(), true);
                        gui.close();
                    }));
        }

        return elements;
    }

    /**
     * Reopen the gui.
     */
    protected abstract void reopen();
}