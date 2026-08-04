package com.nhatbh.basedefensev2.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public class MobLevelData {
    public static final String NBT_KEY_LEVEL = "MobLevel";

    public static int getLevel(LivingEntity entity) {
        if (entity == null) return MobLevelConfig.STARTING_LEVEL;
        CompoundTag data = entity.getPersistentData();
        if (data.contains(NBT_KEY_LEVEL)) {
            return Math.max(1, data.getInt(NBT_KEY_LEVEL));
        }
        return MobLevelConfig.STARTING_LEVEL;
    }

    public static void setLevel(LivingEntity entity, int level) {
        if (entity == null) return;
        int finalLevel = Math.max(1, level);
        if (MobLevelConfig.MAXIMUM_LEVEL > 0) {
            finalLevel = Math.min(finalLevel, MobLevelConfig.MAXIMUM_LEVEL);
        }
        entity.getPersistentData().putInt(NBT_KEY_LEVEL, finalLevel);
    }
}
