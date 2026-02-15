package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Function;

/**
 * Abstract GUI for selecting an item from a list with pagination support.
 * Handles displaying items, pagination buttons, and cancel button.
 * @param <T> The type of items to select from.
 */
public abstract class AbstractSelectionGUI<T> extends SimpleGui {
    protected static final int ITEMS_PER_PAGE = 5 * 9;
    protected final List<T> items;
    protected final Function<T, ?> onSelectItem;
    protected final Runnable onCancel;
    protected final int page;

    /**
     * Constructor for AbstractSelectionGUI.
     * @param player The player for whom the GUI is being created.
     * @param items The list of items to display for selection.
     * @param onSelectItem A function to call when an item is selected.
     * @param onCancel A runnable to call when the selection is cancelled.
     * @param page The current page number (0-indexed).
     */
    public AbstractSelectionGUI(ServerPlayerEntity player, List<T> items,
                                Function<T, ?> onSelectItem,
                                Runnable onCancel,
                                int page) {
        super(getScreenSize(items), player, false);
        this.setTitle(Text.translatable("gui.botc-mc.selection"));

        this.items = items;
        this.onSelectItem = onSelectItem;
        this.onCancel = onCancel;
        this.page = page;

    }

    @Override
    public void beforeOpen() {
        int pages = getPageCount();

        // Add all items for this page
        for (T item : getPage(page)) {
            GuiElementInterface.ClickCallback itemCallback = (i, c, a, g) -> itemSelectCallback(item);
            this.addSlot(getItemStack(item), itemCallback);
        }

        // Pagination buttons
        if (page > 0) {
            GuiElementInterface.ClickCallback prevPageCallback = (i, c, a, g) -> newInstance(player, page - 1).open();
            this.setSlot(9 * this.getHeight() - 9, ButtonBuilder.buildButton(
                    Text.translatable("book.page_button.previous"), ButtonIcon.LEFT, prevPageCallback));
        }
        if (page < pages - 1) {
            GuiElementInterface.ClickCallback nextPageCallback =
                    (i, c, a, g) -> newInstance(player, page + 1).open();
            this.setSlot(9 * this.getHeight() - 1, ButtonBuilder.buildButton(
                    Text.translatable("book.page_button.next"), ButtonIcon.RIGHT, nextPageCallback));
        }

        // Cancel button
        GuiElementInterface.ClickCallback cancelCallback = (i, c, a, g) -> {
            if (this.onCancel != null) {
                this.onCancel.run();
            } else this.close();
        };
        this.setSlot(9 * this.getHeight() - 2, ButtonBuilder.buildButton(
                Text.translatable("gui.cancel"), ButtonIcon.CLOSE, cancelCallback));

        super.beforeOpen();
    }

    /**
     * Determine the appropriate screen size based on the number of items.
     * @return The ScreenHandlerType corresponding to the required number of rows.
     */
    protected static ScreenHandlerType<GenericContainerScreenHandler> getScreenSize(List<?> items) {
        int rows = (int) Math.ceil(items.size() / 9.0);
        return GrimoireGUI.getScreenSizeOfRows(rows + 1);
    }

    /**
     * Calculate the total number of pages needed to display all items.
     * @return The total number of pages.
     */
    protected int getPageCount() {
        return (int) Math.ceil((double) this.items.size() / ITEMS_PER_PAGE);
    }

    /**
     * Get the items for a specific page.
     * @param page The page number (0-indexed).
     * @return A sublist of items for the specified page.
     */
    protected List<T> getPage(int page) {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, this.items.size());
        return this.items.subList(start, end);
    }

    /**
     * Callback function when an item is selected.
     * @param item The selected item.
     */
    protected void itemSelectCallback(T item) {
        this.onSelectItem.apply(item);
        this.close();
    }

    /**
     * Create a new instance of the selection GUI for pagination.
     * @param player The player for whom the GUI is being created.
     * @param page The page number (0-indexed).
     * @return A new instance of AbstractSelectionGUI for the specified page.
     */
    protected abstract AbstractSelectionGUI<T> newInstance(ServerPlayerEntity player, int page);

    /**
     * Get the ItemStack representation of the item.
     * @param item The item to convert.
     * @return The ItemStack representing the item.
     */
    protected abstract ItemStack getItemStack(T item);
}
