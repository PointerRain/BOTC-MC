package golden.botc_mc.botc_mc.game.gui.selection;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import golden.botc_mc.botc_mc.game.gui.ButtonBuilder;
import golden.botc_mc.botc_mc.game.gui.ButtonIcon;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract GUI for selecting MULTIPLE items from a list with pagination support.
 * Handles displaying items, pagination buttons, and cancel button.
 * @param <T> The type of items to select from.
 */
public abstract class AbstractMultiSelectGUI<T> extends AbstractSelectionGUI<T> {
    protected static final int SELECTED_ITEMS_PER_PAGE = 3 * 9;

    List<T> selectedItems;
    protected Function<List<T>, ?> onFinaliseSelection;

    /**
     * Constructor for AbstractMultiSelectGUI.
     * @param player              The player for whom the GUI is being created.
     * @param items               The list of items to display for selection.
     * @param onFinaliseSelection The function to call when the selection is finalised.
     * @param onCancel            A runnable to call when the selection is cancelled.
     * @param page                The current page number (0-indexed).
     */
    public AbstractMultiSelectGUI(ServerPlayerEntity player,
                                  List<T> items, List<T> selectedItems,
                                  Function<List<T>, ?> onFinaliseSelection, Runnable onCancel,
                                  int page) {
        super(player, items, onCancel, page, true);

        this.selectedItems = selectedItems;
        this.onFinaliseSelection = onFinaliseSelection;
    }

    @Override
    public void beforeOpen() {
        super.beforeOpen();

        for (int n = 0; n < getSelectedPage(page).size(); n++) {
            T item = getSelectedPage(page).get(n);
            GuiElementInterface.ClickCallback itemCallback = (i, c, a, g) -> itemDeselectCallback(item);
            setSlot(n + 9 * this.getHeight(), getItemStack(item), itemCallback);
        }

        if (this.canFinalise()) {
            GuiElementInterface.ClickCallback finaliseCallback = (i, c, a, g) -> finaliseSelection();
            this.setSlot(7 + 9 * this.getHeight() + 9 * 3, ButtonBuilder.buildButton(
                    Text.translatable("gui.ok"),
                    ButtonIcon.CONFIRM,
                    finaliseCallback));
        } else {
            ItemStack emptyStack = new ItemStack(Items.GRAY_DYE);
            emptyStack.set(DataComponentTypes.CUSTOM_NAME,
                    ((MutableText) this.getFinaliseReason())
                            .styled(style -> style.withItalic(false)));
            this.setSlot(7 + 9 * this.getHeight() + 9 * 3, emptyStack);
        }
    }

    @Override
    protected int getPageCount() {
        int selectedPageCount = (int) Math.ceil((double) this.selectedItems.size() / SELECTED_ITEMS_PER_PAGE);
        return Math.max(super.getPageCount(), selectedPageCount);
    }

    protected List<T> getSelectedPage(int page) {
        if (this.selectedItems.size() <= SELECTED_ITEMS_PER_PAGE) {
            return selectedItems;
        }
        int start = page * SELECTED_ITEMS_PER_PAGE;
        int end = Math.min(start + SELECTED_ITEMS_PER_PAGE, this.selectedItems.size());
        return this.selectedItems.subList(start, end);
    }

    @Override
    protected void itemSelectCallback(T item) {

        List<T> newSelectedItems = new ArrayList<>(selectedItems);
        newSelectedItems.add(item);
        this.selectedItems = newSelectedItems;

        AbstractSelectionGUI<T> newGui = newInstance(this.player, this.page);
        newGui.open();
    }

    /**
     * Callback function when an item is deselected.
     * @param item The selected item.
     */
    protected void itemDeselectCallback(T item) {
        List<T> newSelectedItems = new ArrayList<>(selectedItems);
        newSelectedItems.remove(item);
        this.selectedItems = newSelectedItems;

        AbstractSelectionGUI<T> newGui = newInstance(this.player, this.page);
        newGui.open();
    }

    protected boolean canFinalise() {
        return !selectedItems.isEmpty();
    }

    protected Text getFinaliseReason() {
        return Text.translatable("gui.botc-mc.selection.finalise_disabled");
    }

    protected void finaliseSelection() {
        this.onFinaliseSelection.apply(this.selectedItems);
        this.close();
    }
}
