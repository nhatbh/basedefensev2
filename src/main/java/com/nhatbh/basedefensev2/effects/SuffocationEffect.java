package com.nhatbh.basedefensev2.effects;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class SuffocationEffect extends MobEffect {

    public SuffocationEffect() {
        super(MobEffectCategory.HARMFUL, 0x5D4037); // Dark smoke brown/purple color
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;

        MobEffectInstance current = entity.getEffect(this);
        if (current == null) return;

        int duration = current.getDuration();

        // Level 10 (amplifier >= 9): Suffocating damage (5% max HP per second)
        if (amplifier >= 9) {
            if (duration % 20 == 0) {
                float damage = entity.getMaxHealth() * 0.05f;
                entity.hurt(entity.damageSources().inFire(), Math.max(1.0f, damage));
            }
        }

        // Downgrade decay when timer expires (1 tick remaining)
        if (duration == 1 && amplifier > 0) {
            entity.addEffect(new MobEffectInstance(this, 20, amplifier - 1, false, true, true));
        }
    }

    /**
     * Called every second (20 ticks) while inside the Air Burning cloud (<6m of boss when Rage >= 100).
     * Refreshes duration to 20 ticks (1 second) and increases amplifier by 1 (max amplifier 9 = Level 10).
     */
    public static void applyOrUpgrade(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        MobEffectInstance existing = entity.getEffect(ModEffects.SUFFOCATION.get());
        int newAmp = 0;
        if (existing != null) {
            newAmp = Math.min(9, existing.getAmplifier() + 1);
        }
        entity.addEffect(new MobEffectInstance(ModEffects.SUFFOCATION.get(), 20, newAmp, false, true, true));
    }
}
