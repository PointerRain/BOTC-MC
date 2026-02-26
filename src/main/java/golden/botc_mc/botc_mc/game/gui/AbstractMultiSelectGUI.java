package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.network.ServerPlayerEntity;
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

        this.setSlot(7 + 9 * this.getHeight() + 9 * 3, ButtonBuilder.buildButton(
                Text.translatable("gui.ok"),
                ButtonIcon.CONFIRM,
                (i,c,a,g) -> {}));
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

    /**
     * Callback function when an item is selected.
     * @param item The selected item.
     */
    @Override
    protected void itemSelectCallback(T item) {

        List<T> newSelectedItems = new ArrayList<>(selectedItems);
        newSelectedItems.add(item);
        this.selectedItems = newSelectedItems;

        AbstractSelectionGUI<T> newGui = newInstance(this.player, this.page);
        newGui.open();
    }

    protected void itemDeselectCallback(T item) {
        List<T> newSelectedItems = new ArrayList<>(selectedItems);
        newSelectedItems.remove(item);
        this.selectedItems = newSelectedItems;

        AbstractSelectionGUI<T> newGui = newInstance(this.player, this.page);
        newGui.open();
    }
}
