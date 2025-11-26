package golden.botc_mc.botc_mc.voting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;


public class VotingMain {
    /**
     * Getting a list of players and giving them the voting items
     */
    public static void distributeVotingItems(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack NO = new ItemStack(Items.RED_CONCRETE,1);
            ItemStack YES = new ItemStack(Items.LIME_CONCRETE, 1);
            player.getInventory().insertStack(NO);
            player.getInventory().insertStack(YES);
        }
    }

    public static void Votestart(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack main = player.getMainHandStack();
            player.sendMessage(Text.literal("Item: " + main), true);
            

        }
    }
}
