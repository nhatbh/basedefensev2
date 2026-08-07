package com.nhatbh.basedefensev2.level;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

public class MobLevelCalculator {
    private static final Random RANDOM = new Random();

    public static int calculateLevel(LivingEntity entity, ServerLevel level, BlockPos pos, int overrideLevel) {
        if (overrideLevel > 0) {
            return overrideLevel;
        }

        ResourceLocation dimId = level.dimension().location();
        MobLevelConfig.DimensionSetting dimSetting = MobLevelConfig.DIMENSION_SETTINGS.get(dimId.toString());

        if (dimSetting != null && dimSetting.fixedLevel > 0) {
            return dimSetting.fixedLevel;
        }

        boolean isOverworld = level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
        int overworldMaxCap = 0;
        double baseLevel = MobLevelConfig.STARTING_LEVEL;

        if (isOverworld) {
            WorldLevelSavedData worldData = WorldLevelSavedData.get(level);
            int worldLevel = worldData.getWorldLevel();
            baseLevel = MobLevelConfig.getOverworldBaseLevel(worldLevel);
            overworldMaxCap = MobLevelConfig.getMobLevelCap(worldLevel);
        }

        // Distance from spawn calculation
        BlockPos spawnPos = level.getSharedSpawnPos();
        double distSq = pos.distSqr(spawnPos);
        double dist = Math.sqrt(distSq);

        if (MobLevelConfig.LEVEL_INCREASE_PER_DISTANCE > 0) {
            if (MobLevelConfig.LEVEL_POWER_PER_DISTANCE > 0) {
                baseLevel += Math.pow(dist * MobLevelConfig.LEVEL_INCREASE_PER_DISTANCE, MobLevelConfig.LEVEL_POWER_PER_DISTANCE);
            } else {
                baseLevel += dist * MobLevelConfig.LEVEL_INCREASE_PER_DISTANCE;
            }
        }

        // Deepness below sea level calculation (Sea level = 63)
        int seaLevel = level.getSeaLevel();
        if (pos.getY() < seaLevel && MobLevelConfig.LEVEL_INCREASE_PER_DEEPNESS > 0) {
            double deepness = seaLevel - pos.getY();
            if (MobLevelConfig.LEVEL_POWER_PER_DEEPNESS > 0) {
                baseLevel += Math.pow(deepness * MobLevelConfig.LEVEL_INCREASE_PER_DEEPNESS, MobLevelConfig.LEVEL_POWER_PER_DEEPNESS);
            } else {
                baseLevel += deepness * MobLevelConfig.LEVEL_INCREASE_PER_DEEPNESS;
            }
        }

        // Day bonus
        if (MobLevelConfig.LEVEL_BONUS_PER_DAY > 0) {
            long dayCount = level.getDayTime() / 24000L;
            baseLevel += dayCount * MobLevelConfig.LEVEL_BONUS_PER_DAY;
        }

        // Random bonus
        if (MobLevelConfig.RANDOM_LEVEL_BONUS > 0) {
            baseLevel += RANDOM.nextInt(MobLevelConfig.RANDOM_LEVEL_BONUS + 1);
        }

        // Apply dimension multiplier and flat bonus
        if (dimSetting != null) {
            baseLevel *= dimSetting.multiplier;
            baseLevel += dimSetting.flatBonus;
        }

        int finalLevel = (int) Math.round(baseLevel);
        if (isOverworld && overworldMaxCap > 0) {
            finalLevel = Math.min(finalLevel, overworldMaxCap);
        }
        if (MobLevelConfig.MAXIMUM_LEVEL > 0) {
            finalLevel = Math.min(finalLevel, MobLevelConfig.MAXIMUM_LEVEL);
        }

        return Math.max(1, finalLevel);
    }
}
