package golden.botc_mc.botc_mc.voting;
import golden.botc_mc.botc_mc.game.botcPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.core.jmx.Server;

import java.util.List;

public class VotingMain {
    /**
     * Getting a list of players and giving them the voting items
     */

    static VoteResult currentNomination;

    public static void nominate(ServerPlayerEntity nominator, ServerPlayerEntity nominee) {
        currentNomination = VoteResult.unfinishedVote(nominator, nominee);
    }

    public static void distributeVotingItems(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack NO = new ItemStack(Items.RED_CONCRETE, 1);
            ItemStack YES = new ItemStack(Items.LIME_CONCRETE, 1);
            player.getInventory().insertStack(NO);
            player.getInventory().insertStack(YES);
        }
    }

    public static void Votestart(MinecraftServer server) {
        int voteCount = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack main = player.getMainHandStack();
            if (main.getItem() == Items.LIME_CONCRETE) {
                voteCount++;
            }
            // Check mainhand, get the item type, if they are holding e.g lime concrete add 1 to voteCount
            player.sendMessage(Text.literal("Count: " + voteCount), true);
        }
        currentNomination = VoteResult.resolveVote(currentNomination , List.of() , voteCount);
    }

    public record VoteResult(ServerPlayerEntity nominator, ServerPlayerEntity nominee, List<ServerPlayerEntity> voters,
                             int votes) {
        public static VoteResult unfinishedVote(ServerPlayerEntity nominator, ServerPlayerEntity nominee) {
            return new VoteResult(nominator, nominee, List.of(), 0);


        }
        // needs to take in current vote object, list of players, and the voteCount.
        public static VoteResult resolveVote(VoteResult voteresult ,List<ServerPlayerEntity> voters , int votes) {
            return new VoteResult(voteresult.nominator() , voteresult.nominee() , voters , votes);
        }


    }
}