package com.nhatbh.basedefensev2.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when a LivingEntity's poise finishes its recovery cooldown and is restored to full.
 */
public class PoiseRecoveryEvent extends Event {
    private final LivingEntity entity;

    public PoiseRecoveryEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
