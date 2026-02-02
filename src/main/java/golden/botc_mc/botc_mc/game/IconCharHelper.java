package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.botc;

public class IconCharHelper {

    // Icons in canonical order matching the font resource
    // TODO: Take this from a data asset
    private static final String[] ICON_CHARS = {"angler", "archer", "arms_up", "ask", "blade", "bloom", "book", "brewer", "burn", "circle", "cog", "danger", "dream", "explorer", "feather", "flow", "friend", "gamble", "grasp", "grin", "guster", "heart", "heartbreak", "howl", "juggle", "miner", "mourner", "orbit", "peer", "play", "plenty", "prize", "rule", "scoop", "scrape", "sheaf", "shear", "shelter", "skull", "snort", "soar", "sweet", "tentacle", "till", "triangle", "wear", "gate"};
    private static final char START_CHAR = '\uEB07'; // Private Use Area start for custom icons

    public static char getIconChar(String iconId) {
        for (int i = 0; i < ICON_CHARS.length; i++) {
            if (ICON_CHARS[i].equalsIgnoreCase(iconId)) {
                return (char) (START_CHAR + i);
            }
        }
        botc.LOGGER.warn("Unknown icon ID '{}'.", iconId);
        return ' ';
    }

    public static char getIconChar(botcCharacter character) {
        String token = character.token();
        if (token == null || token.isEmpty()) {
            botc.LOGGER.warn("Character {} has no token defined.", character.id());
            return ' ';
        }
        return getIconChar(token);
    }
}
