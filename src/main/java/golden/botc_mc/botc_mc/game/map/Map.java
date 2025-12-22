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
     * Extract a string field value from a JSON string (minimal parser).
     * <p>
     * This method performs a simple string search without full JSON parsing.
     * It's suitable for reading simple string fields from JSON configuration files.
     *
     * @param json JSON content as a string
     * @param fieldName name of the field to extract
     * @return field value as a string, or null if not found or invalid format
     */
    public static String extractJsonField(String json, String fieldName) {
        int idx = json.indexOf("\"" + fieldName + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2).trim();
    }

    /**
     * Resolve a logical map id to a template Identifier using map_config files.
     * Map config files are located under data/botc-mc/map_config/<id>.json and contain a single field:
     * { "template": "botc-mc:test" }
     * If not found or invalid, fallback to given id.
     */
    private static Identifier resolveTemplateId(Identifier id) {
        String path = "data/botc-mc/map_config/" + id.getPath() + ".json";
        try (var in = Map.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                String json = new String(in.readAllBytes());
                String value = extractJsonField(json, "template");
                if (value != null && !value.isEmpty()) {
                    Identifier resolved = Identifier.of(value);
                    LOGGER.info("[Map] map_config {} resolved {} -> {}", path, id, resolved);
                    return resolved;
                } else {
                    LOGGER.warn("[Map] map_config {} missing template; using {}", path, id);
                }
            } else {
                LOGGER.debug("[Map] no classpath map_config resource for {} at {}", id, path);
            }
        } catch (Exception ex) {
            LOGGER.warn("[Map] error reading classpath map_config for {}: {}", id, ex.toString());
        }

        // Fallback: look in project-local maps/map_config/<id>.json on disk (useful during development and when maps are stored outside datapacks)
        try {
            java.nio.file.Path localPath = java.nio.file.Paths.get("maps", "map_config", id.getPath() + ".json");
            if (java.nio.file.Files.exists(localPath)) {
                String json = java.nio.file.Files.readString(localPath);
                String value = extractJsonField(json, "template");
                if (value != null && !value.isEmpty()) {
                    Identifier resolved = Identifier.of(value);
                    LOGGER.info("[Map] local map_config {} resolved {} -> {}", localPath, id, resolved);
                    return resolved;
                } else {
                    LOGGER.warn("[Map] local map_config {} missing template; using {}", localPath, id);
                }
            } else {
                LOGGER.debug("[Map] no local map_config file at {}", localPath);
            }
        } catch (Exception ex) {
            LOGGER.warn("[Map] error reading local map_config for {}: {}", id, ex.toString());
        }

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
        Identifier templateId = resolveTemplateId(identifier);
        LOGGER.info("[Map] Loading template {} (from map id {})", templateId, identifier);
        MapTemplate template;
        try {
            template = MapTemplateSerializer.loadFromResource(server, templateId);
        } catch (IOException e) {
            // First, attempt to load a local maps/<template>.nbt using reflection against MapTemplateSerializer
            java.nio.file.Path localMap = java.nio.file.Paths.get("maps", templateId.getPath() + ".nbt");
            if (java.nio.file.Files.exists(localMap)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(localMap)) {
                    // Use reflection to find a suitable static loader method returning MapTemplate
                    Class<?> cls = Class.forName("xyz.nucleoid.map_templates.MapTemplateSerializer");
                    MapTemplate loaded = null;
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                        if (!m.getReturnType().equals(MapTemplate.class)) continue;
                        Class<?>[] params = m.getParameterTypes();
                        try {
                            Object res = null;
                            if (params.length == 1) {
                                if (params[0].isAssignableFrom(java.io.InputStream.class)) {
                                    res = m.invoke(null, in);
                                } else if (params[0].isAssignableFrom(java.nio.file.Path.class)) {
                                    res = m.invoke(null, localMap);
                                } else if (params[0].isAssignableFrom(String.class)) {
                                    res = m.invoke(null, localMap.toString());
                                }
                            }
                            if (res instanceof MapTemplate mt) { loaded = mt; break; }
                        } catch (Throwable ignored) {
                            // try next candidate
                        }
                    }
                    if (loaded != null) {
                        template = loaded;
                        LOGGER.info("[Map] loaded local map template {} from {}", templateId, localMap);
                    } else {
                        String msg = "Map load failed for " + templateId + ": found local map file at " + localMap + " but could not load it via MapTemplateSerializer: " + e.getMessage();
                        LOGGER.error(msg, e);
                        throw new GameOpenException(Text.of(msg));
                    }
                } catch (IOException ioEx) {
                    String msg = "Map load failed for " + templateId + ": error reading local map file " + localMap + ": " + ioEx.getMessage();
                    LOGGER.error(msg, ioEx);
                    throw new GameOpenException(Text.of(msg));
                } catch (ClassNotFoundException cnf) {
                    String msg = "Map load failed for " + templateId + ": MapTemplateSerializer class not found when attempting local load";
                    LOGGER.error(msg, cnf);
                    throw new GameOpenException(Text.of(msg));
                }
            } else {
                String msg = "Map load failed for " + templateId + ": " + e.getMessage();
                LOGGER.error(msg, e);
                throw new GameOpenException(Text.of(msg));
            }
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