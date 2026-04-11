package golden.botc_mc.botc_mc.game.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Enum representing different button icons for GUI elements.
 * Each enum constant corresponds to a specific action or purpose in the GUI.
 * The toItemStack method provides a way to convert the enum constant to a corresponding ItemStack for display in the GUI.
 */
public enum ButtonIcon {
    ADD,
    DELETE,
    LEFT,
    RIGHT,
    CLOSE,
    CONFIRM,
    SHUFFLE,
    UP,
    DOWN,
    EDIT,
    KILL,
    REVIVE,
    REMOVE_VOTE,
    RETURN_VOTE,
    NOMINATE,
    PROMOTE,
    DEMOTE,
    TELEPORT,
    MORE,
    LESS,
    BAG;

    ItemStack toItemStack() {
        return switch (this) {
            case ADD -> new ItemStack(Items.GREEN_DYE); // Placeholder
            case DELETE -> new ItemStack(Items.RED_DYE); // Placeholder

            case LEFT -> new ItemStack(Items.ARROW); // Placeholder
            case RIGHT -> new ItemStack(Items.ARROW); // Placeholder
            case UP, PROMOTE -> new ItemStack(Items.GLOWSTONE_DUST); // Placeholder
            case DOWN, DEMOTE -> new ItemStack(Items.GUNPOWDER); // Placeholder

            case CLOSE -> new ItemStack(Items.BARRIER); // Placeholder
            case CONFIRM -> new ItemStack(Items.LIME_DYE); // Placeholder

            case SHUFFLE -> new ItemStack(Items.PAPER); // Placeholder
            case EDIT -> new ItemStack(Items.WRITABLE_BOOK); // Placeholder

            case KILL -> new ItemStack(Items.WITHER_SKELETON_SKULL); // Placeholder
            case REVIVE -> new ItemStack(Items.SKELETON_SKULL); // Placeholder

            case REMOVE_VOTE -> new ItemStack(Items.GRAY_DYE); // Placeholder
            case RETURN_VOTE -> new ItemStack(Items.YELLOW_DYE); // Placeholder

            case TELEPORT -> new ItemStack(Items.ENDER_PEARL);

            case MORE -> new ItemStack(Items.PAPER);
            case LESS -> new ItemStack(Items.PAPER);

            case BAG -> {
                ItemStack item = new ItemStack(Items.BUNDLE);
                item.remove(DataComponentTypes.BUNDLE_CONTENTS);
                yield item;
            }

            default -> new ItemStack(Items.PAPER); // Fallback
        };
    }
}
