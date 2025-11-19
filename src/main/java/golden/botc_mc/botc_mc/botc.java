package golden.botc_mc.botc_mc;

import golden.botc_mc.botc_mc.game.botcCommands;
import golden.botc_mc.botc_mc.game.botcConfig;
import golden.botc_mc.botc_mc.game.botcWaiting;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.api.game.GameType;

public class botc implements ModInitializer {

    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    // We will use the built-in ScreenHandlerType.GENERIC_9X1 when creating handlers to avoid
    // registry/mapping mismatches in the dev environment.

    public static final GameType<botcConfig> TYPE = GameType.<botcConfig>register(
            Identifier.of(ID, "botc-mc"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    @Override
    public void onInitialize() {
        botcCommands.register();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(botc.ID, "character_data_loader");
            }

            @Override
            public void reload(ResourceManager manager) {
                Resource baseCharacters = manager.getResource(Identifier.of("botc-mc:character_data/base_characters.json")).orElse(null);
                if (baseCharacters != null) {
                    Character.registerBaseCharacters(baseCharacters);
                    // Log some character data to verify loading
                    LOGGER.info(new Character("pithag"));
                } else {
                    LOGGER.error("Error reading base_characters.json");
                }

                // An example of loading a script resource.
                // TODO: Need to work out how to use it in the right place.
                Resource scriptResource =
                        manager.getResource(Identifier.of("botc-mc:scripts/trouble_brewing.json")).orElse(null);
                if (scriptResource != null) {
                    Script troubleBrewing = Script.fromResource(scriptResource);
                    LOGGER.info("Successfully found trouble_brewing.json");
                    LOGGER.info(troubleBrewing.toString());
                } else {
                    LOGGER.error("Error finding trouble_brewing.json");
                }


                for(Identifier id : manager.findResources("character_data", path -> path.toString().endsWith(".json")).keySet()) {
                    LOGGER.info("reloading {}", id);
                }
            }
        });
    }
}
