package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class VoidFissureSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("void_fissure")
            // Step 1: Overhead windup
            .step("windup", 60)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "The earth shall split.");
                })
                .counter(ActiveSequence.CounterType.NORMAL, 40, 60)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onTick(ctx -> {
                    if (ctx.getTicks() % 5 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, ctx.boss().getX(), ctx.boss().getY() + 3, ctx.boss().getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                })

            // Step 2: Leap into the air
            .step("leap", 12)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 2.0f, 0.5f);
                    ctx.boss().setDeltaMovement(0, 1.2, 0);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    // Maintain slight horizontal movement towards target if needed, but mostly vertical
                })

            // Step 3: Slam down
            .step("slam", 20)
                .onStart(ctx -> {
                    ctx.boss().setDeltaMovement(0, -1.8, 0);
                })
                .onTick(ctx -> {
                    if (ctx.boss().onGround()) {
                        ctx.jumpToStep("fissure");
                    } else {
                        ctx.boss().setDeltaMovement(0, -1.8, 0);
                    }
                })

            // Step 4: Cleave and Progressive Fissures
            .step("fissure", 40)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);
                    LivingEntity target = BossSkillHelper.getFurthestTarget(ctx, 100.0);
                    Vec3 dir = ctx.boss().getLookAngle().multiply(1, 0, 1).normalize();
                    if (target != null) {
                        dir = target.position().subtract(ctx.boss().position()).multiply(1, 0, 1).normalize();
                    }
                    ctx.data().put("fissure_dir", dir);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.getTicks() % 2 == 0) {
                        Vec3 dir = (Vec3) ctx.data().get("fissure_dir");
                        if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
                            double distance = ctx.getTicks() * 2.5;
                            Vec3 pos = ctx.boss().position().add(dir.scale(distance));
                            
                            // Visuals
                            level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 1, 0, 0, 0, 0);
                            level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.1, pos.z, 2, 0.3, 0.5, 0.3, 0.02);
                            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5f, 1.5f);

                            // AOE Damage & Effects
                            level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos.subtract(2, 1, 2), pos.add(2, 3, 2))).forEach(e -> {
                                if (e != ctx.boss()) {
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, e, 10.0f, 20.0f);
                                    e.hurt(ctx.boss().damageSources().magic(), damage);
                                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1));
                                }
                            });

                            // Lingering Particle Cloud
                            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(level, pos.x, pos.y, pos.z);
                            cloud.setOwner(ctx.boss());
                            cloud.setRadius(2.5f);
                            cloud.setRadiusOnUse(-0.01f);
                            cloud.setWaitTime(10);
                            cloud.setDuration(300); // 15 seconds
                            cloud.setParticle(ParticleTypes.WITCH);
                            cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1));
                            cloud.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
                            level.addFreshEntity(cloud);
                        }
                    }
                })
            .build();
    }
}
