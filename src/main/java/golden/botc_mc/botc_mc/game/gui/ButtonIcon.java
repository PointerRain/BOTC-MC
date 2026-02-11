package golden.botc_mc.botc_mc.game.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
    TELEPORT;

    ItemStack toItemStack() {
        return switch (this) {
            case ADD -> new ItemStack(Items.GREEN_DYE); // Placeholder
            case DELETE -> new ItemStack(Items.RED_DYE); // Placeholder

            case LEFT -> new ItemStack(Items.ARROW); // Placeholder
            case RIGHT -> new ItemStack(Items.ARROW); // Placeholder
            case UP, PROMOTE -> new ItemStack(Items.FEATHER); // Placeholder
            case DOWN, DEMOTE -> new ItemStack(Items.FEATHER); // Placeholder

            case CLOSE -> new ItemStack(Items.BARRIER); // Placeholder
            case CONFIRM -> new ItemStack(Items.LIME_DYE); // Placeholder

            case SHUFFLE -> new ItemStack(Items.PAPER); // Placeholder
            case EDIT -> new ItemStack(Items.WRITABLE_BOOK); // Placeholder

            case KILL -> new ItemStack(Items.WITHER_SKELETON_SKULL); // Placeholder
            case REVIVE -> new ItemStack(Items.SKELETON_SKULL); // Placeholder

            case REMOVE_VOTE -> new ItemStack(Items.GRAY_DYE); // Placeholder
            case RETURN_VOTE -> new ItemStack(Items.YELLOW_DYE); // Placeholder

            case TELEPORT -> new ItemStack(Items.ENDER_PEARL);

            default -> new ItemStack(Items.PAPER); // Fallback
        };
    }
}

// Button icons
// Add       +
// Delete    bin
// Left      <    spectator scroll left /highlighted
// Right     >    spectator scroll right /highlighted
// Close     X    invite reject /highlighted
// Confirm   ✓    invite accept /highlighted
// Shuffle   ><
// Up        ^    statistics sort up /highlighted
// Down      v    statistics sort down /highlighted
// Edit      ✎
// Kill      skull
// Revive    skull
