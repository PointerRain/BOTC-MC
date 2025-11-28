package golden.botc_mc.botc_mc.game.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
 *
 * <p>Map templates may optionally include a <code>podium_center</code> metadata key with a
 * CSV encoded block position ("x,y,z"). If present this position will be exposed via
 * {@link #getPodiumCenter()} and can be used by map-aware features (for example podium
 * generators) to center generated content.
 */
public final class Map {
    /** Logger scoped to map loading and region extraction. */
    private static final Logger LOGGER = LogManager.getLogger("botc.Map");
    /** Derived respawn regions (immutable after construction). */
    private final Regions regions;
    /** Underlying immutable template returned by the map templates API. */
    private final MapTemplate template;
    /** Optional hard-coded podium center specified by map author via template metadata. */
    private final BlockPos podiumCenter; // nullable: null when absent
    /** Optional podium radius (in blocks) parsed from template metadata or companion JSON. */
    private final Double podiumRadius; // nullable: null when absent
    /** Current expected map_format integer. Used for soft version compatibility warnings only. */
    private static final int CURRENT_MAP_FORMAT = 1;
    /** Optional podium template identifier from metadata or JSON overrides. */
    private final Identifier podiumTemplateId;

    /** Default podium template fallback when none provided by map. */
    private static final Identifier DEFAULT_PODIUM_TEMPLATE = Identifier.of("botc-mc", "podiums/podium-north");

    /**
     * Primary constructor delegating to extended constructor with empty override.
     */
    private Map(MapTemplate template) { this(template, null, null, null); }

