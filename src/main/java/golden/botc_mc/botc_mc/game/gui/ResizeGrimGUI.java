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

public class ResizeGrimGUI extends LayeredGui {
    botcSeatManager seatManager;
    LayerView resizePopoutLayer;

    public ResizeGrimGUI(ServerPlayerEntity player, botcSeatManager seatManager) {
        super(seatManager.getSeatCount() <= 9 ? ScreenHandlerType.GENERIC_9X1 : ScreenHandlerType.GENERIC_9X3,
                player, true);
        this.setTitle(Text.of("Edit Grimoire"));

        this.seatManager = seatManager;

        this.addLayer(new ResizeTownLayer(this), 0, 0);
        this.addLayer(new ResizeMenuLayer(this), 0, this.getHeight() - 1);
    }

    private void showResizePopout(PlayerSeat seat, int seatNumber) {
        if (this.resizePopoutLayer != null) {
            this.removeLayer(this.resizePopoutLayer);
        }
        this.resizePopoutLayer = this.addLayer(new ResizePopoutLayer(this, seat, seatNumber), 2, this.getHeight() - 3);
    }

    private ResizeGrimGUI reopen() {
        ResizeGrimGUI newGui = new ResizeGrimGUI(this.getPlayer(), this.seatManager);
        newGui.open();
        return newGui;
    }

    private void reopen(PlayerSeat seat) {
        ResizeGrimGUI newGui = this.reopen();
        newGui.showResizePopout(seat, this.seatManager.getSeatNumber(seat));
    }

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

    private static class ResizePopoutLayer extends Layer {
        public ResizePopoutLayer(ResizeGrimGUI gui, PlayerSeat seat, int seatNumber) {
            super(2, 5);

            // Head in centre
            ItemStack headItem = PlayerHeadItemStack.of(seat, seatNumber);
            this.setSlot(2, headItem);
            // Add before and add after buttons
            if (gui.seatManager.getSeatCount() < botcSeatManager.MAX_PLAYERS) {
                this.setSlot(1, buildButton(Text.of("Add Before"), (i, c, a, g) -> {
                    gui.seatManager.insert(seatNumber > 1 ? seatNumber : gui.seatManager.getSeatCount() + 1);
                    gui.reopen(seat);
                }));
                this.setSlot(3, buildButton(Text.of("Add After"), (i, c, a, g) -> {
                    gui.seatManager.insert(seatNumber + 1);
                    gui.reopen(seat);
                }));
            }
            // Move left and move right buttons
            this.setSlot(0, buildButton(Text.of("Move Left"), (i, c, a, g) -> {
                gui.seatManager.moveSeat(seatNumber, seatNumber - 1);
                gui.reopen(seat);
            }));
            this.setSlot(4, buildButton(Text.of("Move Right"), (i, c, a, g) -> {
                gui.seatManager.moveSeat(seatNumber, seatNumber + 1);
                gui.reopen(seat);
            }));
            // Remove seat button
            if (gui.seatManager.getSeatCount() > botcSeatManager.MIN_PLAYERS) {
                this.setSlot(7, buildButton(Text.of("Remove Seat"), (i, c, a, g) -> {
                    gui.seatManager.remove(seatNumber);
                    gui.reopen();
                }));
            }
        }
    }

    private static class ResizeMenuLayer extends Layer {
        public ResizeMenuLayer(ResizeGrimGUI gui) {
            super(1, 9);

            // Close button
            this.setSlot(0, buildButton(Text.of("Close Menu"), (i, c, a, g) -> {
                if (gui.resizePopoutLayer != null) {
                    gui.removeLayer(gui.resizePopoutLayer);
                    gui.resizePopoutLayer = null;
                } else {
                    gui.close();
                }
            }));
            // Remove seat button
            if (gui.seatManager.getSeatCount() > botcSeatManager.MIN_PLAYERS) {
                this.setSlot(3, buildButton(Text.of("Remove Seat"), (i, c, a, g) -> {
                    gui.seatManager.setPlayerCount(gui.seatManager.getSeatCount() - 1);
                    gui.reopen();
                }));
            }
            // Add seat button
            if (gui.seatManager.getSeatCount() < botcSeatManager.MAX_PLAYERS) {
                this.setSlot(5, buildButton(Text.of("Add Seat"), (i, c, a, g) -> {
                    gui.seatManager.setPlayerCount(gui.seatManager.getSeatCount() + 1);
                    gui.reopen();
                }));
            }
            // Shuffle seats button
            this.setSlot(8, buildButton(Text.of("Shuffle Seats"), (i, c, a, g) -> {
                gui.seatManager.shuffle();
                gui.reopen();
            }));
        }
    }
}
