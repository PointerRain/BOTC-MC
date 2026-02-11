package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.*;


public class botcItemManager {
    private ItemStack generateWritableBook() {
        ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);

        // TODO: Prefill writable book with some info (current players, current role, etc)
        return stack;
    }

    ItemStack generateScriptBook(Script script) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        // Written book content
        ScriptBookGenerator bookPregenerator = new ScriptBookGenerator(script, null);
        bookPregenerator.generateWrittenBook();
        botc.LOGGER.info(bookPregenerator.getBookmarks());
        ScriptBookGenerator bookGenerator = new ScriptBookGenerator(script, bookPregenerator.getBookmarks());
        WrittenBookContentComponent contentComponent = bookGenerator.generateWrittenBook();
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, contentComponent);
        // Custom color
        if (script.meta().colour() != null) {
            DyedColorComponent colorComponent = new DyedColorComponent(script.colourInt()); // Dark Red color
            stack.set(DataComponentTypes.DYED_COLOR, colorComponent);
        }
        // Custom name
        MutableText name;
        if (script.meta().hideTitle()) {
            name = Text.literal("Script").styled(style -> style.withItalic(false));
        } else {
            name = script.toFormattedText().styled(style -> style.withBold(true).withItalic(false));
        }
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        // Hide tooltip
        TooltipDisplayComponent tooltipComponent = TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR, true)
                .with(DataComponentTypes.WRITTEN_BOOK_CONTENT, true);
        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltipComponent);
        return stack;
    }

    public void giveStarterItems(GameSpace gameSpace, Script script) {
        ItemStack stack1 = this.generateWritableBook();
        ItemStack stack2 = this.generateScriptBook(script);

        for (ServerPlayerEntity player : gameSpace.getPlayers().participants()) {
            player.getInventory().setStack(7, stack1.copy());
            player.getInventory().setStack(8, stack2.copy());
            player.currentScreenHandler.sendContentUpdates();
        }
    }

}
