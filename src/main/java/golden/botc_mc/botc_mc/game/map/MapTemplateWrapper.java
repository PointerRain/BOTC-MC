package golden.botc_mc.botc_mc.game.map;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;
import xyz.nucleoid.plasmid.api.game.GameOpenException;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

/** Lightweight map template wrapper. Provides access to template metadata and regions. */
@SuppressWarnings("unused")
public class MapTemplateWrapper {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("botc.MapTemplate");
    private final Regions regions;
    private final Meta meta;
    private final Attributes attributes;

    private final xyz.nucleoid.map_templates.MapTemplate template;

    private static final int CURRENT_MAP_FORMAT = 1;

    private MapTemplateWrapper(xyz.nucleoid.map_templates.MapTemplate template) {
        this.template = template;

        int mapFormat = template.getMetadata()
                .getData()
                .getInt("track_format", 0);

        if (mapFormat < CURRENT_MAP_FORMAT) {
            throw new GameOpenException(Text.of("This map was built for an earlier version of the mod."));
        } else if (mapFormat > CURRENT_MAP_FORMAT) {
            throw new GameOpenException(Text.of("This map was built for a future version of the mod."));
        }

        this.meta = template.getMetadata()
                .getData()
                .get("meta", Meta.CODEC.codec())
                .orElse(Meta.DEFAULT);

        this.attributes = template.getMetadata()
                .getData()
                .get("attributes", Attributes.CODEC.codec())
                .orElse(Attributes.DEFAULT);

        List<RespawnRegion> checkpoints = template.getMetadata()
                .getRegions("checkpoint")
                .filter(cp -> cp.getData().getInt("index").isPresent())
                .sorted(Comparator.comparingInt(cp -> cp.getData().getInt("index").orElseThrow()))
                .map(RespawnRegion::of)
                .toList();

        if (checkpoints.isEmpty()) {
            checkpoints = List.of(RespawnRegion.DEFAULT);
        }

        RespawnRegion spawn = template.getMetadata()
                .getRegions("spawn")
                .map(RespawnRegion::of)
                .findFirst()
                .orElse(RespawnRegion.DEFAULT);

        List<RespawnRegion> gridBoxes = template.getMetadata()
                .getRegions("grid_box")
                .filter(gb -> gb.getData().getInt("index").isPresent())
                .sorted(Comparator.comparingInt(gb -> gb.getData().getInt("index").orElseThrow()))
                .map(RespawnRegion::of)
                .toList();

        RespawnRegion pitEntry = template.getMetadata()
                .getRegions("pit_entry")
                .map(RespawnRegion::of)
                .findFirst()
                .orElse(RespawnRegion.DEFAULT);

        RespawnRegion pitExit = template.getMetadata()
                .getRegions("pit_exit")
                .map(RespawnRegion::of)
                .findFirst()
                .orElse(RespawnRegion.DEFAULT);

        List<RespawnRegion> pitBoxes = template.getMetadata()
                .getRegions("pit_box")
                .filter(pb -> pb.getData().getInt("index").isPresent())
                .sorted(Comparator.comparingInt(pb -> pb.getData().getInt("index").orElseThrow()))
                .map(RespawnRegion::of)
                .toList();

        this.regions = new Regions(
                checkpoints,
                spawn,
                gridBoxes,
                pitEntry,
                pitExit,
                pitBoxes);
    }

    public static MapTemplateWrapper load(MinecraftServer server, Identifier identifier) {
        MapTemplate template;
        try {
            template = MapTemplateSerializer.loadFromResource(server, identifier);
            LOGGER.info("Loaded map template from {}", identifier);
        } catch (IOException first) {
            // Fallback path
            String path = identifier.getPath();
            if (!path.startsWith("map_template/")) {
                Identifier alt = Identifier.of(identifier.getNamespace(), "map_template/" + path);
                try {
                    template = MapTemplateSerializer.loadFromResource(server, alt);
                    LOGGER.info("Loaded map template from {}", alt);
                } catch (IOException second) {
                    throw new GameOpenException(Text.of("Couldn't load map " + identifier + " (tried " + alt + "): " + second.getMessage()));
                }
            } else {
                throw new GameOpenException(Text.of("Couldn't load map " + identifier));
            }
        }

        return new MapTemplateWrapper(template);
    }

