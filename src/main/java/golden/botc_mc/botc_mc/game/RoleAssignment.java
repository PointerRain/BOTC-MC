package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.gui.TokenItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

public class RoleAssignment {

    public static void showTotemEffect(ServerPlayerEntity player, ItemStack item) {
        DeathProtectionComponent deathProtectionComponent = new DeathProtectionComponent(List.of());
        item.set(DataComponentTypes.DEATH_PROTECTION, deathProtectionComponent);

        botc.LOGGER.info("Triggering pop with item: {}", item);

        // Store the item in the player's offhand to restore later
        ItemStack previousOffhand = player.getOffHandStack();
        // Set the offhand item to the pop item stack
        player.setStackInHand(Hand.OFF_HAND, item);
        // Send the item update to the client to ensure it sees the new offhand item before the pop effect
        player.currentScreenHandler.sendContentUpdates();
        // Trigger the totem pop effect
        player.getWorld().sendEntityStatus(player, (byte) 35);
        // Restore the player's original offhand item after the pop effect
        player.setStackInHand(Hand.OFF_HAND, previousOffhand);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void sendCharacter(ServerPlayerEntity player, botcCharacter character) {

        showTotemEffect(player, TokenItemStack.of(character));

        MutableText titleText = Text.literal("You are the ").formatted(character.team().getColour(false), Formatting.BOLD);
        titleText.append(character.toText());
//        String title = "You are the " + character.;
//        MutableText titleText = Text.literal(title).formatted(character.team().getColour(false), Formatting.BOLD);
//        TitleUtil.queueSubtitle(player, Text.of("Subtitle test"));
//        TitleUtil.showTitle(player, Text.empty());
        TitleUtil.showSubtitle(player, titleText);
    }
}



/*
Show GUI to select in play roles
Hover showing how many of each are expected
Once as many roles as seats are selected, the finalise button can be clicked
Assign one role to each seat
Shuffle roles and assign sequentially
On assignment also display the effects

Some kind of mechanism to resend characters?
 */