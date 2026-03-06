package com.nhatbh.basedefensev2.strength;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

public class EntityEvents {
    /**
     * Fired when a LivingEntity's poise/strength is reduced to 0.
     */
    public static class PoiseBroken extends Event {
        private final LivingEntity entity;

        public PoiseBroken(LivingEntity entity) {
            this.entity = entity;
        }

        public LivingEntity getEntity() {
            return entity;
        }
    }
}
