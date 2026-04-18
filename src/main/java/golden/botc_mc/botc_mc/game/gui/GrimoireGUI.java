package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.LayerView;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.gui.selection.BagSelectionGUI;
import golden.botc_mc.botc_mc.game.gui.selection.NPCCharacterSelectGUI;
import golden.botc_mc.botc_mc.game.gui.selection.PlayerCharacterSelectGUI;
import golden.botc_mc.botc_mc.game.gui.selection.ReminderSelectGUI;
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

/**
 * The main GUI for Storytellers. All storyteller interactions are done through this GUI.
 */
public class GrimoireGUI extends LayeredGui {
    protected final botcSeatManager seatManager;
    protected final Script script;
    private LayerView playerPopoutView;
    private LayerView playerMenuView;
    private LayerView storytellerView;

    /**
     * Constructor for GrimoireGUI.
     * @param player      The player for whom the GUI is being created.
     * @param seatManager The seat manager containing player and storyteller seats.
     * @param script      The game script containing characters and other game data.
     */
    public GrimoireGUI(ServerPlayerEntity player, botcSeatManager seatManager, Script script) {
        super(getScreenSize(seatManager), player, true);
        this.setTitle(Text.translatable("gui.botc-mc.grimoire.title"));

        this.seatManager = seatManager;
        this.script = script;

        LayoutStyle layout = LayoutStyle.getLayoutType(seatManager.getSeatCount());

        this.addLayer(new TownCircleLayer(this, layout), 0, 0);
        this.storytellerView = this.addLayer(new StorytellerLayer(this), 0, this.getHeight() - 4);
    }

