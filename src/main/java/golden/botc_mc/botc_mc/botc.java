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

import java.util.HashMap;
import java.util.Map;

public class botc implements ModInitializer {

    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final Map<String, Script> scripts = new HashMap<>();

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
                    LOGGER.info(new Character("washerwoman"));
                } else {
                    LOGGER.error("Error reading base_characters.json");
                }

                for(Identifier id : manager.findResources("scripts", path -> path.toString().endsWith(".json")).keySet()) {
                    LOGGER.info("Loading {}...", String.valueOf(id));
                    manager.getResource(id).ifPresent(script -> {
                        scripts.put(String.valueOf(id), Script.fromResource(script));
                    });
                }
                LOGGER.info("Loaded {} scripts", scripts.size());
            }
        });
    }
}
