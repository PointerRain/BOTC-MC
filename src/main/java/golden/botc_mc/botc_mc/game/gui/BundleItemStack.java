package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.DyeColor;

import java.util.List;

public class BundleItemStack {
    public static ItemStack of(DyeColor color) {
        ItemStack item = new ItemStack(
                (color != null) ? BundleItem.getBundle(color) : Items.BUNDLE
        );
        item.remove(DataComponentTypes.BUNDLE_CONTENTS);
        TooltipDisplayComponent tooltipComponent = TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR, true)
                .with(DataComponentTypes.WRITTEN_BOOK_CONTENT, true);
        item.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltipComponent);
        return item;
    }

    public static ItemStack of(Script script) {
        DyeColor color = switch (script.meta().name()) {
            // Matching on name is very yucky but missing shouldn't have consequences
            case "Trouble Brewing" -> DyeColor.RED;
            case "Sects & Violets" -> DyeColor.PURPLE;
            case "Bad Moon Rising" -> DyeColor.ORANGE;
            case "Garden of Sin" -> DyeColor.GREEN;
            case "The Tomb" -> DyeColor.MAGENTA;
            case "Midnight in the House of the Damned" -> DyeColor.BLUE;
            default -> null;
        };
        ItemStack item = of(color);
        if (script.hasColour()) {
            item.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(script.colourInt()));
        }
        return item;
    }

    public static ItemStack of(Script script, List<botcCharacter> selectedItems) {
        ItemStack item = of(script);
        if (selectedItems != null && !selectedItems.isEmpty()) {
            BundleContentsComponent contents = new BundleContentsComponent(selectedItems.stream().map(
                    TokenItemStack::of
            ).toList());

            item.set(DataComponentTypes.BUNDLE_CONTENTS, contents);
        }
        return item;
    }
}
