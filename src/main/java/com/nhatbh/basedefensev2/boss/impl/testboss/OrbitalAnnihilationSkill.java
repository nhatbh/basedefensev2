package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class OrbitalAnnihilationSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("orbital_annihilation")
                // Step 1: Beacon
                .step("beacon", 40)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.0f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Orbital target locked.");
                    LivingEntity target = BossSkillHelper.getRandomTarget(ctx, 100.0);
                    if (target != null) {
                        ctx.data().put("beacon_target", target.position());
                    }
                })
                .onTick(ctx -> {
                    Vec3 pos = (Vec3) ctx.data().get("beacon_target");
                    if (pos != null && ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.1, pos.z, 5, 0.2, 0, 0.2, 0.05);
                    }
                })

                // Step 2: Laser Windup (Magic Counter window)
                .step("laser_windup", 60) // Total windup: 40 (beacon) + 60 (windup) = 100 ticks
                .magic(ElementType.ELDRITCH)
                .magicThreshold(50f)
                .onTick(ctx -> {
                    Vec3 pos = (Vec3) ctx.data().get("beacon_target");
                    if (pos != null && ctx.boss().level() instanceof ServerLevel level) {
                        // High-visibility vertical warning beam (growing intensity)
                        float progress = (float) ctx.getTicks() / 60.0f;
                        for (int h = 0; h < 40; h += 2) {
                            level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + h, pos.z, 2, 0.1, 0.1, 0.1, 0.02);
                            if (progress > 0.5) {
                                level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + h, pos.z, 1, 0, 0, 0, 0);
                            }
                        }

                        // Ground warning circle (pulsing)
                        double radius = 10.0;
                        com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level, ParticleTypes.WITCH,
                                pos.add(0, 0.1, 0), radius, 24, 0.05);
                        if (progress > 0.7) {
                            com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level,
                                    ParticleTypes.SOUL_FIRE_FLAME, pos.add(0, 0.1, 0), radius * 0.5, 12, 0.05);
                        }

                        if (ctx.getTicks() % 10 == 0) {
                            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE,
                                    2.0f, 0.5f + progress);
                        }

                        // Heavy charging particles at ground
                        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 0.1, pos.z, 15, 1.0, 0.1, 1.0,
                                0.1);
                    }
                })

                // Step 3: Orbital Strike
                .step("orbital_strike", 100)
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Open up the sky!");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5.0f, 0.5f);
                })
                .onTick(ctx -> {
                    Vec3 pos = (Vec3) ctx.data().get("beacon_target");
                    if (pos != null && ctx.boss().level() instanceof ServerLevel level) {
                        // Thick vertical laser beam
                        for (int h = 0; h < 40; h++) {
                            // Render concentric circles for a thick beam effect
                            com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level,
                                    ParticleTypes.SOUL_FIRE_FLAME, pos.add(0, h, 0), 2.0, 8, 0.01);
                            com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level, ParticleTypes.GLOW,
                                    pos.add(0, h, 0), 5.0, 12, 0.02);
                            if (h % 5 == 0) {
                                com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level,
                                        ParticleTypes.WITCH, pos.add(0, h, 0), 10.0, 16, 0.05);
                            }
                        }

                        // Ground particles
                        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.1, pos.z, 15, 5.0, 0.1, 5.0, 0.1);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.1, pos.z, 20, 10.0, 0.5, 10.0,
                                0.1);

                        if (ctx.getTicks() % 10 == 0) {
                            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                                    4.0f, 0.5f);
                            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                                    SoundSource.HOSTILE, 2.0f, 1.2f);
                        }

                        // Ramping damage in 10 block AOE (Slowly lethal)
                        level.getEntitiesOfClass(LivingEntity.class,
                                new net.minecraft.world.phys.AABB(pos.subtract(10, 5, 10), pos.add(10, 40, 10)))
                                .forEach(e -> {
                                    if (e != ctx.boss()) {
                                        float damage = BossSkillHelper.calculateMixedDamage(ctx, e, 30.0f, 60.0f);
                                        e.hurt(ctx.boss().damageSources().indirectMagic(ctx.boss(), ctx.boss()),
                                                damage);
                                    }
                                });
                    }
                })
                .build();
    }
}
