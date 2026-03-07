package com.nhatbh.basedefensev2.stage.utils;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArenaBarrierManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final BlockPos ARENA_ELLIPSE_CENTER = new BlockPos(29, 52, 1);
    private static final float ARENA_ELLIPSE_RADIUS_X = 52.0f;
    private static final float ARENA_ELLIPSE_RADIUS_Z = 65.0f;

    public static boolean createArenaBarrier(@Nullable ServerLevel level) {
        if (level == null) {
            LOGGER.warn("Cannot create arena barrier: ServerLevel is null - skipping barrier creation");
            return false;
        }

        StageContext savedData = StageContext.getOrCreate(level);

        if (savedData.isArenaBarrierActive()) {
            BlockPos oldCenter = savedData.getArenaBarrierCenter();
            LOGGER.warn("Arena barrier already active at {} - removing old barrier before creating new one", oldCenter);
            removeArenaBarrier(level);
        }

        try {
            savedData.setArenaBarrierCenter(ARENA_ELLIPSE_CENTER);
            savedData.setArenaBarrierEllipse(ARENA_ELLIPSE_RADIUS_X, ARENA_ELLIPSE_RADIUS_Z);

            List<BlockPos> barrierBlocks = placeBarrierBlocks(level, ARENA_ELLIPSE_CENTER, ARENA_ELLIPSE_RADIUS_X,
                    ARENA_ELLIPSE_RADIUS_Z);
            savedData.setBarrierBlockPositions(barrierBlocks);

            LOGGER.info("Arena barrier ellipse created at {} with radii X={}, Z={}, placed {} barrier blocks",
                    ARENA_ELLIPSE_CENTER, ARENA_ELLIPSE_RADIUS_X, ARENA_ELLIPSE_RADIUS_Z, barrierBlocks.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("Exception creating arena barrier ellipse: {} - barrier creation failed",
                    e.getMessage(), e);
            savedData.clearArenaBarrier();
            return false;
        }
    }

    private static List<BlockPos> placeBarrierBlocks(ServerLevel level, BlockPos center, float radiusX, float radiusZ) {
        List<BlockPos> placedBlocks = new ArrayList<>();
        BlockState barrierState = Blocks.BARRIER.defaultBlockState();
        int buildHeight = level.getMaxBuildHeight();
        int minY = center.getY() - 10;
        int maxY = buildHeight - 1;
        int wallThickness = 5;

        int minX = center.getX() - (int) Math.ceil(radiusX + wallThickness);
        int maxX = center.getX() + (int) Math.ceil(radiusX + wallThickness);
        int minZ = center.getZ() - (int) Math.ceil(radiusZ + wallThickness);
        int maxZ = center.getZ() + (int) Math.ceil(radiusZ + wallThickness);

        float innerRadiusX = radiusX;
        float innerRadiusZ = radiusZ;
        float outerRadiusX = radiusX + wallThickness;
        float outerRadiusZ = radiusZ + wallThickness;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x - center.getX();
                    double dz = z - center.getZ();

                    double innerEllipseValue = (dx * dx) / (innerRadiusX * innerRadiusX)
                            + (dz * dz) / (innerRadiusZ * innerRadiusZ);
                    double outerEllipseValue = (dx * dx) / (outerRadiusX * outerRadiusX)
                            + (dz * dz) / (outerRadiusZ * outerRadiusZ);

                    if (innerEllipseValue > 1.0 && outerEllipseValue <= 1.0) {
                        BlockPos pos = new BlockPos(x, y, z);

                        BlockState currentState = level.getBlockState(pos);
                        if (currentState.isAir() || currentState.getFluidState().getType() == Fluids.WATER.getSource()
                                ||
                                currentState.getFluidState().getType() == Fluids.LAVA.getSource()) {
                            level.setBlock(pos, barrierState, 3);
                            placedBlocks.add(pos);
                        }
                    }
                }
            }
        }

        int topY = buildHeight - 1;
        int ceilingMinX = center.getX() - (int) Math.ceil(radiusX);
        int ceilingMaxX = center.getX() + (int) Math.ceil(radiusX);
        int ceilingMinZ = center.getZ() - (int) Math.ceil(radiusZ);
        int ceilingMaxZ = center.getZ() + (int) Math.ceil(radiusZ);

        for (int x = ceilingMinX; x <= ceilingMaxX; x++) {
            for (int z = ceilingMinZ; z <= ceilingMaxZ; z++) {
                double dx = x - center.getX();
                double dz = z - center.getZ();
                double ellipseValue = (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ);

                if (ellipseValue <= 1.0) {
                    BlockPos pos = new BlockPos(x, topY, z);
                    BlockState currentState = level.getBlockState(pos);
                    if (currentState.isAir() || currentState.getFluidState().getType() == Fluids.WATER.getSource() ||
                            currentState.getFluidState().getType() == Fluids.LAVA.getSource()) {
                        level.setBlock(pos, barrierState, 3);
                        placedBlocks.add(pos);
                    }
                }
            }
        }

        int floorTopY = 50;
        int floorBottomY = 47;
        BlockState bedrockState = Blocks.BEDROCK.defaultBlockState();
        int floorMinX = center.getX() - (int) Math.ceil(radiusX);
        int floorMaxX = center.getX() + (int) Math.ceil(radiusX);
        int floorMinZ = center.getZ() - (int) Math.ceil(radiusZ);
        int floorMaxZ = center.getZ() + (int) Math.ceil(radiusZ);

        for (int y = floorBottomY; y <= floorTopY; y++) {
            for (int x = floorMinX; x <= floorMaxX; x++) {
                for (int z = floorMinZ; z <= floorMaxZ; z++) {
                    double dx = x - center.getX();
                    double dz = z - center.getZ();
                    double ellipseValue = (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ);

                    if (ellipseValue <= 1.0) {
                        BlockPos pos = new BlockPos(x, y, z);
                        level.setBlock(pos, bedrockState, 3);
                        placedBlocks.add(pos);
                    }
                }
            }
        }

        return placedBlocks;
    }

    @Deprecated
    public static boolean createArenaBarrier(@Nullable ServerLevel level, @Nullable BlockPos center, float radius) {
        return createArenaBarrier(level);
    }

    public static boolean removeArenaBarrier(@Nullable ServerLevel level) {
        if (level == null) {
            LOGGER.warn("Cannot remove arena barrier: ServerLevel is null");
            return false;
        }

        StageContext savedData = StageContext.getOrCreate(level);

        if (!savedData.isArenaBarrierActive()) {
            LOGGER.debug("No arena barrier to remove");
            return false;
        }

        BlockPos oldCenter = savedData.getArenaBarrierCenter();
        float oldRadiusX = savedData.getArenaBarrierRadiusX();
        float oldRadiusZ = savedData.getArenaBarrierRadiusZ();

        List<BlockPos> barrierBlocks = savedData.getBarrierBlockPositions();
        if (barrierBlocks != null && !barrierBlocks.isEmpty()) {
            int removed = 0;
            for (BlockPos pos : barrierBlocks) {
                BlockState currentState = level.getBlockState(pos);
                if (currentState.is(Blocks.BARRIER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
            }
            LOGGER.info("Removed {} barrier blocks", removed);
        }

        savedData.clearArenaBarrier();
        LOGGER.info("Arena barrier removed (was at {} with ellipse radii X={} Z={})", oldCenter, oldRadiusX,
                oldRadiusZ);
        return true;
    }

    public static boolean isArenaBarrierActive(@Nullable ServerLevel level) {
        if (level == null) {
            return false;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        return savedData.isArenaBarrierActive();
    }

    @Nullable
    public static BlockPos getArenaBarrierCenter(@Nullable ServerLevel level) {
        if (level == null) {
            return null;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        return savedData.getArenaBarrierCenter();
    }

    @Deprecated
    public static float getArenaBarrierRadius(@Nullable ServerLevel level) {
        if (level == null) {
            return 0.0f;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        return savedData.getArenaBarrierRadius();
    }

    public static float getArenaBarrierRadiusX(@Nullable ServerLevel level) {
        if (level == null) {
            return 0.0f;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        return savedData.getArenaBarrierRadiusX();
    }

    public static float getArenaBarrierRadiusZ(@Nullable ServerLevel level) {
        if (level == null) {
            return 0.0f;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        return savedData.getArenaBarrierRadiusZ();
    }

    public static boolean isPositionWithinBarrier(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return false;
        return isPositionWithinBarrier(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public static boolean isPositionWithinBarrier(@Nullable ServerLevel level, double x, double y, double z) {
        if (level == null) {
            return false;
        }

        StageContext savedData = StageContext.getOrCreate(level);
        BlockPos center = savedData.getArenaBarrierCenter();
        float radiusX = savedData.getArenaBarrierRadiusX();
        float radiusZ = savedData.getArenaBarrierRadiusZ();

        if (center == null || radiusX <= 0 || radiusZ <= 0) {
            return false;
        }

        // Horizontal check (Ellipse)
        double dx = x - (center.getX() + 0.5);
        double dz = z - (center.getZ() + 0.5);
        double ellipseValue = (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ);
        if (ellipseValue > 1.0) {
            return false;
        }

        // Vertical check
        // Floor is at 50, Ceiling is at buildHeight - 1
        int minY = 51; 
        int maxY = level.getMaxBuildHeight() - 2;
        return y >= minY && y <= maxY;
    }

    /**
     * Calculates the closest point inside the barrier for a given position.
     * If the position is outside the ellipse, it projects it to the boundary.
     * If it's outside the Y range, it clamps the Y.
     */
    public static Vec3 getClosestPointInside(@Nullable ServerLevel level, Vec3 pos) {
        if (level == null) return pos;

        StageContext savedData = StageContext.getOrCreate(level);
        BlockPos centerPos = savedData.getArenaBarrierCenter();
        float radiusX = savedData.getArenaBarrierRadiusX();
        float radiusZ = savedData.getArenaBarrierRadiusZ();

        if (centerPos == null || radiusX <= 0 || radiusZ <= 0) {
            return pos;
        }

        double centerX = centerPos.getX() + 0.5;
        double centerZ = centerPos.getZ() + 0.5;
        double dx = pos.x - centerX;
        double dz = pos.z - centerZ;

        double x = pos.x;
        double z = pos.z;

        // Project onto ellipse if outside
        double ellipseValue = (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ);
        if (ellipseValue > 1.0) {
            double k = 1.0 / Math.sqrt(ellipseValue);
            // Push slightly inside to avoid floating point issues and immediate re-triggering
            double margin = 0.98;
            x = centerX + dx * k * margin;
            z = centerZ + dz * k * margin;
        }

        // Clamp Y
        int minY = 51;
        int maxY = level.getMaxBuildHeight() - 2;
        double y = Math.max(minY, Math.min(maxY, pos.y));

        return new Vec3(x, y, z);
    }

    public static double getDistanceSquaredToBarrierCenter(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return Double.MAX_VALUE;
        }

        StageContext savedData = StageContext.getOrCreate(level);
        BlockPos center = savedData.getArenaBarrierCenter();

        if (center == null) {
            return Double.MAX_VALUE;
        }

        return pos.distSqr(center);
    }

    public static void setActiveBossEntityUuid(@Nullable ServerLevel level, @Nullable UUID bossEntityUuid) {
        if (level == null) {
            LOGGER.warn("Cannot set active boss entity UUID: ServerLevel is null");
            return;
        }

        StageContext savedData = StageContext.getOrCreate(level);
        long spawnTime = bossEntityUuid != null ? level.getGameTime() : -1;
        savedData.setActiveBossUuid(bossEntityUuid, spawnTime);

        if (bossEntityUuid != null) {
            LOGGER.info("Set active boss entity UUID: {} at game time {}", bossEntityUuid, spawnTime);
        } else {
            LOGGER.info("Cleared active boss entity UUID");
        }
    }

    @Nullable
    public static UUID getActiveBossEntityUuid(@Nullable ServerLevel level) {
        if (level == null) {
            LOGGER.warn("getActiveBossEntityUuid called with null level");
            return null;
        }
        StageContext savedData = StageContext.getOrCreate(level);
        UUID bossUuid = savedData.getActiveBossUuid();
        LOGGER.info("Retrieved active boss UUID: {}", bossUuid != null ? bossUuid : "NULL");
        return bossUuid;
    }

    @Nullable
    public static String getActiveBossEntityId(@Nullable ServerLevel level) {
        UUID uuid = getActiveBossEntityUuid(level);
        return uuid != null ? uuid.toString() : null;
    }

    public static void renderBarrierParticles(@Nullable ServerLevel level) {
        if (level == null) {
            return;
        }

        StageContext savedData = StageContext.getOrCreate(level);
        BlockPos center = savedData.getArenaBarrierCenter();
        float radiusX = savedData.getArenaBarrierRadiusX();
        float radiusZ = savedData.getArenaBarrierRadiusZ();

        if (center == null || radiusX <= 0 || radiusZ <= 0) {
            return;
        }

        ParticleOptions particleType = ParticleTypes.END_ROD;

        int baseY = center.getY();
        int maxY = baseY + 20;
        int yStep = 3;

        float avgRadius = (radiusX + radiusZ) / 2.0f;
        int particlesPerLevel = Math.max(32, (int) (avgRadius * 1.5));
        particlesPerLevel = Math.min(particlesPerLevel, 80);

        for (int y = baseY; y <= maxY; y += yStep) {
            for (int i = 0; i < particlesPerLevel; i++) {
                double angle = (2.0 * Math.PI * i) / particlesPerLevel;
                double offsetX = Math.cos(angle) * radiusX;
                double offsetZ = Math.sin(angle) * radiusZ;

                level.sendParticles(
                        particleType,
                        center.getX() + 0.5 + offsetX,
                        y + 0.5,
                        center.getZ() + 0.5 + offsetZ,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0);
            }
        }
    }

    @Nullable
    private static ParticleOptions parseParticleType(String particleId) {
        if (particleId == null || particleId.isEmpty()) {
            return null;
        }

        try {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(particleId);
            if (resourceLocation == null) {
                return null;
            }

            var particleType = BuiltInRegistries.PARTICLE_TYPE.get(resourceLocation);
            if (particleType == null) {
                return null;
            }

            if (particleType instanceof ParticleOptions particleOptions) {
                return particleOptions;
            }
        } catch (Exception e) {
            // Ignore parsing errors, use default
        }

        return null;
    }
}
