package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AegisReflectionSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("aegis_reflection")
                // Phase 1: Shield & Counter Window
                .parryStep("shield", 80)
                .counter(ActiveSequence.CounterType.NORMAL, 81, 81)
                .punishment((ctx, event) -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 1.5f);
                    ctx.jumpToStep("shockwave"); // Immediate retaliation
                })
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Strike me if you dare!");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);

                    ctx.data().put("shield_dir", ctx.boss().getLookAngle().multiply(1, 0, 1).normalize());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 pos = ctx.boss().position().add(0, 1, 0);

                        // Visual: 360-degree shield particles
                        ParticleUtils.renderCircle(level, ParticleTypes.CLOUD, pos, 2.5, 24, 0.05);
                        ParticleUtils.renderCircle(level, ParticleTypes.SOUL, pos, 2.0, 16, 0.02);
                    }
                })
                .onCountered((ctx, event) -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.0f, 0.8f);

                    // Reduce 10% max strength
                    BossSkillHelper.depletePoise(ctx, 10f);
                    ctx.data().put("weakened_strength", true);
                    // Or apply a weakness effect
                    ctx.boss().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WEAKNESS, 600, 2));

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, ctx.boss().getX(), ctx.boss().getY() + 2,
                                ctx.boss().getZ(), 10, 0.5, 0.5, 0.5, 0);
                    }
                })

                // Phase 2: Punishment (only if not countered)
                .step("shockwave", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 pos = ctx.boss().position();
                        double radius = 12.0;

                        // Huge 360-degree shockwave particles
                        ParticleUtils.renderCircle(level, ParticleTypes.SONIC_BOOM, pos.add(0, 1, 0), radius, 32, 0.1);
                        ParticleUtils.renderCircle(level, ParticleTypes.EXPLOSION, pos.add(0, 1, 0), radius * 0.5, 16,
                                0);

                        List<net.minecraft.world.entity.player.Player> targets = HitboxUtils.getEntitiesInCircle(level,
                                net.minecraft.world.entity.player.Player.class, pos, radius,
                                e -> e.isAlive());

                        for (LivingEntity target : targets) {
                            // Massive knockback away
                            Vec3 knockDir = target.position().subtract(pos).normalize();
                            target.setDeltaMovement(knockDir.scale(2.5).add(0, 1.0, 0));

                            float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 50.0f, 50.0f);
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 0.5f);
                        }
                    }
                })
                .onTick(BossSkillHelper::stopMovement)
                .build();
    }
}
