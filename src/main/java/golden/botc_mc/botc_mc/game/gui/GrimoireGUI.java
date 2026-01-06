package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SimpleGui;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GrimoireGUI extends SimpleGui {
    botcSeatManager seatManager;
    Script script;

    public GrimoireGUI(ServerPlayerEntity player, botcSeatManager seatManager, Script script) {
        super(ScreenHandlerType.GENERIC_9X6, player, true);
        this.setTitle(Text.of("Grimoire"));

        this.seatManager = seatManager;
        this.script = script;

        LayoutStyle layout = LayoutStyle.getLayoutType(seatManager.getSeatCount());
        int maxReminders = LayoutStyle.getMaxReminders(layout);

        // Add items to the GUI based on seat and layout
        for (int n = 0; n < seatManager.getSeatCount(); n++) {
            PlayerSeat seat = seatManager.getSeatFromNumber(n+1);

            ItemStack headItem = getHeadItem(seat, n + 1);
            ItemStack tokenItem = TokenItemStack.of(seat);
            List<ItemStack> reminderItems = getReminderItems(seat.getReminders(), maxReminders);

            int finalN = n;
            GuiElementInterface.ClickCallback headCallback = (index, clickType, slotActionType, gui) ->
                showPlayerPopout(seat, finalN + 1);
            GuiElementInterface.ClickCallback tokenCallback = (index, clickType, slotActionType, gui) ->
                selectCharacter(seat);

            if (layout == LayoutStyle.SINGLE_COLUMN) {
                this.setSlot(9 * n, headItem, headCallback);
                this.setSlot(9 * n + 1, tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(9 * n + 2 + i, reminderItems.get(i));
                }
            }
            if (layout == LayoutStyle.SINGLE_ROW) {
                this.setSlot(n, headItem, headCallback);
                this.setSlot(n + 9, tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(n + 18 + 9 * i, reminderItems.get(i));
                }
            }
            if (layout == LayoutStyle.TWO_COLUMNS) {
                int perColumn = seatManager.getSeatCount() / 2 + seatManager.getSeatCount() % 2;
                this.setSlot(n < perColumn ? 9 * n + 8 : 9 * (n % perColumn), headItem, headCallback);
                this.setSlot(n < perColumn ? 9 * n + 7 : 9 * (n % perColumn) + 1, tokenItem, tokenCallback);
                for (int i = 0; i < reminderItems.size(); i++) {
                    this.setSlot(n < perColumn ? 9 * n + 6 - i : 9 * (n % perColumn) + 2 + i, reminderItems.get(i));
                }
            }
            if (layout == LayoutStyle.TWO_ROWS) {
                int perRow = seatManager.getSeatCount() / 2 + seatManager.getSeatCount() % 2;
                this.setSlot(n < perRow ? n     : 6 * 9 - (n % perRow) - 1, headItem, headCallback);
                this.setSlot(n < perRow ? n + 9 : 5 * 9 - (n % perRow) - 1, tokenItem, tokenCallback);
                if (!reminderItems.isEmpty()) {
                    this.setSlot(n < perRow ? n + 18 : 4 * 9 - (n % perRow) - 1, reminderItems.getFirst());
                }
            }
        }
    }

    private void clearPlayerPopout() {
        for (int i = 53; i < 81; i++) {
            this.clearSlot(i);
        }
    }

    public void showPlayerPopout(PlayerSeat seat, int seatNumber) {
        clearPlayerPopout();
        ItemStack headItem = getHeadItem(seat, seatNumber);
        ItemStack tokenItem = TokenItemStack.of(seat);

        GuiElementInterface.ClickCallback tokenCallback = (index, clickType, slotActionType, gui) ->
                selectCharacter(seat);

        List<ItemStack> reminderItems = getReminderItems(seat.getReminders(), 16);
        botc.LOGGER.info("Showing popout for seat {} with {} reminders.", seatNumber, reminderItems.size());
        int offset = 62 + (9 - Math.min(reminderItems.size(), 7)) / 2;
        this.setSlot(offset, headItem);
        this.setSlot(offset + 1, tokenItem, tokenCallback);
        for (int i = 0; i < reminderItems.size(); i++) {
            this.setSlot(offset + 2 + i, reminderItems.get(i));
        }
    }

    public void selectCharacter(PlayerSeat seat) {
        CharacterSelectGUI gui = new CharacterSelectGUI(player, script, (c) -> {
            seat.setCharacter(c);
            this.close();
            // Easier to reopen a new GUI than refresh the existing one
            GrimoireGUI newGui = new GrimoireGUI(player, seatManager, script);
            int seatNumber = seatManager.getSeatNumber(seat);
            newGui.showPlayerPopout(seat, seatNumber);
            newGui.open();
            return null;
        });
        gui.open();
    }

    private static @NotNull ItemStack getHeadItem(PlayerSeat seat, int seatNumber) {
        ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
        if (seat.hasPlayerEntity()) {
            ProfileComponent profile = new ProfileComponent(seat.getPlayerEntity().getGameProfile());
            headItem.set(DataComponentTypes.PROFILE, profile);
        }
        headItem.set(DataComponentTypes.CUSTOM_NAME, seat.getOccupantText());
        headItem.setCount(seatNumber);
        return headItem;
    }

    List<ItemStack> getReminderItems(List<String> reminders, int maxReminders) {
        List<ItemStack> reminderItems = new ArrayList<>();
        if (reminders.size() > maxReminders) {
            maxReminders -= 1; // Reserve one slot for "See All"
        }
        // Add reminder items
        for (int i = 0; i < Math.min(reminders.size(), maxReminders); i++) {
            ItemStack reminderItem = new ItemStack(Items.PAPER);
            MutableText reminderText = (MutableText) Text.of(reminders.get(i));
            reminderText.styled(style -> style.withItalic(false));
            reminderItem.set(DataComponentTypes.CUSTOM_NAME, reminderText);
            reminderItems.add(reminderItem);
        }
        // If there are more reminders, add a "See All" item containing the rest
        if (reminders.size() > maxReminders) {
            ItemStack moreItem = new ItemStack(Items.PAPER);
            MutableText moreText = (MutableText) Text.of("See All");
            moreItem.set(DataComponentTypes.CUSTOM_NAME, moreText);
            List<Text> allRemindersText = new ArrayList<>();
            for (int i = maxReminders; i < reminders.size(); i++) {
                MutableText reminderText = (MutableText) Text.of("- " + reminders.get(i));
                reminderText.styled(style -> style.withColor(Formatting.WHITE).withItalic(false));
                allRemindersText.add(reminderText);
            }
            LoreComponent loreComponent = new LoreComponent(allRemindersText);
            moreItem.set(DataComponentTypes.LORE, loreComponent);
            reminderItems.add(moreItem);
        }
        return reminderItems;
    }

    private enum LayoutStyle {
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
                case TWO_ROWS -> 1;
                default -> 1;
            };
        }
    }
}