    public static MapTemplateWrapper load(MinecraftServer server, String resourcePath) {
        java.util.LinkedHashSet<Identifier> attempts = new java.util.LinkedHashSet<>();

        // Candidate namespaces to try
        java.util.List<String> namespaces = new java.util.ArrayList<>();
        if (resourcePath.contains(":")) {
            var provided = Identifier.of(resourcePath);
            namespaces.add(provided.getNamespace());
        }
        namespaces.add("botc");
        namespaces.add("botc-mc");

        // Derive a canonical tail by removing any leading known prefixes
        String tail = resourcePath.contains(":") ? Identifier.of(resourcePath).getPath() : resourcePath;
        while (tail.startsWith("map_template/") || tail.startsWith("plasmid/")) {
            if (tail.startsWith("map_template/")) tail = tail.substring("map_template/".length());
            if (tail.startsWith("plasmid/")) tail = tail.substring("plasmid/".length());
        }

        // Prefix combos to try (ordered) — don't create duplicates
        String[] prefixes = new String[] {
                "",
                "map_template/",
                "plasmid/",
                "map_template/plasmid/",
                "plasmid/map_template/"
        };

        // Build identifiers to attempt
        for (String ns : namespaces) {
            for (String pref : prefixes) {
                String p = pref + tail;
                try { attempts.add(Identifier.of(ns, p)); } catch (Exception ignored) {}
            }
        }

        // Also try literal if resourcePath included a namespace originally
        if (resourcePath.contains(":")) {
            try { attempts.add(Identifier.of(resourcePath)); } catch (Exception ignored) {}
        }

        // Log attempts for debugging
        LOGGER.info("Attempting to load map resource '{}' with {} candidate identifiers", resourcePath, attempts.size());
        for (Identifier id : attempts) {
            LOGGER.info("  will try id: {}", id);
        }

        IOException lastEx = null;
        for (Identifier id : attempts) {
            try {
                MapTemplate template = MapTemplateSerializer.loadFromResource(server, id);
                LOGGER.info("Successfully loaded map template from {}", id);
                return new MapTemplateWrapper(template);
            } catch (IOException e) {
                lastEx = e;
                LOGGER.debug("Failed to load template from {}: {}", id, e.getMessage());
            }
        }

        throw new GameOpenException(Text.of("Couldn't load map resource " + resourcePath + " (attempted " + attempts.size() + " variants): " + (lastEx == null ? "unknown" : lastEx.getMessage())));
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }

    // Expose the underlying MapTemplate so callers can build template-backed maps
    public xyz.nucleoid.map_templates.MapTemplate getTemplate() { return this.template; }

    public Regions getRegions() { return this.regions; }
    public Meta getMeta() { return this.meta; }
    public Attributes getAttributes() { return this.attributes; }

    public record RespawnRegion(BlockBounds bounds, float yaw, float pitch) {
        public static RespawnRegion DEFAULT = new RespawnRegion(BlockBounds.ofBlock(BlockPos.ORIGIN), 0.0f, 0.0f);

        private static RespawnRegion of(TemplateRegion templateRegion) {
            return new RespawnRegion(
                    templateRegion.getBounds(),
                    templateRegion.getData().getFloat("yaw", DEFAULT.yaw()),
                    templateRegion.getData().getFloat("pitch", DEFAULT.pitch()));
        }
    }

    public record Regions(
            List<RespawnRegion> checkpoints,
            RespawnRegion spawn, List<RespawnRegion> gridBoxes,
            RespawnRegion pitEntry, RespawnRegion pitExit, List<RespawnRegion> pitBoxes) { }

    public record Attributes(
            int timeOfDay,
            Layout layout) {

        public static final Attributes DEFAULT = new Attributes(6000, Layout.CIRCULAR);

        public static final MapCodec<Attributes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("time_of_day", DEFAULT.timeOfDay()).forGetter(Attributes::timeOfDay),
                Layout.CODEC.optionalFieldOf("layout", DEFAULT.layout()).forGetter(Attributes::layout))
                .apply(instance, Attributes::new));
    }

    public record Meta(
            String name, List<String> authors, Optional<String> description, Optional<String> url) {

        public static final Meta DEFAULT = new Meta("Unknown Map", List.of("Unknown Authors"), Optional.empty(), Optional.empty());

        public static final MapCodec<Meta> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("name", DEFAULT.name()).forGetter(Meta::name),
                Codec.STRING.listOf().optionalFieldOf("authors", DEFAULT.authors()).forGetter(Meta::authors),
                Codec.STRING.optionalFieldOf("description").forGetter(Meta::description),
                Codec.STRING.optionalFieldOf("url").forGetter(Meta::url))
                .apply(instance, Meta::new));
    }

    public enum Layout {
        CIRCULAR("circular"), LINEAR("linear");
        private final String name;
        Layout(String name) { this.name = name; }
        public String asString() { return this.name; }
        public static final Codec<Layout> CODEC = Codec.STRING.xmap(Layout::fromString, Layout::asString);
        private static Layout fromString(String s) {
            for (Layout l : values()) if (l.name.equalsIgnoreCase(s)) return l;
            return CIRCULAR;
        }
    }
}
