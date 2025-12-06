package golden.botc_mc.botc_mc.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.*;


public class botcItemManager {
    private ItemStack generateWritableBook() {
        ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);

        // TODO: Prefill writable book with some info (current players, current role, etc)
        return stack;
    }

    private ItemStack generateTroubleBrewingRuleBook() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        String[] pages= {
            "\n\n§4§l§nTrouble Brewing§r",
            "§1§l§nTownsfolk - Good§r\n- §9§r §9Washerwoman§r\n- §9§r §9Librarian§r\n- §9§r §9Investigator§r\n- §9§r §9Chef§r\n- §9§r §9Empath§r\n- §9§r §9Fortune Teller§r\n- §9§r §9Undertaker§r\n- §9§r §9Monk§r\n- §9§r §9Ravenkeeper§r\n- §9§r §9Maiden§r\n- §9§r §9Slayer§r\n- §9§r §9Soldier§r\n- §9§r §9Mayor§r",
            "§3§l§nOutsider - Good§r\n- §b§r §bButler§r\n- §b§r §bDrunk§r\n- §b§r §bRecluse§r\n- §b§r §bSaint§r\n\n§c§l§nMinion - Evil§r\n- §c§r §cPoisoner§r\n- §c§r §cSpy§r\n- §c§r §cScarlet Woman§r\n- §c§r §cBaron§r\n\n§4§l§nDemon - Evil§r\n- §4§r §4Imp§r",
            "§1§r §1§l§nWasherwoman§r§6§r\n§9Townsfolk - Good§r\nYou start knowing that 1 of 2 players is a particular Townsfolk.\n\n§1§r §1§l§nLibrarian§r§6§r\n§9Townsfolk - Good§r\nYou start knowing that 1 of 2 players is a particular Outsider. (Or that zero are in play.)",
            "§1§r §1§l§nInvestigator§r§6§r\n§9Townsfolk - Good§r\nYou start knowing that 1 of 2 players is a particular Minion.\n\n\n§1§r §1§l§nChef§r§6§r\n§9Townsfolk - Good§r\nYou start knowing how many pairs of evil players there are.",
            "§1§r §1§l§nEmpath§r§6§r\n§9Townsfolk - Good§r\nEach night, you learn how many of your 2 alive neighbors are evil.\n§1§r §1§l§nFortune Teller§r§6§r\n§9Townsfolk - Good§r\nEach night, choose 2 players: you learn if either is a Demon. There is a good player that registers as a Demon to you.",
            "§1§r §1§l§nUndertaker§r§6§r\n§9Townsfolk - Good§r\nEach night*, you learn which character died by execution today.\n\n\n§1§r §1§l§nMonk§r§6§r\n§9Townsfolk - Good§r\nEach night*, choose a player (not yourself): they are safe from the Demon tonight.",
            "§1§r §1§l§nRavenkeeper§r§6§r\n§9Townsfolk - Good§r\nIf you die at night, you are woken to choose a player: you learn their character.\n§1§r §1§l§nMaiden§r§6§r\n§9Townsfolk - Good§r\nThe 1st time you are nominated, if the nominator is a Townsfolk, they are executed immediately.",
            "§1§r §1§l§nSlayer§r§6§r\n§9Townsfolk - Good§r\nOnce per game, during the day, publicly choose a player: if they are the Demon, they die.\n\n\n§1§r §1§l§nSoldier§r§6§r\n§9Townsfolk - Good§r\nYou are safe from the Demon.",
            "§1§r §1§l§nMayor§r§6§r\n§9Townsfolk - Good§r\nIf only 3 players live & no execution occurs, your team wins. If you die at night, another player might die instead.",
            "§3§r §3§l§nButler§r§6§r\n§bOutsider - Good§r\nEach night, choose a player (not yourself): tomorrow, you may only vote if they are voting too.\n§3§r §3§l§nDrunk§r§6§r\n§bOutsider - Good§r\nYou do not know you are the Drunk. You think you are a Townsfolk character, but you are not.",
            "§3§r §3§l§nRecluse§r§6§r\n§bOutsider - Good§r\nYou might register as evil & as a Minion or Demon, even if dead.\n\n\n§3§r §3§l§nSaint§r§6§r\n§bOutsider - Good§r\nIf you die by execution, your team loses.",
            "§c§r §c§l§nPoisoner§r§6§r\n§cMinion - Evil§r\nEach night, choose a player: they are poisoned tonight and tomorrow day.\n§c§r §c§l§nSpy§r§6§r\n§cMinion - Evil§r\nEach night, you see the Grimoire. You might register as good & as a Townsfolk or Outsider, even if dead.",
            "§c§r §c§l§nScarlet Woman§r§6§r\n§cMinion - Evil§r\nIf there are 5 or more players alive & the Demon dies, you become the Demon. (Travellers don't count.)\n\n§c§r §c§l§nBaron§r§6§r\n§cMinion - Evil§r\nThere are extra Outsiders in play. [+2 Outsiders]",
            "§4§r §4§l§nImp§r§6§r\n§4Demon - Evil§r\nEach night*, choose a player: they die. If you kill yourself this way, a Minion becomes the Imp.",
            "§l§nFirst Night Order§r\n 1. §7Dusk§r\n 2. §cMinion Info§r\n 3. §4Demon Info§r\n 4. §c§r §cPoisoner§r\n 5. §9§r §9Washerwoman§r\n 6. §9§r §9Librarian§r\n 7. §9§r §9Investigator§r\n 8. §9§r §9Chef§r\n 9. §9§r §9Empath§r\n10. §9§r §9Fortune Teller§r\n11. §b§r §bButler§r\n12. §c§r §cSpy§r\n13. §7Dawn§r",
            "§l§nOther Night Order§r\n 1. §7Dusk§r\n 2. §c§r §cPoisoner§r\n 3. §9§r §9Monk§r\n 4. §c§r §cScarlet Woman§r\n 5. §4§r §4Imp§r\n 6. §9§r §9Ravenkeeper§r\n 7. §9§r §9Empath§r\n 8. §9§r §9Fortune Teller§r\n 9. §9§r §9Undertaker§r\n10. §b§r §bButler§r\n11. §c§r §cSpy§r\n12. §7Dawn§r",
            "§l§nPlayer Counts§r\n 5: §93§r, §b0§r, §c1§r, §41§r\n 6: §93§r, §b1§r, §c1§r, §41§r\n 7: §95§r, §b0§r, §c1§r, §41§r\n 8: §95§r, §b1§r, §c1§r, §41§r\n 9: §95§r, §b2§r, §c1§r, §41§r\n10: §97§r, §b0§r, §c2§r, §41§r\n11: §97§r, §b1§r, §c2§r, §41§r\n12: §97§r, §b2§r, §c2§r, §41§r\n13: §99§r, §b0§r, §c3§r, §41§r\n14: §99§r, §b1§r, §c3§r, §41§r\n15: §99§r, §b2§r, §c3§r, §41§r"
        };

        

        WrittenBookContentComponent contentComponent = new WrittenBookContentComponent(
            RawFilteredPair.of("§4§lTrouble Brewing§r"), //title
            "", //author
            0, //version
            List.of(
                RawFilteredPair.of(Text.of(pages[0])),
                RawFilteredPair.of(Text.of(pages[1])),
                RawFilteredPair.of(Text.of(pages[2])),
                RawFilteredPair.of(Text.of(pages[3])),
                RawFilteredPair.of(Text.of(pages[4])),
                RawFilteredPair.of(Text.of(pages[5])),
                RawFilteredPair.of(Text.of(pages[6])),
                RawFilteredPair.of(Text.of(pages[7])),
                RawFilteredPair.of(Text.of(pages[8])),
                RawFilteredPair.of(Text.of(pages[9])),
                RawFilteredPair.of(Text.of(pages[10])),
                RawFilteredPair.of(Text.of(pages[11])),
                RawFilteredPair.of(Text.of(pages[12])),
                RawFilteredPair.of(Text.of(pages[13])),
                RawFilteredPair.of(Text.of(pages[14])),
                RawFilteredPair.of(Text.of(pages[15])),
                RawFilteredPair.of(Text.of(pages[16])),
                RawFilteredPair.of(Text.of(pages[17])) // TODO: Remove this entire block of text to instead have some kind of generator
            ), // pages
            false); //processed (just put false)


        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, contentComponent);

        return stack;
    }

    public void giveStarterItems(GameSpace gameSpace) {
        ItemStack stack1 = this.generateWritableBook();
        ItemStack stack2 = this.generateTroubleBrewingRuleBook();

        for (ServerPlayerEntity player : gameSpace.getPlayers().participants()) {
            player.getInventory().setStack(7, stack1.copy());
            player.getInventory().setStack(8, stack2.copy());
            player.currentScreenHandler.sendContentUpdates();
        }
    }

}
