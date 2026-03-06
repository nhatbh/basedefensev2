package com.nhatbh.basedefensev2.strength;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public class EntityStrengthData {
    public static final String NBT_KEY = "EntityStrengthData";

    public float maxStrength;
    public float currentStrength;
    public float reductionValue;
    public boolean isPercentageBased;
    public int recoveryTicks;

    public EntityStrengthData(float maxStrength, float currentStrength, float reductionValue, boolean isPercentageBased, int recoveryTicks) {
        this.maxStrength = maxStrength;
        this.currentStrength = currentStrength;
        this.reductionValue = reductionValue;
        this.isPercentageBased = isPercentageBased;
        this.recoveryTicks = recoveryTicks;
    }

    public static EntityStrengthData get(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        if (!persistentData.contains(NBT_KEY)) {
            return null; // Not initialized
        }
        CompoundTag tag = persistentData.getCompound(NBT_KEY);
        return new EntityStrengthData(
            tag.getFloat("MaxStrength"),
            tag.getFloat("CurrentStrength"),
            tag.getFloat("ReductionValue"),
            tag.getBoolean("IsPercentageBased"),
            tag.getInt("RecoveryTicks")
        );
    }

    public static void set(LivingEntity entity, EntityStrengthData data) {
        CompoundTag persistentData = entity.getPersistentData();
        CompoundTag tag = new CompoundTag();
        tag.putFloat("MaxStrength", data.maxStrength);
        tag.putFloat("CurrentStrength", data.currentStrength);
        tag.putFloat("ReductionValue", data.reductionValue);
        tag.putBoolean("IsPercentageBased", data.isPercentageBased);
        tag.putInt("RecoveryTicks", data.recoveryTicks);
        persistentData.put(NBT_KEY, tag);
    }

    public void save(LivingEntity entity) {
        set(entity, this);
    }
}