    /**
     * Internal constructor building region lists from a loaded template, with optional overrides
     * for the podium center and podium radius (used when a companion JSON provides values).
     * @param template loaded map template
     * @param podiumOverride nullable podium center to use instead of template metadata
     * @param podiumRadiusOverride nullable podium radius override (in blocks)
     * @param podiumTemplateOverride nullable podium template identifier to use instead of metadata
     */
    private Map(MapTemplate template, BlockPos podiumOverride, Double podiumRadiusOverride, Identifier podiumTemplateOverride) {
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

        // Determine podium center: use override when present, otherwise template metadata
        if (podiumOverride != null) {
            this.podiumCenter = podiumOverride;
            LOGGER.info("Using podium_center override: {}", podiumOverride);
        } else {
            String podiumCsv = template.getMetadata().getData().getString("podium_center", "");
            if (podiumCsv == null || podiumCsv.isBlank()) {
                this.podiumCenter = null;
            } else {
                BlockPos parsed = parseCsvBlockPos(podiumCsv);
                this.podiumCenter = parsed;
                if (parsed != null) {
                    LOGGER.info("Map provides podium_center metadata: {}", parsed);
                }
            }
        }

        // Determine podium radius: prefer explicit override argument, otherwise try template metadata
        if (podiumRadiusOverride != null) {
            LOGGER.info("Using podium_radius override: {}", podiumRadiusOverride);
            this.podiumRadius = podiumRadiusOverride;
        } else {
            Double fromMeta = null;
            try {
                String s = template.getMetadata().getData().getString("podium_radius", "");
                if (s != null && !s.isBlank()) {
                    fromMeta = Double.parseDouble(s.trim());
                    LOGGER.info("Read podium_radius from template metadata: {}", fromMeta);
                }
                if (fromMeta == null) {
                    String sd = template.getMetadata().getData().getString("podium_diameter", "");
                    if (sd != null && !sd.isBlank()) fromMeta = Double.parseDouble(sd.trim()) / 2.0;
                }
            } catch (Throwable ignored) { fromMeta = null; }
            this.podiumRadius = fromMeta;
        }

        // Determine podium template identifier: use override when present, otherwise template metadata
        Identifier resolvedTemplateId;
        if (podiumTemplateOverride != null) {
            resolvedTemplateId = podiumTemplateOverride;
        } else {
            Identifier fromMeta = null;
            try {
                String templateStr = template.getMetadata().getData().getString("podium_template", "");
                if (templateStr != null && !templateStr.isBlank()) {
                    fromMeta = Identifier.tryParse(templateStr.trim());
                }
            } catch (Throwable ignored) {}
            resolvedTemplateId = fromMeta;
        }
        if (resolvedTemplateId == null) {
            resolvedTemplateId = DEFAULT_PODIUM_TEMPLATE;
        }
        this.podiumTemplateId = resolvedTemplateId;

        this.regions = new Regions(checkpoints, spawn);
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
        try {
            template = MapTemplateSerializer.loadFromResource(server, identifier);
        } catch (IOException e) {
            String msg = "Map load failed for " + identifier + ": " + e.getMessage();
            LOGGER.error(msg, e);
            throw new GameOpenException(Text.of(msg));
        }

        // Build map normally and then check companion plasmid JSON for optional overrides (center and radius)
        Map result = new Map(template);
        try {
            // Primary expected location: data/<ns>/plasmid/game/<idPath>.json
            Identifier resourceId = Identifier.of(identifier.getNamespace(), "plasmid/game/" + identifier.getPath() + ".json");
            var primary = server.getResourceManager().getResource(resourceId);
            if (primary.isPresent()) {
                try (Reader r = new InputStreamReader(primary.get().getInputStream())) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    BlockPos centerFromJson = null;
                    Double radiusFromJson = null;
                    Identifier templateFromJson = null;
                    if (obj != null) {
                        if (obj.has("podium_center")) {
                            try { String v = obj.get("podium_center").getAsString(); centerFromJson = parseCsvBlockPos(v); } catch (Throwable ignored) {}
                        }
                        if (obj.has("podium_radius")) {
                            try { radiusFromJson = obj.get("podium_radius").getAsDouble(); } catch (Throwable ignored) {}
                        }
                        if (radiusFromJson == null && obj.has("podium_diameter")) {
                            try { double diam = obj.get("podium_diameter").getAsDouble(); radiusFromJson = diam / 2.0; } catch (Throwable ignored) {}
                        }
                        if (obj.has("podium_template")) {
                            try { templateFromJson = Identifier.tryParse(obj.get("podium_template").getAsString()); } catch (Throwable ignored) {}
                        }
                    }

                    if (centerFromJson != null || radiusFromJson != null || templateFromJson != null) {
                        if (templateFromJson == null) templateFromJson = DEFAULT_PODIUM_TEMPLATE;
                        result = new Map(template, centerFromJson, radiusFromJson, templateFromJson);
                        LOGGER.info("Applied podium overrides from plasmid JSON: center={} radius={} template={} (from {})", centerFromJson, radiusFromJson, templateFromJson, resourceId);
                    }
                }
            } else {
                // Fallback: search all JSON files in data/<ns>/plasmid/game and choose the one with matching map_id
                var resources = server.getResourceManager().findResources("plasmid/game", id -> id.getPath().endsWith(".json"));
                for (var entry : resources.entrySet()) {
                    try (Reader r = new InputStreamReader(entry.getValue().getInputStream())) {
                        JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                        if (obj == null) continue;
                        // Match map_id key if present
                        String mapIdStr = obj.has("map_id") ? obj.get("map_id").getAsString() : null;
                        if (mapIdStr == null) continue;
                        if (!mapIdStr.equals(identifier.toString())) continue;

                        BlockPos centerFromJson = null;
                        Double radiusFromJson = null;
                        Identifier templateFromJson = null;
                        if (obj.has("podium_center")) {
                            try { String v = obj.get("podium_center").getAsString(); centerFromJson = parseCsvBlockPos(v); } catch (Throwable ignored) {}
                        }
                        if (obj.has("podium_radius")) {
                            try { radiusFromJson = obj.get("podium_radius").getAsDouble(); } catch (Throwable ignored) {}
                        }
                        if (radiusFromJson == null && obj.has("podium_diameter")) {
                            try { double diam = obj.get("podium_diameter").getAsDouble(); radiusFromJson = diam / 2.0; } catch (Throwable ignored) {}
                        }
                        if (obj.has("podium_template")) {
                            try { templateFromJson = Identifier.tryParse(obj.get("podium_template").getAsString()); } catch (Throwable ignored) {}
                        }

                        if (centerFromJson != null || radiusFromJson != null || templateFromJson != null) {
                            if (templateFromJson == null) templateFromJson = DEFAULT_PODIUM_TEMPLATE;
                            result = new Map(template, centerFromJson, radiusFromJson, templateFromJson);
                            LOGGER.info("Applied podium overrides from discovered JSON: center={} radius={} template={} (from {})", centerFromJson, radiusFromJson, templateFromJson, entry.getKey());
                            break;
                        }
                    } catch (Throwable ignored) {
                        // continue scanning other files
                    }
                }
            }
        } catch (Throwable ignored) {}

        return result;
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
     * Optional podium center as specified by the map template metadata.
     * @return optional block position to use as podium center
     */
    public Optional<BlockPos> getPodiumCenter() { return Optional.ofNullable(this.podiumCenter); }

    /**
     * Optional podium radius in blocks. Returns value parsed from companion JSON or template metadata if present.
     * Adds debug output to confirm value source.
     * @return optional podium radius
     */
    public Optional<Double> getPodiumRadius() {
        if (this.podiumRadius != null) {
            LOGGER.info("[Map] getPodiumRadius: value present: {}", this.podiumRadius);
        } else {
            LOGGER.info("[Map] getPodiumRadius: value absent, using default");
        }
        return Optional.ofNullable(this.podiumRadius);
    }

    /**
     * Optional podium template identifier. Returns value parsed from companion JSON or template metadata if present.
     * @return optional podium template identifier
     */
    public Optional<Identifier> getPodiumTemplateId() { return Optional.ofNullable(this.podiumTemplateId); }

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

    // --- helpers ---
    private static BlockPos parseCsvBlockPos(String csv) {
        if (csv == null) return null;
        String[] parts = csv.split(",");
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ex) {
            LOGGER.warn("Failed to parse podium_center metadata: {}", csv);
            return null;
        }
    }
}
