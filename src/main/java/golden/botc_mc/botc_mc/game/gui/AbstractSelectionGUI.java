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

public abstract class AbstractSelectionGUI<T> extends SimpleGui {
    protected static final int ITEMS_PER_PAGE = 5 * 9;
    protected final List<T> items;
    protected final Function<T, ?> onSelectItem;

    public AbstractSelectionGUI(ServerPlayerEntity player, List<T> items,
                                Function<T, ?> onSelectItem, int page) {
        super(getScreenSize(items), player, false);
        this.setTitle(Text.of("Select an Item"));

        this.items = items;
        this.onSelectItem = onSelectItem;

        int pages = getPageCount();

        // Add all items for this page
        for (T item : getPage(page)) {
            GuiElementInterface.ClickCallback itemCallback =
                    (index, clickType, slotActionType, gui) -> itemSelectCallback(item);
            this.addSlot(getItemStack(item), itemCallback);
        }

        // Pagination buttons
        if (page > 0) {
            GuiElementInterface.ClickCallback prevPageCallback =
                    (index, clickType, slotActionType, gui) -> newInstance(player, page - 1).open();
            this.setSlot(9 * this.getHeight() - 9, SeatMenuLayer.buildButton(Text.of("Previous Page"),
                    prevPageCallback));
        }
        if (page < pages - 1) {
            GuiElementInterface.ClickCallback nextPageCallback =
                    (index, clickType, slotActionType, gui) -> newInstance(player, page + 1).open();
            this.setSlot(9 * this.getHeight() - 1, SeatMenuLayer.buildButton(Text.of("Next Page"), nextPageCallback));
        }

        // Cancel button
        this.setSlot(9 * this.getHeight() - 2, SeatMenuLayer.buildButton(Text.of("Cancel"), (index, clickType,
                                                                                             slotActionType, gui) ->
                this.close()));
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

    protected void itemSelectCallback(T item) {
        this.onSelectItem.apply(item);
        this.close();
    }

    protected abstract AbstractSelectionGUI<T> newInstance(ServerPlayerEntity player, int page);

    protected abstract ItemStack getItemStack(T item);
}
