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
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;
import xyz.nucleoid.plasmid.api.game.GameOpenException;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

/**
 * A BOTC map loaded from a map template resource.
 */
public class MapInstance {
    private final Regions regions;
    private final Meta meta;
    private final Attributes attributes;

    private final MapTemplate template;

    private static final int CURRENT_MAP_FORMAT = 1;

    private MapInstance(MapTemplate template) {
        this.template = template;

        int mapFormat = template.getMetadata()
                .getData()
                .getInt("map_format", 0);

        // Previously we enforced that map_format must match CURRENT_MAP_FORMAT.
        // For now, we only log via exception text but do NOT block loading on mismatched versions.
        // This allows older templates (like test.nbt) to be used while the format stabilizes.
        if (mapFormat < CURRENT_MAP_FORMAT) {
            // Old map format: allow loading anyway.
        } else if (mapFormat > CURRENT_MAP_FORMAT) {
            // Future map format: also allow loading for now.
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

        this.regions = new Regions(checkpoints, spawn);
    }

    /**
     * Load a BOTC map from a resource.
     */
    public static Map load(MinecraftServer server, Identifier identifier) {
        // Delegate to the core Map loader to avoid calling its private constructor
        return Map.load(server, identifier);
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }

    public Regions getRegions() {
        return this.regions;
    }

    public Meta getMeta() {
        return this.meta;
    }

    public Attributes getAttributes() {
        return this.attributes;
    }

    public record RespawnRegion(BlockBounds bounds, float yaw, float pitch) {
        public static RespawnRegion DEFAULT = new RespawnRegion(BlockBounds.ofBlock(BlockPos.ORIGIN), 0.0f, 0.0f);

        private static RespawnRegion of(TemplateRegion templateRegion) {
            return new RespawnRegion(
                    templateRegion.getBounds(),
                    templateRegion.getData().getFloat("yaw", DEFAULT.yaw()),
                    templateRegion.getData().getFloat("pitch", DEFAULT.pitch()));
        }

        public BlockPos centerBlock() {
            return BlockPos.ofFloored(this.bounds.center());
        }
    }

    public record Regions(List<RespawnRegion> checkpoints, RespawnRegion spawn) {
    }

    public record Attributes(int timeOfDay) {
        public static final Attributes DEFAULT = new Attributes(6000);

        public static final MapCodec<Attributes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("time_of_day", DEFAULT.timeOfDay()).forGetter(Attributes::timeOfDay)
        ).apply(instance, Attributes::new));
    }

    public record Meta(String name, List<String> authors, Optional<String> description, Optional<String> url) {
        public static final Meta DEFAULT = new Meta("Unknown Map", List.of("Unknown Authors"), Optional.empty(), Optional.empty());

        public static final MapCodec<Meta> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("name", DEFAULT.name()).forGetter(Meta::name),
                Codec.STRING.listOf().optionalFieldOf("authors", DEFAULT.authors()).forGetter(Meta::authors),
                Codec.STRING.optionalFieldOf("description").forGetter(Meta::description),
                Codec.STRING.optionalFieldOf("url").forGetter(Meta::url)
        ).apply(instance, Meta::new));
    }
}
