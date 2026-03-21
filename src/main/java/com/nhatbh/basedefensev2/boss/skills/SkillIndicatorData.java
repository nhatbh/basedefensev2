package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.world.phys.Vec3;

public class SkillIndicatorData {
    public int entityId;
    public String stepId;
    public int tickInStep;
    public int totalDuration;
    
    public ActiveSequence.CounterType counterType;
    public int counterWindowStart;
    public int counterWindowEnd;
    public Vec3 counterDirection;
    public ElementType magicElement;
    public boolean isParry;

    public SkillIndicatorData(int entityId, String stepId, int tickInStep, int totalDuration,
                              ActiveSequence.CounterType counterType, int counterWindowStart, int counterWindowEnd,
                              Vec3 counterDirection, ElementType magicElement, boolean isParry) {
        this.entityId = entityId;
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
}
