package golden.botc_mc.botc_mc.game.map;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;
import xyz.nucleoid.plasmid.api.game.GameOpenException;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

/**
 * Represents loaded map metadata, regions, and attributes used for a BOTC session.
 * Maintains spawn and checkpoint respawn regions extracted from the template.
 */
public final class Map {
    private static final Logger LOGGER = LogManager.getLogger("botc.Map");

    private final Regions regions;

    private final MapTemplate template;

    private static final int CURRENT_MAP_FORMAT = 1;

    /** Internal constructor building region lists from a loaded template. */
    private Map(MapTemplate template) {
        this.template = template;

        int mapFormat = template.getMetadata()
                .getData()
                .getInt("map_format", 0);

        // Allow loading even if map_format mismatches for now.
        if (mapFormat < CURRENT_MAP_FORMAT) {
            LOGGER.warn("Map template is from an older format ({} < {}), continuing anyway.", mapFormat, CURRENT_MAP_FORMAT);
        } else if (mapFormat > CURRENT_MAP_FORMAT) {
            LOGGER.warn("Map template is from a newer format ({} > {}), continuing anyway.", mapFormat, CURRENT_MAP_FORMAT);
        }

        List<RespawnRegion> checkpoints = template.getMetadata()
                .getRegions("checkpoint")
                .filter(cp -> cp.getData().getInt("index").isPresent())
                .sorted(Comparator.comparingInt(cp -> cp.getData().getInt("index").orElseThrow()))
                .map(RespawnRegion::of)
                .toList();

        if (checkpoints.isEmpty()) {
            checkpoints = java.util.List.of(RespawnRegion.DEFAULT);
        }

        RespawnRegion spawn = template.getMetadata()
                .getRegions("spawn")
                .map(RespawnRegion::of)
                .findFirst()
                .orElse(RespawnRegion.DEFAULT);

        this.regions = new Regions(checkpoints, spawn);
    }

    /**
     * Load map metadata and resources by identifier.
     * @param server Minecraft server instance used to access resource manager
     * @param identifier namespaced map id (e.g. <code>botc-mc:test</code>)
     * @return loaded Map instance
     * @throws xyz.nucleoid.plasmid.api.game.GameOpenException if the underlying template cannot be read
     */
    public static Map load(MinecraftServer server, Identifier identifier) {
        MapTemplate template;
        try {
            template = MapTemplateSerializer.loadFromResource(server, identifier);
        } catch (IOException e) {
            // Log a clear error and throw a GameOpenException so only this game open is aborted.
            String msg = "Map load failed for " + identifier + ": " + e.getMessage();
            LOGGER.error(msg, e);
            throw new GameOpenException(Text.of(msg));
        }

        return new Map(template);
    }

    /**
     * Creates a chunk generator that will serve blocks from this loaded template.
     * @param server Minecraft server instance (world/dimension context)
     * @return template-backed chunk generator
     */
    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }

    /**
     * Access the spawn and checkpoint regions contained in this map.
     * @return regions record (spawn + ordered checkpoints)
     */
    public Regions getRegions() { return this.regions; }

    /**
     * Spawn/respawn region definition.
     * @param bounds block bounds of the region
     * @param yaw default facing yaw for player spawn
     * @param pitch default facing pitch for player spawn
     */
    public record RespawnRegion(BlockBounds bounds, float yaw, float pitch) {
        public static final RespawnRegion DEFAULT = new RespawnRegion(BlockBounds.ofBlock(BlockPos.ORIGIN), 0.0f, 0.0f);

        /**
         * Factory converting a template region to a respawn region, applying default yaw/pitch
         * if not explicitly provided in region metadata.
         * @param templateRegion source template region
         * @return respawn region instance
         */
        private static RespawnRegion of(TemplateRegion templateRegion) {
            return new RespawnRegion(
                    templateRegion.getBounds(),
                    templateRegion.getData().getFloat("yaw", DEFAULT.yaw()),
                    templateRegion.getData().getFloat("pitch", DEFAULT.pitch()));
        }

        /**
         * Compute the center block position used for spawning players into this region.
         * @return center block position
         */
        public BlockPos centerBlock() {
            return BlockPos.ofFloored(this.bounds.center());
        }
    }

    /**
     * Region container grouping checkpoints and spawn.
     * @param checkpoints ordered checkpoint respawn regions
     * @param spawn primary spawn region
     */
    public record Regions(List<RespawnRegion> checkpoints, RespawnRegion spawn) {}
}