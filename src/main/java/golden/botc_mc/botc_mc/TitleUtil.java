package golden.botc_mc.botc_mc;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * Utility class for showing titles, subtitles, and action bar messages to players.
 */
public class TitleUtil {

    /**
     * Show a subtitle with default timings.
     * @param player Player to show the subtitle to.
     * @param text The subtitle text.
     */
    public static void showSubtitle(ServerPlayerEntity player, Text text) {
        showSubtitle(player, text, 10, 70, 20);
    }

    /**
     * Show a subtitle with custom timings.
     * @param player Player to show the subtitle to.
     * @param text The subtitle text.
     * @param fadeInTicks How many ticks to fade the text in for.
     * @param stayTicks How many ticks the text should remain visible.
     * @param fadeOutTicks How many ticks to fade the text out for.
     */
    public static void showSubtitle(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        queueSubtitle(player, text);
        showTitle(player, Text.empty(), fadeInTicks, stayTicks, fadeOutTicks);
    }

    /**
     * Queues a subtitle but does not send it. Can be sent by calling {@code showTitle(ServerPlayerEntity, Text)}
     * or {@code showTitle(ServerPlayerEntity, Text, int, int, int)}.
     * @param player Player to show the subtitle to.
     * @param text The subtitle text.
     */
    public static void queueSubtitle(ServerPlayerEntity player, Text text) {
        SubtitleS2CPacket packet = new SubtitleS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    /**
     * Show a title with default timings.
     * @param player Player to show the title to.
     * @param text The title text.
     */
    public static void showTitle(ServerPlayerEntity player, Text text) {
        showTitle(player, text, 10, 70, 20);
    }

    /**
     * Show a title with custom timings.
     * @param player Player to show the title to.
     * @param text The title text.
     * @param fadeInTicks How many ticks to fade the text in for.
     * @param stayTicks How many ticks the text should remain visible.
     * @param fadeOutTicks How many ticks to fade the text out for.
     */
    public static void showTitle(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        setTimings(player, fadeInTicks, stayTicks, fadeOutTicks);
        TitleS2CPacket packet = new TitleS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    /**
     * Show an action bar message with default timings.
     * @param player Player to show the message to.
     * @param text The message text.
     */
    public static void showActionBar(ServerPlayerEntity player, Text text) {
        showActionBar(player, text, 10, 70, 20);
    }

    /**
     * Show an action bar message with custom timings.
     * @param player Player to show the message to.
     * @param text The message text.
     * @param fadeInTicks How many ticks to fade the text in for.
     * @param stayTicks How many ticks the text should remain visible.
     * @param fadeOutTicks How many ticks to fade the text out for.
     */
    public static void showActionBar(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        setTimings(player, fadeInTicks, stayTicks, fadeOutTicks);
        OverlayMessageS2CPacket packet = new OverlayMessageS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    /**
     * Sets the timings for the next title, subtitle, or action bar message to be sent to the player.
     * @param player The player to set the timings for.
     * @param fadeInTicks How many ticks to fade the text in for.
     * @param stayTicks How many ticks the text should remain visible.
     * @param fadeOutTicks How many ticks to fade the text out for.
     */
    private static void setTimings(ServerPlayerEntity player, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        TitleFadeS2CPacket titleFadeS2CPacket = new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks);
        player.networkHandler.sendPacket(titleFadeS2CPacket);
    }

    /**
     * Show a totem pop effect to a player with a custom item.
     * @param player The player to show the totem effect to.
     * @param item The item to show as the effect.
     */
    public static void showTotemEffect(ServerPlayerEntity player, ItemStack item) {
        DeathProtectionComponent deathProtectionComponent = new DeathProtectionComponent(List.of());
        item.set(DataComponentTypes.DEATH_PROTECTION, deathProtectionComponent);

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

}
