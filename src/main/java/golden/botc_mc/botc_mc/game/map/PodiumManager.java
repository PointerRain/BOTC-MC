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

    private record PlacedPodium(BlockPos origin, Identifier templateId, BlockRotation rotation) {}

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

    private static BlockRotation rotationTowards(BlockPos center, BlockPos pos) {
        int dx = center.getX() - pos.getX();
        int dz = center.getZ() - pos.getZ();
        // Choose the dominant axis for facing to avoid diagonal ambiguity
        if (Math.abs(dx) >= Math.abs(dz)) {
            if (dx > 0) return BlockRotation.CLOCKWISE_90;      // face east
            else if (dx < 0) return BlockRotation.COUNTERCLOCKWISE_90; // face west
            else return BlockRotation.NONE; // center aligned horizontally; default north/south by dz below
        } else {
            if (dz < 0) return BlockRotation.NONE;             // center north of pos (negative Z) => face north
            else if (dz > 0) return BlockRotation.CLOCKWISE_180; // center south of pos (positive Z) => face south
            else return BlockRotation.NONE;
        }
    }

    private static void placeTemplate(ServerWorld world, BlockPos origin, PlacementContext ctx, BlockRotation rotation, BlockPos templateOffset) {
        // Apply the template offset (rotated appropriately) to align the visual center with the target position
        BlockPos rotatedOffset = rotateOffset(templateOffset, rotation);
        BlockPos adjustedOrigin = origin.subtract(rotatedOffset);

        StructurePlacementData data = new StructurePlacementData().setMirror(BlockMirror.NONE).setRotation(rotation);
        ctx.template().place(world, adjustedOrigin, adjustedOrigin, data, world.getRandom(), 2);
    }

    private static BlockPos rotateOffset(BlockPos offset, BlockRotation rotation) {
        // Rotate the offset vector to match the template rotation
        int x = offset.getX();
        int z = offset.getZ();
        return switch (rotation) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new BlockPos(-z, offset.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(-x, offset.getY(), -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, offset.getY(), -x);
        };
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

    private static void clearVolumeCentered(ServerWorld world, BlockPos origin, Vec3i size, int pad) {
        int sx = size.getX() + pad * 2;
        int sy = size.getY() + pad; // usually templates are floor-up; minimal vertical pad
        int sz = size.getZ() + pad * 2;
        int hx = sx / 2;
        int hz = sz / 2;
        int minX = origin.getX() - hx;
        int minY = origin.getY();
        int minZ = origin.getZ() - hz;
        int maxX = minX + sx - 1;
        int maxY = minY + sy - 1;
        int maxZ = minZ + sz - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    try { world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static Vec3i rotatedSize(Vec3i size, BlockRotation rotation) {
        // Structure placement rotates around origin; footprint swaps X/Z on 90/270
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            case CLOCKWISE_180, NONE -> size;
        };
    }

    private static List<BlockPos> generateCircle(ServerWorld world, int centerX, int centerY, int centerZ, double radius, int count, PlacementMode mode, PlacementContext ctx, BlockPos templateOffset) {
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


        // Hard clear pass: ensure target areas are completely empty before placement
        if (mode == PlacementMode.TEMPLATE && ctx != null) {
            // Use center-based volume clear to handle templates with negative offsets relative to origin
            for (BlockPos t : targets) {
                BlockRotation rot = rotationTowards(new BlockPos(centerX, y, centerZ), t);
                Vec3i rsize = rotatedSize(ctx.size(), rot);
                clearVolumeCentered(world, t, rsize, 2);
            }
        } else {
            for (BlockPos t : targets) { try { world.setBlockState(t, Blocks.AIR.getDefaultState(), 3); } catch (Throwable ignored) {} }
        }

        for (BlockPos pos : targets) {
            try {
                if (mode == PlacementMode.TEMPLATE && ctx != null) {
                    BlockRotation rot = rotationTowards(new BlockPos(centerX, y, centerZ), pos);
                    placeTemplate(world, pos, ctx, rot, templateOffset);
                    placed.add(pos);
                    // record with rotation
                } else {
                    world.setBlockState(pos, FALLBACK_BLOCK, 3);
                    placed.add(pos);
                }
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
        return createAndRecordWithOffset(world, centerX, centerY, centerZ, radius, count, mode, templateId, BlockPos.ORIGIN);
    }

    public static synchronized List<BlockPos> createAndRecordWithOffset(ServerWorld world, int centerX, int centerY, int centerZ, double radius, int count, PlacementMode mode, Identifier templateId, BlockPos templateOffset) {
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

        // Compute target positions on the circle (no offset applied to circle positions)
        List<BlockPos> targets = new ArrayList<>();
        int minY = world.getBottomY();
        int maxY = minY + world.getDimension().height() - 1;
        int y = Math.max(minY, Math.min(maxY, centerY));
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * ((double) i / (double) count);
            int bx = (int) Math.round(centerX + Math.cos(angle) * radius);
            int bz = (int) Math.round(centerZ + Math.sin(angle) * radius);
            targets.add(new BlockPos(bx, y, bz));
        }

        List<BlockPos> placed = new ArrayList<>();
        recordedWorld = world;
        recordedPositions = new ArrayList<>();
        for (BlockPos pos : targets) {
            try {
                if (finalMode == PlacementMode.TEMPLATE && ctx != null) {
                    BlockRotation rot = rotationTowards(new BlockPos(centerX, y, centerZ), pos);

                    // Calculate adjusted origin using rotated template offset
                    BlockPos rotatedOffset = rotateOffset(templateOffset, rot);
                    BlockPos adjustedOrigin = pos.subtract(rotatedOffset);

                    // Place template with the offset applied (no pre-clearing - let template placement handle it)
                    placeTemplate(world, pos, ctx, rot, templateOffset);

                    // Record the adjusted origin for removal
                    recordedPositions.add(new PlacedPodium(adjustedOrigin, templateId, rot));
                } else {
                    world.setBlockState(pos, FALLBACK_BLOCK, 3);
                    recordedPositions.add(new PlacedPodium(pos, null, BlockRotation.NONE));
                }
                placed.add(pos);
            } catch (Throwable ignored) {}
        }
        return placed;
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

        BlockPos templateOffset = map != null ? map.getPodiumTemplateOffset().orElse(BlockPos.ORIGIN) : BlockPos.ORIGIN;
        System.out.println("[PodiumManager] Template offset used: " + templateOffset);

        Optional<BlockPos> opt = map != null ? map.getPodiumCenter() : Optional.empty();
        BlockPos center = opt.orElse(new BlockPos(fallbackX, fallbackY, fallbackZ));
        return createAndRecordWithOffset(world, center.getX(), center.getY(), center.getZ(), effectiveRadius, count, mode, templateId, templateOffset);
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
                    w.setBlockState(podium.origin, Blocks.AIR.getDefaultState(), 3);
                    removed.add(podium.origin);
                } catch (Throwable ignored) {}
            } else {
                try {
                    PlacementContext ctx = prepareTemplate(w, podium.templateId);
                    if (ctx != null) {
                        LOGGER.info("Clearing previously placed template {} at {}", podium.templateId, podium.origin);
                        // Clear the exact footprint from the adjusted origin
                        Vec3i size = ctx.size();
                        Vec3i rsize = rotatedSize(size, podium.rotation);
                        // Add padding to catch edge blocks and rotated bounds
                        int padX = 2, padY = 1, padZ = 2;
                        for (int x = -padX; x < rsize.getX() + padX; x++) {
                            for (int y = 0; y < rsize.getY() + padY; y++) {
                                for (int z = -padZ; z < rsize.getZ() + padZ; z++) {
                                    BlockPos clearPos = podium.origin.add(x, y, z);
                                    try {
                                        w.setBlockState(clearPos, Blocks.AIR.getDefaultState(), 3);
                                    } catch (Throwable ignored2) {}
                                }
                            }
                        }
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