    /**
     * Determines the appropriate screen size based on the number of rows.
     * @param rows The number of rows needed.
     * @return The corresponding ScreenHandlerType for the given number of rows.
     */
    public static ScreenHandlerType<GenericContainerScreenHandler> getScreenSizeOfRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    /**
     * Determines the appropriate screen size based on the seat manager's seat count and layout style.
     * @param seatManager The seat manager containing player and storyteller seats.
     * @return The corresponding ScreenHandlerType for the given seat configuration.
     */
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
            for (int n = 1; n <= seatManager.getSeatCount(); n++) {
                maxReminders = Math.max(maxReminders, seatManager.getSeatFromNumber(n).getReminders().size());
            }
            return getScreenSizeOfRows(2 + maxReminders);
        }
        return ScreenHandlerType.GENERIC_9X6;
    }

    /**
     * Reopens the GrimoireGUI for the same player, seat manager, and script.
     * @return The newly opened GrimoireGUI instance.
     */
    public GrimoireGUI reopen() {
        GrimoireGUI newGui = new GrimoireGUI(this.getPlayer(), this.seatManager, this.script);
        newGui.open();
        this.close();
        return newGui;
    }

    /**
     * Reopens the GrimoireGUI and shows the player popout for the specified player seat.
     * @param seat The player seat for which to show the popout.
     */
    public void reopen(PlayerSeat seat) {
        GrimoireGUI newGui = this.reopen();
        newGui.showSeatPopout(seat);
    }

    /**
     * Reopens the GrimoireGUI and shows the player popout for the specified storyteller seat.
     * @param seat The storyteller seat for which to show the popout.
     */
    public void reopen(StorytellerSeat seat) {
        GrimoireGUI newGui = this.reopen();
        newGui.showSeatPopout(seat);
    }

    /**
     * Shows the player popout for the specified player seat.
     * @param seat The player seat for which to show the popout.
     */
    public void showSeatPopout(PlayerSeat seat) {
        clearInventorySection();
        int offset = (7 - Math.min(seat.getReminders().size(), 7)) / 2;
        int seatNumber = seatManager.getSeatNumber(seat);
        botc.LOGGER.info("Showing popout for seat {} at offset {}.", seatNumber, offset);
        this.playerPopoutView = this.addLayer(new SeatPopoutLayer(this, seat, seatNumber), offset,
                this.getHeight() - 3);
        this.playerMenuView = this.addLayer(new PlayerSeatMenuLayer(this, seat), 0, this.getHeight() - 1);
        this.markDirty();
    }

    /**
     * Shows the player popout for the specified storyteller seat.
     * @param seat The storyteller seat for which to show the popout.
     */
    public void showSeatPopout(StorytellerSeat seat) {
        clearInventorySection();
        botc.LOGGER.info("Showing menu for storyteller seat.");
        this.playerPopoutView = this.addLayer(new SeatPopoutLayer(this, seat), 3, this.getHeight() - 3);
        this.playerMenuView = this.addLayer(new StorytellerSeatMenuLayer(this, seat), 0, this.getHeight() - 1);
        this.markDirty();
    }

    /**
     * Clears the inventory section layers (popout and menu) if they exist.
     */
    private void clearInventorySection() {
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

    /**
     * Opens the character selection GUI for the specified player seat.
     * @param seat The player seat for which to select a character.
     */
    public void selectCharacter(PlayerSeat seat) {
        PlayerCharacterSelectGUI gui = new PlayerCharacterSelectGUI(this.getPlayer(), script, (c) -> {
            seat.setCharacter(c);
            this.reopen(seat);
            return null;
        }, () -> this.reopen(seat), false, 0);
        gui.open();
    }

    /**
     * Opens the character selection GUI for the specified storyteller seat.
     * @param seat The storyteller seat for which to select a character.
     */
    public void selectCharacter(StorytellerSeat seat) {
        PlayerCharacterSelectGUI gui = new PlayerCharacterSelectGUI(this.getPlayer(), script, (c) -> {
            seat.setCharacter(c);
            this.reopen(seat);
            return null;
        }, () -> this.reopen(seat), false, 0);
        gui.open();
    }

    /**
     * Generates GUI elements for the reminder tokens of a player seat.
     * @param seat         The player seat whose reminders are being displayed.
     * @param reminders    The list of reminder tokens to display.
     * @param maxReminders The maximum number of reminders to display before adding a "See All" item.
     * @return A list of GUI elements representing the reminder tokens.
     */
    public List<GuiElement> getReminderItems(PlayerSeat seat, List<botcCharacter.ReminderToken> reminders,
                                             int maxReminders) {
        List<GuiElement> elements = new ArrayList<>();
        if (reminders.size() > maxReminders) {
            maxReminders -= 1; // Reserve one slot for "See All"
        }
        // Add reminder items
        for (int n = 0; n < Math.min(reminders.size(), maxReminders); n++) {
            ItemStack reminderItem = TokenItemStack.of(reminders.get(n));
            GuiElementInterface.ClickCallback reminderCallback = reminderClickCallback(seat, reminders, n);
            GuiElement reminderElement = new GuiElement(reminderItem, reminderCallback);
            elements.add(reminderElement);
        }
        // If there are more reminders, add a "See All" item containing the rest
        if (reminders.size() > maxReminders) {
            ItemStack moreItem = new ItemStack(Items.PAPER);
            MutableText moreText = Text.translatable("gui.botc-mc.reminder.see_all");
            moreItem.set(DataComponentTypes.CUSTOM_NAME, moreText);
            List<Text> allRemindersText = new ArrayList<>();
            for (int i = maxReminders; i < reminders.size(); i++) {
                MutableText reminderText = (MutableText) Text.of("- ");
                reminderText.append(Text.translatable(reminders.get(i).reminder()));
                reminderText.styled(style -> style.withColor(Formatting.WHITE).withItalic(false));
                allRemindersText.add(reminderText);
            }
            LoreComponent loreComponent = new LoreComponent(allRemindersText);
            moreItem.set(DataComponentTypes.LORE, loreComponent);
            GuiElementInterface.ClickCallback moreCallback = (i, c, a, g) -> showSeatPopout(seat);
            GuiElement moreElement = new GuiElement(moreItem, moreCallback);
            elements.add(moreElement);
        }
        return elements;
    }

    /**
     * Creates a click callback for a reminder token item.
     * If the item is shift-right-clicked, it removes the reminder.
     * If the item is right-clicked and is a custom reminder, it opens the editing GUI.
     * Otherwise, it shows the player seat popout.
     * @param seat      The player seat associated with the reminder.
     * @param reminders The list of reminder tokens.
     * @param n         The index of the reminder token.
     * @return A ClickCallback that handles interactions with the reminder token item.
     */
    private GuiElementInterface.ClickCallback reminderClickCallback(PlayerSeat seat,
                                                                    List<botcCharacter.ReminderToken> reminders,
                                                                    int n) {
        return (i, c, a, g) -> {
            // Remove reminder
            if (c == ClickType.MOUSE_RIGHT_SHIFT) {
                seat.removeReminder(n);
                this.reopen(seat);
                // Edit reminder
            } else if (c == ClickType.MOUSE_RIGHT && reminders.get(n).character() == botcCharacter.EMPTY) {
                ReminderSelectGUI.CustomTokenBox box = new ReminderSelectGUI.CustomTokenBox(this.getPlayer(),
                        (token) -> {
                    seat.removeReminder(n);
                    seat.addReminderToken(token);
                    this.reopen(seat);
                    return null;
                });
                String[] existingLines = reminders.get(n).reminder().split("\n", 4);
                for (int lineIndex = 0; lineIndex < existingLines.length; lineIndex++) {
                    box.setLine(lineIndex, Text.of(existingLines[lineIndex]));
                }
                box.open();
            } else {
                showSeatPopout(seat);
            }
        };
    }

    /**
     * Opens the reminder selection GUI to add a reminder token to the specified player seat.
     * @param seat The player seat to which the reminder token will be added.
     */
    public void addReminder(PlayerSeat seat) {
        ReminderSelectGUI gui = new ReminderSelectGUI(this.getPlayer(), script, seatManager, (token) -> {
            seat.addReminderToken(token);
            this.reopen(seat);
            return null;
        }, () -> this.reopen(seat), false, 0);
        gui.open();
    }

    /**
     * Opens the NPC character selection GUI to add an NPC to the seat manager.
     */
    public void addNPC() {
        NPCCharacterSelectGUI gui = new NPCCharacterSelectGUI(this.getPlayer(), script, seatManager,
                (c) -> {
                    seatManager.addNPC(c);
                    this.reopen();
                    return null;
                }, this::reopen, 0);
        gui.open();
    }

    /**
     * Opens the grimoire resizing GUI to edit the player count and seat order.
     */
    public void editGrimoire() {
        ResizeGrimGUI gui = new ResizeGrimGUI(this.getPlayer(), this.seatManager);
        gui.open();
    }

    /**
     * Opens the role selection gui to select and distribute characters.
     */
    public void buildBag() {
        BagSelectionGUI gui = new BagSelectionGUI(this.getPlayer(), this.script, this.seatManager, List.of(),
            selectedItems -> {
                botc.LOGGER.info("Selected {}", selectedItems);
                this.seatManager.assignCharacters(selectedItems);
                return null;
            }, this::reopen, 0);
        gui.open();
    }

    /**
     * Enum representing different layout styles for the grimoire GUI.
     * The layout style is determined based on the number of seats.
     * SINGLE_COLUMN: Up to 6 seats.
     * SINGLE_ROW: Up to 9 seats.
     * TWO_COLUMNS: Up to 12 seats.
     * TWO_ROWS: Up to 18 seats.
     */
    public enum LayoutStyle {
        UNKNOWN,
        SINGLE_COLUMN,
        SINGLE_ROW,
        TWO_COLUMNS,
        TWO_ROWS;

        /**
         * Determines the layout style based on the number of seats.
         * @param seatCount The number of seats in the game.
         * @return The corresponding LayoutStyle.
         */
        static LayoutStyle getLayoutType(int seatCount) {
            if (seatCount <= 6) return SINGLE_COLUMN;
            if (seatCount <= 9) return SINGLE_ROW;
            if (seatCount <= 12) return TWO_COLUMNS;
            if (seatCount <= 18) return TWO_ROWS;
            return UNKNOWN;
        }

        /**
         * Determines the maximum number of reminders to display based on the layout style.
         * This is limited by available space in the GUI.
         * @param layoutStyle The layout style of the grimoire GUI.
         * @return The maximum number of reminders that can be displayed.
         */
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
