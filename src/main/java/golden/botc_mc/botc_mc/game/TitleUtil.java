package golden.botc_mc.botc_mc.game;

import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class TitleUtil {

    public static void showSubtitle(ServerPlayerEntity player, Text text) {
        showSubtitle(player, text, 10, 70, 20);
    }

    public static void showSubtitle(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        queueSubtitle(player, text);
        showTitle(player, Text.empty(), fadeInTicks, stayTicks, fadeOutTicks);
    }

    public static void queueSubtitle(ServerPlayerEntity player, Text text) {
        SubtitleS2CPacket packet = new SubtitleS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    public static void showTitle(ServerPlayerEntity player, Text text) {
        showTitle(player, text, 10, 70, 20);
    }

    public static void showTitle(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        setTimings(player, fadeInTicks, stayTicks, fadeOutTicks);
        TitleS2CPacket packet = new TitleS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    public static void showActionBar(ServerPlayerEntity player, Text text) {
        showActionBar(player, text, 10, 70, 20);
    }

    public static void showActionBar(ServerPlayerEntity player, Text text, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        setTimings(player, fadeInTicks, stayTicks, fadeOutTicks);
        OverlayMessageS2CPacket packet = new OverlayMessageS2CPacket(text);
        player.networkHandler.sendPacket(packet);
    }

    private static void setTimings(ServerPlayerEntity player, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        TitleFadeS2CPacket titleFadeS2CPacket = new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks);
        player.networkHandler.sendPacket(titleFadeS2CPacket);
    }

}
