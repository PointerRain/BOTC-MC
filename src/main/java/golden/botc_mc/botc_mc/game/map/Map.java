package golden.botc_mc.botc_mc.game.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Loaded map wrapper for a BOTC game instance.
 * <p>Holds a {@link MapTemplate} and derived respawn regions (spawn + ordered checkpoints).
 * No block mutation logic is kept here: world generation is delegated to {@link #asGenerator(MinecraftServer)}.
 */
public final class Map {
    /** Logger scoped to map loading and region extraction. */
    private static final Logger LOGGER = LogManager.getLogger("botc.Map");
    /** Derived respawn regions (immutable after construction). */
    private final Regions regions;
    /** Underlying immutable template returned by the map templates API. */
    private final MapTemplate template;
    /** Current expected map_format integer. Used for soft version compatibility warnings only. */
    private static final int CURRENT_MAP_FORMAT = 1;

    /**
     * Internal constructor building region lists from a loaded template.
     * Extraction rules:
     * <ul>
     *   <li><b>Spawn</b>: first region tagged "spawn"; falls back to {@link RespawnRegion#DEFAULT} if absent.</li>
     *   <li><b>Checkpoints</b>: all regions tagged "checkpoint" that define an integer <code>index</code>; sorted ascending. If none found, a single DEFAULT is used.</li>
     *   <li>Yaw/Pitch: taken from region metadata keys <code>yaw</code> and <code>pitch</code>, defaulting to the DEFAULT region values.</li>
     * </ul>
     * @param template loaded map template
     */
    private Map(MapTemplate template) {
        this.template = template;

        int mapFormat = template.getMetadata().getData().getInt("map_format", 0);
        // Mismatch warnings only; never blocks load. Allows forward/backward compatible experimentation.
        if (mapFormat < CURRENT_MAP_FORMAT) {
            LOGGER.warn("Map template older format ({} < {}), continuing.", mapFormat, CURRENT_MAP_FORMAT);
        } else if (mapFormat > CURRENT_MAP_FORMAT) {
            LOGGER.warn("Map template newer format ({} > {}), continuing.", mapFormat, CURRENT_MAP_FORMAT);
        }

        // Collect checkpoint regions with defined index; ensure stable ordering by numeric index.
        List<RespawnRegion> checkpoints = template.getMetadata()
                .getRegions("checkpoint")
                .filter(cp -> cp.getData().getInt("index").isPresent())
                .sorted(Comparator.comparingInt(cp -> cp.getData().getInt("index").orElseThrow()))
                .map(RespawnRegion::of)
                .toList();
        if (checkpoints.isEmpty()) checkpoints = java.util.List.of(RespawnRegion.DEFAULT);

        // Single spawn region; first match or DEFAULT.
        RespawnRegion spawn = template.getMetadata()
                .getRegions("spawn")
                .map(RespawnRegion::of)
                .findFirst()
                .orElse(RespawnRegion.DEFAULT);

        this.regions = new Regions(checkpoints, spawn);
    }

    /**
     * Resolve a logical map id to a template Identifier using map_config files.
     * Map config files are located under data/botc-mc/map_config/<id>.json and contain a single field:
     * { "template": "botc-mc:test" }
     * If not found or invalid, fallback to given id.
     */
    private static Identifier resolveTemplateId(MinecraftServer server, Identifier id) {
        try {
            Path root = server.getDataPackManager().getResourcePackContainer().getPath(); // may not be accessible; fallback to project root
        } catch (Throwable ignored) {}
        // Fallback simple resolution: look in classpath resources under data/botc-mc/map_config
        String path = "data/botc-mc/map_config/" + id.getPath() + ".json";
        try (var in = Map.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                String json = new String(in.readAllBytes());
                int idx = json.indexOf("\"template\"");
                if (idx >= 0) {
                    int colon = json.indexOf(":", idx);
                    int quote1 = json.indexOf('"', colon);
                    int quote2 = json.indexOf('"', quote1 + 1);
                    if (quote1 > 0 && quote2 > quote1) {
                        String template = json.substring(quote1 + 1, quote2).trim();
                        return Identifier.of(template);
                    }
                }
            }
        } catch (Exception ignored) {}
        return id;
    }

    /**
     * Load map template and derive respawn metadata.
     * @param server Minecraft server for resource access
     * @param identifier namespaced id (e.g. {@code botc-mc:test})
     * @return new Map instance
     * @throws GameOpenException if template resource cannot be read
     */
    public static Map load(MinecraftServer server, Identifier identifier) {
        MapTemplate template;
        Identifier templateId = resolveTemplateId(server, identifier);
        try {
            template = MapTemplateSerializer.loadFromResource(server, templateId);
        } catch (IOException e) {
            String msg = "Map load failed for " + templateId + ": " + e.getMessage();
            LOGGER.error(msg, e);
            throw new GameOpenException(Text.of(msg));
        }
        return new Map(template);
    }

    /**
     * Create a chunk generator serving blocks directly from the template.
     * @param server server instance providing dimension context
     * @return template-backed chunk generator
     */
    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }

    /**
     * Access respawn region set.
     * @return spawn + ordered checkpoints
     */
    public Regions getRegions() { return this.regions; }

    /**
     * Spawn/respawn region definition.
     * @param bounds region bounds
     * @param yaw default player facing yaw
     * @param pitch default player facing pitch
     */
    public record RespawnRegion(BlockBounds bounds, float yaw, float pitch) {
        /** Fallback region at origin with zero orientation. */
        public static final RespawnRegion DEFAULT = new RespawnRegion(BlockBounds.ofBlock(BlockPos.ORIGIN), 0.0f, 0.0f);

        /**
         * Convert template region metadata to a RespawnRegion, applying default yaw/pitch if missing.
         * @param templateRegion source region
         * @return converted respawn region
         */
        private static RespawnRegion of(TemplateRegion templateRegion) {
            return new RespawnRegion(
                    templateRegion.getBounds(),
                    templateRegion.getData().getFloat("yaw", DEFAULT.yaw()),
                    templateRegion.getData().getFloat("pitch", DEFAULT.pitch()));
        }

        /** Center block used for teleport/spawn operations.
         * @return block position at bounds center
         */
        public BlockPos centerBlock() { return BlockPos.ofFloored(this.bounds.center()); }
    }

    /**
     * Region container grouping checkpoints and spawn.
     * @param checkpoints ordered respawn checkpoints
     * @param spawn primary spawn region
     */
    public record Regions(List<RespawnRegion> checkpoints, RespawnRegion spawn) {}
}