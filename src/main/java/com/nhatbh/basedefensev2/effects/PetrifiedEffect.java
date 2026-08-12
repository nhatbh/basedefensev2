package com.nhatbh.basedefensev2.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class PetrifiedEffect extends MobEffect {

    public PetrifiedEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A4A4A); // Dark stone color
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Enforce movement freeze (slowness & jump lock) - NO BLINDNESS
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false, false));

        // Freeze horizontal movement & upward jump velocity
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(0, Math.min(0, motion.y), 0);
        entity.hurtMarked = true;
    }
}
