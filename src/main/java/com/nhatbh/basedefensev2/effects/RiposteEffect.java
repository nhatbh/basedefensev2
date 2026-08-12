package com.nhatbh.basedefensev2.effects;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Beneficial combat effect awarded to players upon a successful parry against a boss.
 * Increases melee poise damage dealt by 2x on 1st parry, 3x on 2nd, up to 4x on 3rd parry (capped at 4x max).
 * Lasts for 3 seconds (60 ticks).
 */
public class RiposteEffect extends MobEffect {
    public RiposteEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00FFCC); // Electric Cyan / Golden Surge aura
    }

    /**
     * Applies or upgrades the Riposte effect on the parrying player.
     * Triggers 1 flash effect and a high-velocity flame particle burst at player chest height.
     * Duration: 3 seconds (60 ticks).
     * Consecutive parries stack up to amplifier 2 (4.0x multiplier max).
     */
    public static void applyTo(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide())
            return;

        MobEffect effect = ModEffects.RIPOSTE.get();
        MobEffectInstance current = entity.getEffect(effect);

        int newAmplifier = 0;
        if (current != null) {
            newAmplifier = Math.min(2, current.getAmplifier() + 1);
        }

        entity.addEffect(new MobEffectInstance(effect, 60, newAmplifier, false, true, true));

        // Visual Effect on Successful Parry (1 Flash + Small High-Velocity Flame Burst)
        if (entity.level() instanceof ServerLevel level) {
            double x = entity.getX();
            double y = entity.getY() + entity.getEyeHeight() * 0.75;
            double z = entity.getZ();

            // 1 Flash effect
            level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);

            // Small high-velocity flame particle burst
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 0.15, 0.15, 0.15, 0.4);
        }
    }

    public static float getPoiseDamageMultiplier(LivingEntity entity) {
        if (entity == null) return 1.0f;
        MobEffectInstance instance = entity.getEffect(ModEffects.RIPOSTE.get());
        if (instance != null) {
            return Math.min(4.0f, 2.0f + instance.getAmplifier());
        }
        return 1.0f;
    }
}
