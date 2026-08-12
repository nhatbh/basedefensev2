package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GravitonSingularitySkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("graviton_singularity")
                // Phase 1: Pulling (80 ticks) - MAGIC counter
                .parryStep("pulling", 80)
                .counter(ActiveSequence.CounterType.MAGIC, 1, 80)
                .magicThreshold(30.0f)
                .onCountered((ctx, event) -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 1.2f);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY() + 1,
                                ctx.boss().getZ(), 10, 0.5, 0.5, 0.5, 0);
                    }
                    ctx.applyExhaustion(200); // Heavy stun
                    ctx.stopSequence();
                })
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Collapse into nothing!");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 center = ctx.boss().position().add(0, 1, 0);

                        // Visuals: Portal particles spiraling in
                        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 20, 0.5, 0.5, 0.5, 0.1);
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 10, 2.0, 2.0,
                                2.0, -0.2);

                        // Pull logic
                        double pullRadius = 100.0;
                        List<Player> players = level.getEntitiesOfClass(Player.class,
                                ctx.boss().getBoundingBox().inflate(pullRadius));
                        for (Player player : players) {
                            Vec3 pullDir = center.subtract(player.position()).normalize();
                            double dist = player.distanceTo(ctx.boss());
                            if (dist > 3.0) {
                                double pullStrength = 0.2;
                                player.setDeltaMovement(player.getDeltaMovement().add(pullDir.scale(pullStrength)));
                                player.hurtMarked = true;
                            }
                        }
                    }
                })

                // Phase 2: Condensing (40 ticks) - NO PULL, Escape window
                .parryStep("condensing", 40)
                .counter(ActiveSequence.CounterType.MAGIC, 1, 40)
                .magicThreshold(30.0f)
                .onCountered((ctx, event) -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 1.2f);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY() + 1,
                                ctx.boss().getZ(), 10, 0.5, 0.5, 0.5, 0);
                    }
                    ctx.applyExhaustion(200);
                    ctx.stopSequence();
                })
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Everything... disappears.");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 center = ctx.boss().position().add(0, 1, 0);
                        // Intense condensing visuals
                        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 40, 0.2, 0.2, 0.2, 0.5);
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 20, 1.0, 1.0,
                                1.0, 1.0);
                    }
                })

                // Phase 2: Wide Sweep (wide_sweep)
                .step("sweep", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.2f);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        double radius = 8.0;
                        Vec3 pos = ctx.boss().position().add(0, 1, 0);

                        ParticleUtils.renderCircle(level, ParticleTypes.SWEEP_ATTACK, pos, radius, 32, 0);
                        ParticleUtils.renderCircle(level, ParticleTypes.EXPLOSION, pos, radius * 0.5, 16, 0);

                        List<net.minecraft.world.entity.player.Player> targets = HitboxUtils.getEntitiesInCircle(level,
                                net.minecraft.world.entity.player.Player.class, pos,
                                radius,
                                e -> e.isAlive());

                        for (LivingEntity target : targets) {
                            float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 15.0f, 25.0f);
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);

                            // Clear beneficial buffs
                            target.getActiveEffects().stream()
                                    .filter(effect -> effect.getEffect().isBeneficial())
                                    .map(MobEffectInstance::getEffect)
                                    .toList() // Avoid ConcurrentModificationException
                                    .forEach(target::removeEffect);

                            // Knockback away
                            Vec3 knockDir = target.position().subtract(ctx.boss().position()).normalize();
                            target.setDeltaMovement(target.getDeltaMovement().add(knockDir.scale(2.5).add(0, 0.5, 0)));
                        }
                    }
                })
                .onTick(BossSkillHelper::stopMovement)
                .build();
    }
}
