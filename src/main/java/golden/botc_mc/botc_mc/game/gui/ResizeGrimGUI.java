package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayerView;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static golden.botc_mc.botc_mc.game.gui.SeatMenuLayer.buildButton;

/**
 * GUI for resizing and managing the player seats in the Grimoire.
 */
public class ResizeGrimGUI extends LayeredGui {
    botcSeatManager seatManager;
    LayerView resizePopoutLayer;

    public ResizeGrimGUI(ServerPlayerEntity player, botcSeatManager seatManager) {
        super(seatManager.getSeatCount() <= 9 ? ScreenHandlerType.GENERIC_9X1 : ScreenHandlerType.GENERIC_9X3,
                player, true);
        this.setTitle(Text.translatable("gui.botc-mc.edit_grim"));

        this.seatManager = seatManager;

        this.addLayer(new ResizeTownLayer(this), 0, 0);
        this.addLayer(new ResizeMenuLayer(this), 0, this.getHeight() - 1);
    }

    /**
     * Show the resize popout layer for a specific seat.
     * Includes options to add, remove, and move seats.
     * @param seat The player seat to manage.
     * @param seatNumber The number of the seat.
     */
    private void showResizePopout(PlayerSeat seat, int seatNumber) {
        if (this.resizePopoutLayer != null) {
            this.removeLayer(this.resizePopoutLayer);
        }
        this.resizePopoutLayer = this.addLayer(new ResizePopoutLayer(this, seat, seatNumber), 2, this.getHeight() - 3);
    }

    /**
     * Reopen the GUI to reflect any changes made.
     * @return The new instance of ResizeGrimGUI.
     */
    private ResizeGrimGUI reopen() {
        ResizeGrimGUI newGui = new ResizeGrimGUI(this.getPlayer(), this.seatManager);
        newGui.open();
        return newGui;
    }

    /**
     * Reopen the GUI and show the resize popout for a specific seat.
     * @param seat The player seat to manage.
     */
    private void reopen(PlayerSeat seat) {
        ResizeGrimGUI newGui = this.reopen();
        newGui.showResizePopout(seat, this.seatManager.getSeatNumber(seat));
    }

    /**
     * Layer displaying the current player seats for resizing.
     */
    private static class ResizeTownLayer extends Layer {
        public ResizeTownLayer(ResizeGrimGUI gui) {
            super(3, 9);

            for (int n = 0; n < gui.seatManager.getSeatCount(); n++) {
                PlayerSeat seat = gui.seatManager.getSeatFromNumber(n + 1);
                ItemStack headItem = PlayerHeadItemStack.of(seat, n + 1);
                int finalN = n;
                GuiElementInterface.ClickCallback headCallback = (i, c, a, g) -> gui.showResizePopout(seat, finalN + 1);
                if (gui.seatManager.getSeatCount() <= 9)
                    this.setSlot(n, headItem, headCallback);
                else {
                    int perRow = gui.seatManager.getSeatCount() / 2 + gui.seatManager.getSeatCount() % 2;
                    this.setSlot(n < perRow ? n : 3 * 9 - (n % perRow) - 1, headItem, headCallback);
                }
            }
        }
    }

    /**
     * Layer for managing a specific seat (add, remove, move).
     */
    private static class ResizePopoutLayer extends Layer {
        public ResizePopoutLayer(ResizeGrimGUI gui, PlayerSeat seat, int seatNumber) {
            super(2, 5);

            // Head in centre
            ItemStack headItem = PlayerHeadItemStack.of(seat, seatNumber);
            this.setSlot(2, headItem);
            // Add before and add after buttons
            if (gui.seatManager.getSeatCount() < botcSeatManager.MAX_PLAYERS) {
                this.setSlot(1, buildButton(Text.translatable("gui.botc-mc.seats.add_before"), (i, c, a, g) -> {
                    gui.seatManager.insert(seatNumber > 1 ? seatNumber : gui.seatManager.getSeatCount() + 1);
                    gui.reopen(seat);
                }));
                this.setSlot(3, buildButton(Text.translatable("gui.botc-mc.seats.add_after"), (i, c, a, g) -> {
                    gui.seatManager.insert(seatNumber + 1);
                    gui.reopen(seat);
                }));
            }
            // Move left and move right buttons
            this.setSlot(0, buildButton(Text.translatable("gui.botc-mc.seats.move_left"), (i, c, a, g) -> {
                gui.seatManager.moveSeat(seatNumber, seatNumber - 1);
                gui.reopen(seat);
            }));
            this.setSlot(4, buildButton(Text.translatable("gui.botc-mc.seats.move_right"), (i, c, a, g) -> {
                gui.seatManager.moveSeat(seatNumber, seatNumber + 1);
                gui.reopen(seat);
            }));
            // Remove seat button
            if (gui.seatManager.getSeatCount() > botcSeatManager.MIN_PLAYERS) {
                this.setSlot(7, buildButton(Text.translatable("gui.botc-mc.seats.remove"), (i, c, a, g) -> {
                    gui.seatManager.remove(seatNumber);
                    gui.reopen();
                }));
            }
        }
    }

    /**
     * Layer containing general resize options (add/remove/shuffle seats).
     * Shown at the bottom of the GUI.
     * Seats are added to the end when added from here.
     * Seats are removed in a smart order to minimise disruption.
     */
    private static class ResizeMenuLayer extends Layer {
        public ResizeMenuLayer(ResizeGrimGUI gui) {
            super(1, 9);

            // Close button
            this.setSlot(0, buildButton(Text.translatable("gui.cancel"), (i, c, a, g) -> {
                if (gui.resizePopoutLayer != null) {
                    gui.removeLayer(gui.resizePopoutLayer);
                    gui.resizePopoutLayer = null;
                } else {
                    gui.close();
                }
            }));
            // Remove seat button
            if (gui.seatManager.getSeatCount() > botcSeatManager.MIN_PLAYERS) {
                this.setSlot(3, buildButton(Text.translatable("gui.botc-mc.seats.remove"), (i, c, a, g) -> {
                    gui.seatManager.setPlayerCount(gui.seatManager.getSeatCount() - 1);
                    gui.reopen();
                }));
            }
            // Add seat button
            if (gui.seatManager.getSeatCount() < botcSeatManager.MAX_PLAYERS) {
                this.setSlot(5, buildButton(Text.translatable("gui.botc-mc.seats.add"), (i, c, a, g) -> {
                    gui.seatManager.setPlayerCount(gui.seatManager.getSeatCount() + 1);
                    gui.reopen();
                }));
            }
            // Shuffle seats button
            this.setSlot(8, buildButton(Text.translatable("gui.botc-mc.seats.shuffle"), (i, c, a, g) -> {
                gui.seatManager.shuffle();
                gui.reopen();
            }));
        }
    }
}
