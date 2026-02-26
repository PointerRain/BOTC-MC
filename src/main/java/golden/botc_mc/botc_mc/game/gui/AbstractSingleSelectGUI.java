package golden.botc_mc.botc_mc.game.gui;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.function.Function;

/**
 * Abstract GUI for selecting a SINGLE item from a list with pagination support.
 * Handles displaying items, pagination buttons, and cancel button.
 * @param <T> The type of items to select from.
 */
public abstract class AbstractSingleSelectGUI<T> extends AbstractSelectionGUI<T> {
    protected final Function<T, ?> onSelectItem;

    /**
     * Constructor for AbstractSelectionGUI.
     * @param player       The player for whom the GUI is being created.
     * @param items        The list of items to display for selection.
     * @param onSelectItem A function to call when an item is selected.
     * @param onCancel     A runnable to call when the selection is cancelled.
     * @param page         The current page number (0-indexed).
     */
    public AbstractSingleSelectGUI(ServerPlayerEntity player, List<T> items,
                                   Function<T, ?> onSelectItem, Runnable onCancel,
                                   int page) {
        super(player, items, onCancel, page, false);

        this.onSelectItem = onSelectItem;
    }

    /**
     * Callback function when an item is selected.
     * @param item The selected item.
     */
    protected void itemSelectCallback(T item) {
        this.onSelectItem.apply(item);
        this.close();
    }
}
