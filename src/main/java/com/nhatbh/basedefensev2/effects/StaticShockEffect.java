package com.nhatbh.basedefensev2.effects;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class StaticShockEffect extends MobEffect {

    public StaticShockEffect() {
        super(MobEffectCategory.HARMFUL, 0x00FFFF); // Electric cyan color
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            Vec3 pos = entity.position();
            // Render 10-block radius particle ring around the afflicted player
            ParticleUtils.renderCircle(level, ParticleTypes.ELECTRIC_SPARK, pos, 10.0, 48, 0.05);
            level.sendParticles(ParticleTypes.WAX_OFF, pos.x, pos.y + 0.1, pos.z, 3, 0.5, 0.1, 0.5, 0.02);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        if (!entity.level().isClientSide() && entity.level() instanceof ServerLevel level) {
            detonateStaticShock(level, entity);
        }
    }

    private static void detonateStaticShock(ServerLevel level, LivingEntity afflicted) {
        Vec3 center = afflicted.position();

        // Explosive thunder sound
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.2f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 2.5f, 1.0f);

        // Search all players in 10-block radius EXCEPT the afflicted player
        List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                level, Player.class, center, 10.0, player -> player.isAlive() && !player.isCreative() && !player.isSpectator());

        for (Player target : nearbyPlayers) {
            if (!target.equals(afflicted)) {
                // Strike cosmetic lightning bolt
                LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                if (lightning != null) {
                    lightning.moveTo(target.position());
                    lightning.setVisualOnly(true);
                    level.addFreshEntity(lightning);
                }

                // Clear all beneficial effects before applying damage
                com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.clearBeneficialEffects(target);

                // Deal damage: half max HP (35%) + half flat (10.5) = 21.0 total damage on 30 HP player
                float damage = (target.getMaxHealth() * 0.35f) + 10.5f;
                target.hurt(level.damageSources().magic(), damage);

                // Apply Slowness II for 8 seconds (160 ticks)
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1, false, true));

                // Impact particle FX
                level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 25, 0.5, 0.5, 0.5, 0.2);
            }
        }
    }
}
