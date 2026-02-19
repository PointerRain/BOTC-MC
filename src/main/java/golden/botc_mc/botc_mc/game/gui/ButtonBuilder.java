package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElement;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Utility class for building button GuiElements with consistent styling and behaviour.
 */
public class ButtonBuilder {
    /**
     * Build a simple button GuiElement with a paper icon and custom name and click callback.
     * Prefer using {@link #buildButton(Text, ButtonIcon, GuiElement.ClickCallback)}
     * or {@link #buildButton(Text, ItemStack, GuiElement.ClickCallback)} with a button icon.
     * @param name     The display name of the button.
     * @param callback The click callback for the button.
     * @return The constructed GuiElement button.
     */
    @SuppressWarnings("unused") // Suppress unused warnings for this method, as it is convenient default button builder.
    public static GuiElement buildButton(Text name, GuiElement.ClickCallback callback) {
        return buildButton(name, new ItemStack(Items.PAPER), callback);
    }

    /**
     * Build a button GuiElement with a custom icon, name, and click callback.
     * @param name     The display name of the button.
     * @param icon     The icon type for the button.
     * @param callback The click callback for the button.
     * @return The constructed GuiElement button.
     */
    public static GuiElement buildButton(Text name, ButtonIcon icon, GuiElement.ClickCallback callback) {
        return buildButton(name, icon.toItemStack(), callback);
    }

    /**
     * Build a button GuiElement with a custom icon ItemStack, name, and click callback.
     * @param name     The display name of the button.
     * @param icon     The icon ItemStack for the button.
     * @param callback The click callback for the button.
     * @return The constructed GuiElement button.
     */
    public static GuiElement buildButton(Text name, ItemStack icon, GuiElement.ClickCallback callback) {
        ItemStack itemButton = icon.copy();
        name = name.copy().setStyle(Style.EMPTY.withColor(Formatting.WHITE).withItalic(false));
        itemButton.set(DataComponentTypes.CUSTOM_NAME, name);
        return new GuiElement(itemButton, callback);
    }
}
