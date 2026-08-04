package com.nhatbh.basedefensev2.strength;

import com.nhatbh.basedefensev2.api.event.PoiseBrokenEvent;
import net.minecraft.world.entity.LivingEntity;

public class EntityEvents {
    /**
     * Fired when a LivingEntity's poise/strength is reduced to 0.
     * @deprecated Use {@link PoiseBrokenEvent} instead.
     */
    @Deprecated
    public static class PoiseBroken extends PoiseBrokenEvent {
        public PoiseBroken(LivingEntity entity) {
            super(entity);
        }
    }
}

