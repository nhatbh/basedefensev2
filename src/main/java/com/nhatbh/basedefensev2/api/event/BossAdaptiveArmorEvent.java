package com.nhatbh.basedefensev2.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import javax.annotation.Nullable;

/**
 * Events related to the Boss Bullet Adaptive Armor System.
 */
public class BossAdaptiveArmorEvent extends Event {

    private final LivingEntity boss;

    public BossAdaptiveArmorEvent(LivingEntity boss) {
        this.boss = boss;
    }

    public LivingEntity getBoss() {
        return boss;
    }

    /**
     * Fired when adaptive reduction is being calculated for vitality damage on a boss.
     * This event is {@link Cancelable}. If canceled, no adaptive reduction is applied (multiplier set to 1.0).
     */
    @Cancelable
    public static class Calculate extends BossAdaptiveArmorEvent {
        private final String ammoType;
        private final float rawVitalityDamage;
        private float reductionMultiplier;

        public Calculate(LivingEntity boss, @Nullable String ammoType, float rawVitalityDamage, float reductionMultiplier) {
            super(boss);
            this.ammoType = ammoType;
            this.rawVitalityDamage = rawVitalityDamage;
            this.reductionMultiplier = reductionMultiplier;
        }

        @Nullable
        public String getAmmoType() {
            return ammoType;
        }

        public float getRawVitalityDamage() {
            return rawVitalityDamage;
        }

        public float getReductionMultiplier() {
            return reductionMultiplier;
        }

        public void setReductionMultiplier(float reductionMultiplier) {
            this.reductionMultiplier = Math.max(0.05f, Math.min(1.0f, reductionMultiplier));
        }

        public float getFinalVitalityDamage() {
            return rawVitalityDamage * reductionMultiplier;
        }
    }

    /**
     * Fired when a boss's adaptive armor resets upon exhaustion recovery.
     */
    public static class Reset extends BossAdaptiveArmorEvent {
        public Reset(LivingEntity boss) {
            super(boss);
        }
    }
}
