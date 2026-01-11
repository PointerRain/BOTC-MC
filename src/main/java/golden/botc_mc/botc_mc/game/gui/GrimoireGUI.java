package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.gui.layered.LayerView;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class GrimoireGUI extends LayeredGui {
    protected final botcSeatManager seatManager;
    protected final Script script;
    protected final ServerPlayerEntity player;
    private LayerView playerPopoutView = null;
    private LayerView playerMenuView = null;
    private LayerView storytellerView;

    public GrimoireGUI(ServerPlayerEntity player, botcSeatManager seatManager, Script script) {
        super(getScreenSize(seatManager), player, true);
        this.setTitle(Text.of("Grimoire"));

        this.seatManager = seatManager;
        this.script = script;
        this.player = player;

        LayoutStyle layout = LayoutStyle.getLayoutType(seatManager.getSeatCount());

        this.addLayer(new TownCircleLayer(this, layout), 0, 0);
        this.storytellerView = this.addLayer(new StorytellerLayer(this), 0, this.getHeight() - 4);
    }

    static ScreenHandlerType<GenericContainerScreenHandler> getScreenSizeOfRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    static ScreenHandlerType<?> getScreenSize(botcSeatManager seatManager) {
        LayoutStyle layout = LayoutStyle.getLayoutType(seatManager.getSeatCount());
        if (layout == LayoutStyle.SINGLE_COLUMN) {
            return getScreenSizeOfRows(seatManager.getSeatCount());
        }
        if (layout == LayoutStyle.TWO_COLUMNS) {
            int perColumn = seatManager.getSeatCount() / 2 + seatManager.getSeatCount() % 2;
            return getScreenSizeOfRows(perColumn);
        }
        if (layout == LayoutStyle.SINGLE_ROW) {
            int maxReminders = 0;
            for (int n = 1; n < seatManager.getSeatCount(); n++) {
                maxReminders = Math.max(maxReminders, seatManager.getSeatFromNumber(n).getReminders().size());
            }
            return getScreenSizeOfRows(2 + maxReminders);
        }
        return ScreenHandlerType.GENERIC_9X6;
    }

    public GrimoireGUI reopen() {
        GrimoireGUI newGui = new GrimoireGUI(this.player, this.seatManager, this.script);
        newGui.open();
        this.close();
        return newGui;
    }

    public void reopen(PlayerSeat seat) {
        GrimoireGUI newGui = this.reopen();
        newGui.showPlayerPopout(seat);
    }

    public void reopen(StorytellerSeat seat) {
        GrimoireGUI newGui = this.reopen();
        newGui.showPlayerPopout(seat);
    }

    public void showPlayerPopout(PlayerSeat seat, int seatNumber) {
        clearInventorySection();
        int offset = (7 - Math.min(seat.getReminders().size(), 7)) / 2;
        botc.LOGGER.info("Showing popout for seat {} at offset {}.", seatNumber, offset);
        this.playerPopoutView = this.addLayer(new SeatPopoutLayer(this, seat, seatNumber), offset, this.getHeight() - 3);
        this.playerMenuView = this.addLayer(new SeatMenuLayer(this, seat), 0, this.getHeight() - 1);
        this.markDirty();
    }

    public void showPlayerPopout(PlayerSeat seat) {
        int seatNumber = seatManager.getSeatNumber(seat);
        showPlayerPopout(seat, seatNumber);
    }

    public void showPlayerPopout(StorytellerSeat seat) {
        clearInventorySection();
        botc.LOGGER.info("Showing menu for storyteller seat.");
        this.playerPopoutView = this.addLayer(new SeatPopoutLayer(this, seat), 3, this.getHeight() - 3);
        this.playerMenuView = this.addLayer(new SeatMenuLayer(this, seat), 0, this.getHeight() - 1);
        this.markDirty();
    }

    /**
     * Clears the inventory section layers (popout and menu) if they exist.
     */
    void clearInventorySection() {
        if (this.playerPopoutView != null) {
            this.removeLayer(this.playerPopoutView);
            this.playerPopoutView = null;
        }
        if (this.playerMenuView != null) {
            this.removeLayer(this.playerMenuView);
            this.playerMenuView = null;
        }
        if (this.storytellerView != null) {
            this.removeLayer(this.storytellerView);
            this.storytellerView = null;
        }
    }

    public void selectCharacter(PlayerSeat seat) {
        CharacterSelectGUI gui = new PlayerCharacterSelectGUI(this.player, script, false, (c) -> {
            seat.setCharacter(c);
            this.reopen(seat);
            return null;
        }, 0);
        gui.open();
    }

    public void selectCharacter(StorytellerSeat seat) {
        CharacterSelectGUI gui = new PlayerCharacterSelectGUI(this.player, script, false, (c) -> {
            seat.setCharacter(c);
            this.reopen(seat);
            return null;
        }, 0);
        gui.open();
    }

    static List<ItemStack> getReminderItems(List<botcCharacter.ReminderToken> reminders, int maxReminders) {
        List<ItemStack> reminderItems = new ArrayList<>();
        if (reminders.size() > maxReminders) {
            maxReminders -= 1; // Reserve one slot for "See All"
        }
        // Add reminder items
        for (int i = 0; i < Math.min(reminders.size(), maxReminders); i++) {
            ItemStack reminderItem = TokenItemStack.of(reminders.get(i));
            reminderItems.add(reminderItem);
        }
        // If there are more reminders, add a "See All" item containing the rest
        if (reminders.size() > maxReminders) {
            ItemStack moreItem = new ItemStack(Items.PAPER);
            MutableText moreText = (MutableText) Text.of("See All");
            moreItem.set(DataComponentTypes.CUSTOM_NAME, moreText);
            List<Text> allRemindersText = new ArrayList<>();
            for (int i = maxReminders; i < reminders.size(); i++) {
                MutableText reminderText = (MutableText) Text.of("- " + reminders.get(i).reminder());
                reminderText.styled(style -> style.withColor(Formatting.WHITE).withItalic(false));
                allRemindersText.add(reminderText);
            }
            LoreComponent loreComponent = new LoreComponent(allRemindersText);
            moreItem.set(DataComponentTypes.LORE, loreComponent);
            reminderItems.add(moreItem);
        }
        return reminderItems;
    }

    public void addReminder(PlayerSeat seat) {
        ReminderSelectGUI gui = new ReminderSelectGUI(this.player, script, seatManager, (token) -> {
            seat.addReminderToken(token);
            this.reopen(seat);
            return null;
        }, false, 0);
        gui.open();
    }

    public enum LayoutStyle {
        UNKNOWN,
        SINGLE_COLUMN,
        SINGLE_ROW,
        TWO_COLUMNS,
        TWO_ROWS;

        static LayoutStyle getLayoutType(int seatCount) {
            if (seatCount <= 6) return SINGLE_COLUMN;
            if (seatCount <= 9) return SINGLE_ROW;
            if (seatCount <= 12) return TWO_COLUMNS;
            if (seatCount <= 18) return TWO_ROWS;
            return UNKNOWN;
        }

        static int getMaxReminders(LayoutStyle layoutStyle) {
            return switch (layoutStyle) {
                case SINGLE_COLUMN -> 7;
                case SINGLE_ROW -> 4;
                case TWO_COLUMNS -> 2;
                default -> 1;
            };
        }
    }
}
