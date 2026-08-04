package com.nhatbh.basedefensev2.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when a LivingEntity's poise (strength) is depleted to 0 (Poise Broken).
 */
public class PoiseBrokenEvent extends Event {
    private final LivingEntity entity;

    public PoiseBrokenEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
