package golden.botc_mc.botc_mc.game.map;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utility that generates simple podiums arranged in a circle.
 * <p>
 * This class provides helpers used by the '/botc podium' command. It places
 * single-block podiums or template-defined structures on a circle centered at the
 * requested coordinates. Generation will abort if any target block positions
 * are already occupied (i.e. not air) to avoid accidental block replacement.
 */
public final class PodiumManager {
    private PodiumManager() {}

    private static final Logger LOGGER = LogManager.getLogger("botc.PodiumManager");

    // Recorded last generation so only one set exists at a time.
    private static ServerWorld recordedWorld = null;
    private static List<PlacedPodium> recordedPositions = new ArrayList<>();

    private record PlacedPodium(BlockPos origin, Identifier templateId) {}

    private static final BlockState FALLBACK_BLOCK = Blocks.DIAMOND_BLOCK.getDefaultState();

    private static record PlacementContext(StructureTemplate template, StructurePlacementData data, Vec3i size) {}

    private static PlacementContext prepareTemplate(ServerWorld world, Identifier templateId) {
        if (templateId == null) return null;
        StructureTemplateManager manager = world.getServer().getStructureTemplateManager();
        StructureTemplate template;

        // First try: use StructureTemplateManager (standard path)
        try {
            template = manager.getTemplate(templateId).orElse(null);
        } catch (Exception ex) {
            LOGGER.error("Template lookup threw exception for {}: {}", templateId, ex.toString());
            template = null;
        }

        // Second try: if manager failed, load NBT directly from resource manager
        if (template == null) {
            LOGGER.info("Template manager returned null for {}. Attempting direct NBT load from resource pack.", templateId);
            template = loadTemplateFromResource(world, templateId);
        }

        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            LOGGER.warn("Template {} missing or empty. size={} availableNamespaces={}", templateId, template == null ? "null" : template.getSize(), world.getServer().getResourceManager().getAllNamespaces());
            world.getServer().getResourceManager().findResources("structures", id -> id.getPath().contains("podium")).forEach((id, res) -> LOGGER.info("Found related structure candidate: {}", id));
            return null;
        }
        StructurePlacementData data = new StructurePlacementData().setMirror(BlockMirror.NONE).setRotation(BlockRotation.NONE);
        LOGGER.info("Template {} loaded successfully with size {}", templateId, template.getSize());
        return new PlacementContext(template, data, template.getSize());
    }

    /**
     * Load a structure template directly from the resource manager by reading NBT.
     * This is a fallback for when StructureTemplateManager doesn't recognize the structure,
     * typically for structure-block-exported NBT files in mod datapacks.
     */
    private static StructureTemplate loadTemplateFromResource(ServerWorld world, Identifier templateId) {
        try {
            // Build resource path: data/<namespace>/structures/<path>.nbt
            Identifier resourceId = Identifier.of(templateId.getNamespace(), "structures/" + templateId.getPath() + ".nbt");
            LOGGER.info("Attempting to load structure NBT from resource: {}", resourceId);

            Optional<Resource> resourceOpt = world.getServer().getResourceManager().getResource(resourceId);
            if (resourceOpt.isEmpty()) {
                LOGGER.warn("Resource not found at: {}", resourceId);
                return null;
            }

            try (InputStream stream = resourceOpt.get().getInputStream()) {
                NbtCompound nbt = NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes());
                StructureTemplate template = new StructureTemplate();
                var blockLookup = world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
                template.readNbt(blockLookup, nbt);
                LOGGER.info("Successfully loaded structure {} from NBT resource with size {}", templateId, template.getSize());
                return template;
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load structure {} from resource NBT: {}", templateId, ex.toString(), ex);
            return null;
        }
    }

    private static boolean canPlaceTemplate(ServerWorld world, BlockPos origin, PlacementContext ctx) {
        if (ctx == null) return false;
        Vec3i size = ctx.size();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    mutable.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!world.getBlockState(mutable).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void placeTemplate(ServerWorld world, BlockPos origin, PlacementContext ctx) {
        ctx.template().place(world, origin, origin, ctx.data(), world.getRandom(), 2);
    }

    private static void clearTemplate(ServerWorld world, BlockPos origin, PlacementContext ctx) {
        Vec3i size = ctx.size();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos target = origin.add(x, y, z);
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    private static List<BlockPos> generateCircle(ServerWorld world, int centerX, int centerY, int centerZ, double radius, int count, PlacementMode mode, PlacementContext ctx) {
        List<BlockPos> placed = new ArrayList<>();
        if (world == null || count <= 0) return placed;

        int minY = world.getBottomY();
        int maxY = minY + world.getDimension().height() - 1;
        int y = Math.max(minY, Math.min(maxY, centerY));

        List<BlockPos> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * ((double) i / (double) count);
            double fx = centerX + Math.cos(angle) * radius;
            double fz = centerZ + Math.sin(angle) * radius;

            int bx = (int) Math.round(fx);
            int bz = (int) Math.round(fz);

            BlockPos pos = new BlockPos(bx, y, bz);
            targets.add(pos);
        }

        // Pre-flight check for template placement: ensure target area empty
        if (mode == PlacementMode.TEMPLATE && ctx != null) {
            for (BlockPos t : targets) {
                if (!canPlaceTemplate(world, t, ctx)) {
                    return Collections.emptyList();
                }
            }
        } else {
            for (BlockPos t : targets) {
                try {
                    if (!world.getBlockState(t).isAir()) {
                        return Collections.emptyList();
                    }
                } catch (Throwable ignored) {
                    return Collections.emptyList();
                }
            }
        }

        for (BlockPos pos : targets) {
            try {
                if (mode == PlacementMode.TEMPLATE && ctx != null) {
                    placeTemplate(world, pos, ctx);
                } else {
                    world.setBlockState(pos, FALLBACK_BLOCK, 3);
                }
                placed.add(pos);
            } catch (Throwable t) {
                // ignore and continue
            }
        }

        return placed;
    }

    public enum PlacementMode {
        BLOCK,
        TEMPLATE
    }

    public static synchronized List<BlockPos> createAndRecord(ServerWorld world, int centerX, int centerY, int centerZ, double radius, int count, PlacementMode mode, Identifier templateId) {
        removeRecorded();

        PlacementMode finalMode = mode;
        PlacementContext ctx = null;
        if (mode == PlacementMode.TEMPLATE && templateId != null) {
            ctx = prepareTemplate(world, templateId);
            if (ctx == null) {
                world.getPlayers().forEach(p -> p.sendMessage(Text.literal("Unable to find podium template: " + templateId + ". Using fallback block."), false));
                LOGGER.warn("Falling back to blocks for template {}", templateId);
                finalMode = PlacementMode.BLOCK;
            }
        }

        List<BlockPos> placed = generateCircle(world, centerX, centerY, centerZ, radius, count, finalMode, ctx);
        if (placed.isEmpty()) {
            if (finalMode == PlacementMode.TEMPLATE && ctx != null) {
                LOGGER.warn("Template placement aborted because target area was blocked for template {}", templateId);
                world.getPlayers().forEach(p -> p.sendMessage(Text.literal("Podium template placement aborted (area not empty)."), false));
            }
            return Collections.emptyList();
        }

        recordedWorld = world;
        recordedPositions = new ArrayList<>();
        for (BlockPos pos : placed) {
            recordedPositions.add(new PlacedPodium(pos, finalMode == PlacementMode.TEMPLATE ? templateId : null));
        }
        return new ArrayList<>(placed);
    }

    public static synchronized List<BlockPos> createForMap(Map map, ServerWorld world, int fallbackX, int fallbackY, int fallbackZ, double defaultRadius, int count) {
        double effectiveRadius = defaultRadius;
        String radiusSource = "default value";
        if (map != null) {
            Optional<Double> rOpt = map.getPodiumRadius();
            if (rOpt.isPresent()) {
                double rad = rOpt.get();
                if (rad > 0) {
                    effectiveRadius = rad;
                    radiusSource = "map JSON value";
                }
            }
        }
        System.out.println("[PodiumManager] Podium radius used: " + effectiveRadius);
        System.out.println("[PodiumManager] Podium radius source: "+radiusSource);

        PlacementMode mode = PlacementMode.BLOCK;
        Identifier templateId = null;
        if (map != null) {
            Optional<Identifier> templateOpt = map.getPodiumTemplateId();
            if (templateOpt.isPresent()) {
                mode = PlacementMode.TEMPLATE;
                templateId = templateOpt.get();
                System.out.println("[PodiumManager] Podium template used: " + templateId);
            }
        }

        Optional<BlockPos> opt = map != null ? map.getPodiumCenter() : Optional.empty();
        BlockPos center = opt.orElse(new BlockPos(fallbackX, fallbackY, fallbackZ));
        return createAndRecord(world, center.getX(), center.getY(), center.getZ(), effectiveRadius, count, mode, templateId);
    }

    public static synchronized List<BlockPos> removeRecorded() {
        List<BlockPos> removed = new ArrayList<>();
        if (recordedWorld == null || recordedPositions == null || recordedPositions.isEmpty()) {
            recordedWorld = null;
            recordedPositions = new ArrayList<>();
            return removed;
        }

        ServerWorld w = recordedWorld;
        for (PlacedPodium podium : recordedPositions) {
            if (podium.templateId == null) {
                try {
                    if (w.getBlockState(podium.origin).getBlock() == FALLBACK_BLOCK.getBlock()) {
                        w.setBlockState(podium.origin, Blocks.AIR.getDefaultState(), 3);
                        removed.add(podium.origin);
                    }
                } catch (Throwable ignored) {}
            } else {
                try {
                    PlacementContext ctx = prepareTemplate(w, podium.templateId);
                    if (ctx != null) {
                        LOGGER.info("Clearing previously placed template {} at {}", podium.templateId, podium.origin);
                        clearTemplate(w, podium.origin, ctx);
                        removed.add(podium.origin);
                    }
                } catch (Throwable ignored) {}
            }
        }

        recordedWorld = null;
        recordedPositions = new ArrayList<>();
        LOGGER.info("Removed {} podium entries", removed.size());
        return removed;
    }
}
