package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class BossSkillData {
    public static final String NBT_KEY = "BossSkillData";

    public String stepId;
    public int tickInStep;
    public int totalDuration;
    public ActiveSequence.CounterType counterType;
    public int counterWindowStart;
    public int counterWindowEnd;
    public Vec3 counterDirection;
    public ElementType magicElement;
    public boolean isParry;

    public BossSkillData(String stepId, int tickInStep, int totalDuration,
                         ActiveSequence.CounterType counterType, int counterWindowStart, int counterWindowEnd,
                         Vec3 counterDirection, ElementType magicElement, boolean isParry) {
        this.stepId = stepId;
        this.tickInStep = tickInStep;
        this.totalDuration = totalDuration;
        this.counterType = counterType;
        this.counterWindowStart = counterWindowStart;
        this.counterWindowEnd = counterWindowEnd;
        this.counterDirection = counterDirection;
        this.magicElement = magicElement;
        this.isParry = isParry;
    }

    public static BossSkillData get(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        if (!persistentData.contains(NBT_KEY)) {
            return null;
        }
        CompoundTag tag = persistentData.getCompound(NBT_KEY);
        
        ActiveSequence.CounterType type = ActiveSequence.CounterType.valueOf(tag.getString("CounterType"));
        ElementType element = tag.contains("MagicElement") ? ElementType.valueOf(tag.getString("MagicElement")) : null;
        Vec3 dir = tag.contains("DirX") ? new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ")) : null;

        return new BossSkillData(
            tag.getString("StepId"),
            tag.getInt("TickInStep"),
            tag.getInt("TotalDuration"),
            type,
            tag.getInt("CounterWindowStart"),
            tag.getInt("CounterWindowEnd"),
            dir,
            element,
            tag.getBoolean("IsParry")
        );
    }

    public void save(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        CompoundTag tag = new CompoundTag();
        tag.putString("StepId", stepId);
        tag.putInt("TickInStep", tickInStep);
        tag.putInt("TotalDuration", totalDuration);
        tag.putString("CounterType", counterType.name());
        tag.putInt("CounterWindowStart", counterWindowStart);
        tag.putInt("CounterWindowEnd", counterWindowEnd);
        if (counterDirection != null) {
            tag.putDouble("DirX", counterDirection.x);
            tag.putDouble("DirY", counterDirection.y);
            tag.putDouble("DirZ", counterDirection.z);
        }
        if (magicElement != null) {
            tag.putString("MagicElement", magicElement.name());
        }
        tag.putBoolean("IsParry", isParry);
        persistentData.put(NBT_KEY, tag);
    }

    public static void clear(LivingEntity entity) {
        entity.getPersistentData().remove(NBT_KEY);
    }
}
